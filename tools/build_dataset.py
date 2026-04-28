#!/usr/bin/env python3
"""
Construit la base SQLite + le GeoJSON pré-cuits des arbres parisiens
à partir du dataset OpenData `les-arbres`.

Sorties :
- ../app/src/main/assets/databases/arbres-paris.db   (Room, fiche détail)
- ../app/src/main/assets/arbres-paris.geojson        (MapLibre, source clusterisée)
- ../app/src/main/assets/species-index.json          (lookup int -> {genre, espece})
- ../app/src/main/assets/dataset-stats.json          (totals affichés en Arboretum)

Schéma SQLite identique à celui que Room générerait pour `ArbreEntity` —
sans cela, `createFromAsset()` rejette la base au runtime.

Les arbres sans `genre` ou `espece` sont filtrés à l'entrée : la mécanique de
découverte est par espèce, ils n'ont rien à raccrocher. Les `speciesIndex`
sont persistés dans `species-index.json` et préservés entre exécutions —
sans ça, regénérer la base casserait les captures déjà enregistrées chez
l'utilisateur (qui réfèrent les espèces par leur int).

Stdlib uniquement (urllib + csv + sqlite3 + json), aucun pip install requis.
"""

from __future__ import annotations

import csv
import json
import sqlite3
import sys
import urllib.request
from pathlib import Path

CSV_URL = (
    "https://opendata.paris.fr/api/explore/v2.1/catalog/datasets/"
    "les-arbres/exports/csv?lang=fr&timezone=Europe%2FBerlin"
    "&use_labels=false&delimiter=%3B"
)

ROOT = Path(__file__).resolve().parent.parent
RAW_CSV = ROOT / "tools" / "les-arbres.csv"
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
OUT_DB = ASSETS_DIR / "databases" / "arbres-paris.db"
OUT_GEOJSON = ASSETS_DIR / "arbres-paris.geojson"
OUT_SPECIES_INDEX = ASSETS_DIR / "species-index.json"
OUT_DATASET_STATS = ASSETS_DIR / "dataset-stats.json"


