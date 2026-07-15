#!/usr/bin/env python3
"""Génère `docs/photos-cascade/index.html` : galerie HTML de relecture visuelle
des photos S10 (cascade Wikidata P18 / iNaturalist — cycle Herbier).

Lit deux assets committés :
- `app/src/main/assets/species-photos.json` : manifest photos, structure
  `{"meta":{"v","licenses":{<key>:{name,url}},"sources":{<key>:{name,authors}}},
  "photos":{"<sk>":[{"f","r","src","lic","by","u"}]}}`. Seules les entrées **S10**
  sont affichées : celles dont `src ∈ {"wikimedia-commons","inaturalist"}`. Les
  entrées S9 (`src == "paris"`) sont exclues de la galerie mais comptées pour le
  contexte « X/665 trous comblés ». Chaque entrée S10 a exactement 1 photo
  `{sk}-0.webp` de rôle "p".
- `app/src/main/assets/species-index.json` : liste `{"i":sk,"g","e","nc","nv","n"}`,
  utilisée pour afficher par sk le nom latin `g e`, le vernaculaire `nv` et le
  nom commun `nc`.

Les WebP (`app/src/main/assets/species-photos/{sk}-0.webp`) sont **référencés par
chemin relatif** (`<img src="../../app/src/main/assets/...">`) et jamais inlinés
en base64 : les WebP committés pèsent lourd et les inliner gonflerait l'HTML.
Le poids total est lu sur disque via `os.path.getsize` pour l'en-tête.

Galerie groupée par SOURCE (Wikimedia Commons puis iNaturalist), triée par sk.
WebP absent du disque → placeholder texte « (fichier manquant) », jamais
d'exception. Manifest ou index absent → message clair, exit propre.

Ce n'est **pas** un artefact de build consommé par l'app : c'est un outil de
vérification humaine (mauvais taxon, image hors-sujet, licence), à régénérer
après un run de la cascade photos S10.

Usage :
    python3 tools/build_photos_cascade_preview.py
    python3 tools/build_photos_cascade_preview.py --manifest <m.json> --index <i.json> --out <index.html>
"""
from __future__ import annotations

import argparse
import datetime
import html
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "species-photos.json"
DEFAULT_INDEX = ROOT / "app" / "src" / "main" / "assets" / "species-index.json"
DEFAULT_OUT = ROOT / "docs" / "photos-cascade" / "index.html"
# Dossier des WebP (relatif à l'HTML de sortie), pour le rendu <img> et getsize.
PHOTOS_DIR = ROOT / "app" / "src" / "main" / "assets" / "species-photos"

# Sources de la cascade S10. Ordre = ordre des sections de la galerie.
S10_SOURCES = ("wikimedia-commons", "inaturalist")
S10_SOURCE_LABELS = {
    "wikimedia-commons": "Wikimedia Commons",
    "inaturalist": "iNaturalist",
}
S9_SOURCE = "paris"
# Dénominateur « trous comblés » : espèces identifiées sans photo S9 ciblées par
# la cascade. Constante de contexte (cf. brief S10).
GAP_TARGET = 665
# Étiquettes lisibles des clés de licence rencontrées côté cascade.
LICENSE_LABELS = {
    "cc0": "CC0",
    "pd": "Domaine public",
    "cc-by": "CC BY",
}


# ---------------------------------------------------------------------------
# Chargement des assets (jamais d'exception : messages + exit propre)
# ---------------------------------------------------------------------------

def load_json(path: Path, label: str) -> object | None:
    """Charge un JSON ; None + message clair si absent ou illisible."""
    if not path.exists():
        print(f"[err] {label} absent : {path}")
        return None
    try:
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"[err] {label} illisible ({path}) : {exc}")
        return None


def build_index_lookup(index: object) -> dict[int, dict]:
    """Indexe la liste species-index par sk (`i`). Robuste aux entrées malformées."""
    lookup: dict[int, dict] = {}
    if not isinstance(index, list):
        return lookup
    for entry in index:
        if isinstance(entry, dict) and "i" in entry:
            try:
                lookup[int(entry["i"])] = entry
            except (TypeError, ValueError):
                continue
    return lookup


def latin_name(entry: dict | None, sk: int) -> str:
    """Nom latin `genre espece` depuis l'entrée species-index ; fallback sk."""
    if not entry:
        return f"sk {sk}"
    genre = (entry.get("g") or "").strip()
    espece = (entry.get("e") or "").strip()
    latin = " ".join(p for p in (genre, espece) if p)
    return latin or f"sk {sk}"


# ---------------------------------------------------------------------------
# Collecte des entrées S10
# ---------------------------------------------------------------------------

def collect_s10(manifest: dict) -> list[dict]:
    """Aplati le manifest en entrées S10 : une par photo de source cascade.

    Chaque item : `{sk, photo, index_entry?}` (index_entry ajouté au rendu).
    Trié par (source dans l'ordre S10_SOURCES, sk).
    """
    photos = manifest.get("photos") or {}
    items: list[dict] = []
    for sk_str, plist in photos.items():
        try:
            sk = int(sk_str)
        except (TypeError, ValueError):
            continue
        for p in (plist or []):
            if p.get("src") in S10_SOURCES:
                items.append({"sk": sk, "photo": p})
    return items


def webp_size_bytes(fname: str) -> int:
    """Poids sur disque du WebP (0 si absent/illisible)."""
    if not fname:
        return 0
    path = PHOTOS_DIR / fname
    try:
        return os.path.getsize(path)
    except OSError:
        return 0


# ---------------------------------------------------------------------------
# Rendu HTML
# ---------------------------------------------------------------------------

CSS = """
:root {
  --bg: #fafaf8; --fg: #1f2937; --muted: #6b7280; --border: #e5e7eb;
  --card-bg: #ffffff; --accent: #15803d; --link: #1d4ed8;
}
* { box-sizing: border-box; }
body {
  margin: 0; padding: 24px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  background: var(--bg); color: var(--fg); line-height: 1.45;
}
h1 { margin: 0 0 4px; font-size: 26px; }
h2 { margin: 34px 0 14px; font-size: 19px; border-bottom: 1px solid var(--border); padding-bottom: 6px; }
.meta { color: var(--muted); font-size: 13px; margin-bottom: 16px; }
.meta code { background: #eef2f7; padding: 1px 6px; border-radius: 3px;
  font-family: ui-monospace, monospace; font-size: 12px; }
.intro {
  background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 8px;
  padding: 12px 16px; margin-bottom: 18px; font-size: 14px;
}
.grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px; margin-bottom: 18px;
}
.card {
  background: var(--card-bg); border: 1px solid var(--border);
  border-radius: 8px; padding: 14px 16px;
}
.card .label { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.card .value { font-size: 24px; font-weight: 600; margin-top: 4px; }
.card .sub { font-size: 12px; color: var(--muted); margin-top: 4px; }
.gallery { display: flex; flex-wrap: wrap; gap: 16px; }
.photo {
  margin: 0; width: 260px; background: var(--card-bg);
  border: 1px solid var(--border); border-radius: 10px; padding: 10px; display: flex;
  flex-direction: column; gap: 6px;
}
.photo-img {
  max-width: 240px; max-height: 240px; width: auto; border-radius: 6px;
  background: #f3f4f6; display: block; margin: 0 auto;
}
.photo-missing {
  color: var(--muted); font-size: 12.5px; font-style: italic; text-align: center;
  border: 1px dashed var(--border); border-radius: 6px; padding: 40px 14px; background: #f9fafb;
}
.photo-missing code { font-family: ui-monospace, monospace; font-style: normal; font-size: 11px; }
.photo figcaption { display: flex; flex-direction: column; gap: 2px; font-size: 12.5px; }
.p-sk { font-family: ui-monospace, monospace; color: var(--muted); font-size: 11.5px; }
.p-latin { font-style: italic; font-weight: 600; font-size: 14px; }
.p-nv { color: var(--fg); font-size: 12.5px; }
.p-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 2px; }
.p-lic { background: #eef2ff; border: 1px solid #c7d2fe; color: #3730a3;
  border-radius: 999px; padding: 1px 8px; font-size: 11px; }
.p-by { color: var(--muted); font-size: 11.5px; }
.p-src a { color: var(--link); text-decoration: none; font-size: 11.5px; }
.p-src a:hover { text-decoration: underline; }
.empty-note { color: var(--muted); font-style: italic; font-size: 14px; }
"""