def download(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 1_000_000:
        print(f"[skip] {dest.name} déjà présent ({dest.stat().st_size // 1_000_000} Mo)")
        return
    print(f"[get ] {url}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url) as resp, dest.open("wb") as out:
        bytes_total = 0
        while True:
            chunk = resp.read(1 << 16)
            if not chunk:
                break
            out.write(chunk)
            bytes_total += len(chunk)
        print(f"[ok  ] {bytes_total // 1_000_000} Mo écrits dans {dest.name}")


def to_int_or_none(v: str) -> int | None:
    v = (v or "").strip()
    if not v:
        return None
    try:
        n = int(float(v))
    except ValueError:
        return None
    return n if n > 0 else None


def to_str_or_none(v: str) -> str | None:
    v = (v or "").strip()
    return v or None


def parse_geo(v: str) -> tuple[float, float] | None:
    # Format OpenData : "48.8513, 2.3530"
    if not v or "," not in v:
        return None
    try:
        lat_s, lon_s = v.split(",", 1)
        return float(lat_s.strip()), float(lon_s.strip())
    except ValueError:
        return None


def build_address(complement: str, adresse: str, arrondissement: str) -> str | None:
    parts: list[str] = []
    comp = to_str_or_none(complement)
    voie = to_str_or_none(adresse)
    arr = to_str_or_none(arrondissement)

    head = " ".join(filter(None, [comp, voie]))
    if head:
        parts.append(head)
    if arr:
        parts.append(arr)
    return ", ".join(parts) if parts else None


SCHEMA_SQL = [
    # Schéma identique à celui que Room produit pour `ArbreEntity` —
    # ordre des colonnes, types, nullabilités et nom d'index doivent matcher.
    # genre/espece NOT NULL parce que les rows sans espèce sont filtrées en amont.
    """
    CREATE TABLE IF NOT EXISTS `arbre` (
      `id` INTEGER NOT NULL,
      `genre` TEXT NOT NULL,
      `espece` TEXT NOT NULL,
      `varieteCultivar` TEXT,
      `nomCommun` TEXT,
      `hauteurM` INTEGER,
      `circonferenceCm` INTEGER,
      `remarquable` INTEGER NOT NULL,
      `adresse` TEXT,
      `latitude` REAL NOT NULL,
      `longitude` REAL NOT NULL,
      PRIMARY KEY(`id`)
    )
    """,
    "CREATE INDEX IF NOT EXISTS `index_arbre_latitude_longitude` "
    "ON `arbre` (`latitude`, `longitude`)",
]


def load_existing_species_index(path: Path) -> dict[tuple[str, str], int]:
    """Charge l'index existant pour préserver les int speciesIndex entre runs."""
    if not path.exists():
        return {}
    try:
        with path.open("r", encoding="utf-8") as f:
            entries = json.load(f)
        return {(e["g"], e["e"]): e["i"] for e in entries}
    except (json.JSONDecodeError, KeyError, TypeError):
        print(f"[warn] {path.name} illisible, repart de zéro")
        return {}


def build(csv_path: Path, db_path: Path, geojson_path: Path) -> None:
    if db_path.exists():
        db_path.unlink()
    db_path.parent.mkdir(parents=True, exist_ok=True)
    geojson_path.parent.mkdir(parents=True, exist_ok=True)

    species_index = load_existing_species_index(OUT_SPECIES_INDEX)
    next_index = (max(species_index.values()) + 1) if species_index else 0
    if species_index:
        print(f"[idx ] {len(species_index)} espèces déjà indexées (next={next_index})")

    print(f"[db  ] création {db_path}")
    con = sqlite3.connect(db_path)
    cur = con.cursor()
    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")
    for stmt in SCHEMA_SQL:
        cur.execute(stmt)

    inserted = 0
    skipped = 0
    skipped_no_species = 0
    remarquables = 0
    seen_ids: set[int] = set()

    # GeoJSON streamé : on écrit l'enveloppe à la main pour éviter de garder
    # 217k features en RAM avant un dump unique.
    print(f"[geo ] création {geojson_path}")
    with geojson_path.open("w", encoding="utf-8") as gj:
        gj.write('{"type":"FeatureCollection","features":[')
        first_feature = True

        # utf-8-sig : OpenData Paris exporte un BOM en tête de fichier.
        with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f, delimiter=";")
            cur.execute("BEGIN")
            for row in reader:
                try:
                    id_ = int(row["idbase"])
                except (KeyError, ValueError, TypeError):
                    skipped += 1
                    continue
                if id_ in seen_ids:
                    skipped += 1
                    continue
                geo = parse_geo(row.get("geo_point_2d", ""))
                if geo is None:
                    skipped += 1
                    continue
                lat, lon = geo

                genre = to_str_or_none(row.get("genre", ""))
                espece = to_str_or_none(row.get("espece", ""))
                if genre is None or espece is None:
                    skipped_no_species += 1
                    continue

                seen_ids.add(id_)

                key = (genre, espece)
                sk = species_index.get(key)
                if sk is None:
                    sk = next_index
                    species_index[key] = sk
                    next_index += 1

                variete = to_str_or_none(row.get("varieteoucultivar", ""))
                nom_commun = to_str_or_none(row.get("libellefrancais", ""))
                hauteur = to_int_or_none(row.get("hauteurenm", ""))
                circonference = to_int_or_none(row.get("circonferenceencm", ""))
                remarquable = (
                    1 if (row.get("remarquable", "") or "").strip().upper() == "OUI" else 0
                )
                if remarquable:
                    remarquables += 1
                adresse = build_address(
                    row.get("complementadresse", ""),
                    row.get("adresse", ""),
                    row.get("arrondissement", ""),
                )

                cur.execute(
                    "INSERT INTO arbre VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    (
                        id_, genre, espece, variete, nom_commun,
                        hauteur, circonference, remarquable, adresse, lat, lon,
                    ),
                )

                feature = {
                    "type": "Feature",
                    "geometry": {"type": "Point", "coordinates": [lon, lat]},
                    "properties": {
                        "id": id_,
                        "remarquable": bool(remarquable),
                        "sk": sk,
                    },
                }
                if not first_feature:
                    gj.write(",")
                gj.write(json.dumps(feature, ensure_ascii=False, separators=(",", ":")))
                first_feature = False

                inserted += 1
                if inserted % 50_000 == 0:
                    print(f"[ins ] {inserted} arbres insérés...")
            con.commit()

        gj.write("]}")

    cur.execute("VACUUM")
    con.close()

    # species-index.json : trié par index pour un diff lisible.
    species_entries = sorted(
        ({"i": i, "g": g, "e": e} for (g, e), i in species_index.items()),
        key=lambda e: e["i"],
    )
    with OUT_SPECIES_INDEX.open("w", encoding="utf-8") as f:
        json.dump(species_entries, f, ensure_ascii=False, separators=(",", ":"))

    stats = {
        "totalArbres": inserted,
        "totalEspeces": len(species_index),
        "totalRemarquables": remarquables,
    }
    with OUT_DATASET_STATS.open("w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)

    db_mb = db_path.stat().st_size // 1_000_000
    gj_mb = geojson_path.stat().st_size // 1_000_000
    print(
        f"[done] {inserted} arbres ({skipped_no_species} sans espèce filtrés, "
        f"{skipped} autres ignorés)"
    )
    print(f"       → {db_path.name} ({db_mb} Mo)")
    print(f"       → {geojson_path.name} ({gj_mb} Mo)")
    print(f"       → {OUT_SPECIES_INDEX.name} ({len(species_index)} espèces)")
    print(f"       → {OUT_DATASET_STATS.name} ({remarquables} remarquables)")


def main() -> int:
    download(CSV_URL, RAW_CSV)
    build(RAW_CSV, OUT_DB, OUT_GEOJSON)
    return 0


if __name__ == "__main__":
    sys.exit(main())