def photo_card(item: dict, index_lookup: dict[int, dict]) -> str:
    """Une vignette de galerie pour une photo S10."""
    sk = item["sk"]
    p = item["photo"]
    entry = index_lookup.get(sk)
    fname = (p.get("f") or "").strip()

    latin = latin_name(entry, sk)
    nv = (entry.get("nv") if entry else "") or ""
    nc = (entry.get("nc") if entry else "") or ""

    path = (PHOTOS_DIR / fname) if fname else None
    if path is not None and path.exists():
        rel = os.path.relpath(path, DEFAULT_OUT.parent)
        media = (f'<img class="photo-img" src="{html.escape(rel)}" '
                 f'alt="{html.escape(latin)}" loading="lazy">')
    else:
        label = html.escape(fname) if fname else "fichier inconnu"
        media = (f'<div class="photo-missing"><code>{label}</code><br>'
                 '(fichier manquant)</div>')

    lic_key = p.get("lic") or ""
    lic_label = LICENSE_LABELS.get(lic_key, lic_key or "licence ?")
    by = p.get("by") or "auteur inconnu"
    url = p.get("u") or ""

    # Nom vernaculaire + nom commun (dédupliqués si identiques).
    noms = " · ".join(dict.fromkeys(n for n in (nv, nc) if n))

    tags = [f'<span class="p-lic">{html.escape(lic_label)}</span>',
            f'<span class="p-by">{html.escape(by)}</span>']
    src_html = ""
    if url:
        src_html = (f'<span class="p-src"><a href="{html.escape(url)}" '
                    f'target="_blank" rel="noopener">page source ↗</a></span>')

    return (
        '<figure class="photo">'
        f'{media}'
        '<figcaption>'
        f'<span class="p-sk">sk {sk} · {html.escape(fname or "?")}</span>'
        f'<span class="p-latin">{html.escape(latin)}</span>'
        + (f'<span class="p-nv">{html.escape(noms)}</span>' if noms else "")
        + f'<span class="p-tags">{"".join(tags)}</span>'
        + src_html
        + '</figcaption></figure>'
    )


def _card(label: str, value: str, sub: str = "") -> str:
    sub_html = f'<div class="sub">{html.escape(sub)}</div>' if sub else ""
    return (
        f'<div class="card"><div class="label">{html.escape(label)}</div>'
        f'<div class="value">{html.escape(value)}</div>{sub_html}</div>'
    )


def render_html(manifest: dict, index: object, manifest_path: Path) -> str:
    index_lookup = build_index_lookup(index)
    items = collect_s10(manifest)

    total = len(items)
    by_source: dict[str, list[dict]] = {s: [] for s in S10_SOURCES}
    for it in items:
        by_source[it["photo"]["src"]].append(it)
    for s in by_source:
        by_source[s].sort(key=lambda it: it["sk"])

    # Répartition par licence + poids total sur disque (S10 seulement).
    lic_counts: dict[str, int] = {}
    total_bytes = 0
    for it in items:
        lic = it["photo"].get("lic") or "?"
        lic_counts[lic] = lic_counts.get(lic, 0) + 1
        total_bytes += webp_size_bytes((it["photo"].get("f") or "").strip())
    total_mb = total_bytes / (1024 * 1024)

    # Contexte S9 : nombre d'espèces avec photo paris (trous déjà couverts hors S10).
    n_s9 = 0
    for plist in (manifest.get("photos") or {}).values():
        if any(p.get("src") == S9_SOURCE for p in (plist or [])):
            n_s9 += 1

    lic_summary = " · ".join(
        f"{LICENSE_LABELS.get(k, k)} {v}" for k, v in sorted(lic_counts.items())
    ) or "—"

    generated = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")

    out: list[str] = []
    out.append("<!doctype html>")
    out.append('<html lang="fr"><head><meta charset="utf-8">')
    out.append('<meta name="viewport" content="width=device-width, initial-scale=1">')
    out.append("<title>Photos cascade S10 — relecture visuelle</title>")
    out.append(f"<style>{CSS}</style>")
    out.append("</head><body>")
    out.append("<h1>Photos cascade S10 — relecture visuelle</h1>")
    out.append(
        f'<div class="meta">Généré le {generated} · manifest '
        f'<code>{html.escape(str(manifest_path))}</code> · '
        f'outil <code>tools/build_photos_cascade_preview.py</code></div>'
    )
    out.append(
        '<div class="intro">Page de <strong>vérification humaine</strong>, pas un '
        'artefact de build. Objectif : repérer d\'un coup d\'œil les mauvais taxons, '
        'images hors-sujet ou licences douteuses parmi les photos récupérées par la '
        'cascade Wikidata P18 / iNaturalist. Chaque vignette est référencée depuis '
        '<code>app/src/main/assets/species-photos/</code> (jamais inlinée).</div>'
    )

    # --- Bandeau compteurs ---
    n_wc = len(by_source["wikimedia-commons"])
    n_inat = len(by_source["inaturalist"])
    out.append('<div class="grid">')
    out.append(_card("Photos S10", str(total), "sources cascade"))
    out.append(_card("Wikimedia Commons", str(n_wc)))
    out.append(_card("iNaturalist", str(n_inat)))
    out.append(_card("Licences", lic_summary))
    out.append(_card("Poids total", f"{total_mb:.1f} Mo", "WebP sur disque"))
    out.append(_card("Trous comblés", f"{n_s9 + total}/{GAP_TARGET}",
                     f"S9 {n_s9} + S10 {total}"))
    out.append("</div>")

    # --- Galerie par source ---
    for src in S10_SOURCES:
        section = by_source[src]
        label = S10_SOURCE_LABELS[src]
        out.append(f'<h2>{html.escape(label)} <span class="meta">'
                   f'({len(section)} photo(s))</span></h2>')
        if not section:
            out.append('<p class="empty-note">Aucune photo de cette source.</p>')
            continue
        out.append('<div class="gallery">')
        for it in section:
            out.append(photo_card(it, index_lookup))
        out.append('</div>')

    out.append("</body></html>")
    return "".join(out)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST,
                    help=f"manifest species-photos.json (défaut {DEFAULT_MANIFEST})")
    ap.add_argument("--index", type=Path, default=DEFAULT_INDEX,
                    help=f"species-index.json (défaut {DEFAULT_INDEX})")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT,
                    help=f"HTML de sortie (défaut {DEFAULT_OUT})")
    args = ap.parse_args()

    manifest = load_json(args.manifest, "manifest")
    if not isinstance(manifest, dict):
        print("      Rien à générer sans manifest valide.")
        return 1
    index = load_json(args.index, "species-index")
    if index is None:
        # L'index n'est pas bloquant : la galerie reste lisible (noms → fallback sk).
        print("[warn] species-index absent : noms latins/vernaculaires indisponibles.")
        index = []

    args.out.parent.mkdir(parents=True, exist_ok=True)
    n_s10 = len(collect_s10(manifest))
    print(f"[casc] {n_s10} photo(s) S10 (wikimedia-commons + inaturalist)")
    htmlout = render_html(manifest, index, args.manifest)
    args.out.write_text(htmlout, encoding="utf-8")
    size_mb = args.out.stat().st_size / (1024 * 1024)
    print(f"[ok ] → {args.out} ({size_mb:.2f} Mo)")
    if n_s10 == 0:
        print("[note] aucune entrée S10 dans le manifest — cascade pas encore lancée ?")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
