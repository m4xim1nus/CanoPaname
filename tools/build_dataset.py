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
import html
import json
import re
import sqlite3
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
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
OUT_SPECIES_INFO = ASSETS_DIR / "species-info.json"
OUT_REMARQUABLES_INFO = ASSETS_DIR / "remarquables-info.json"
OUT_SPLASH_TIPS = ASSETS_DIR / "splash-tips.json"
STATIC_SPLASH_TIPS = ROOT / "tools" / "splash-tips-static.json"

# Placeholders runtime supportés côté Kotlin (cf. SplashTipsController). Toute
# autre clé `{xxx}` rencontrée dans un tip player du JSON statique fait
# planter le build — sinon affichage de `Tu as croisé {???} espèces.` brut.
SUPPORTED_PLACEHOLDERS = {"captureCount", "speciesCount", "remarquableCount", "daysSinceFirst"}
PLACEHOLDER_RE = re.compile(r"\{([a-zA-Z]+)\}")

# Filtrage strict des arrondissements parisiens dans les agrégats `arr_*` :
# `endswith("e")` matche aussi « Hauts-de-Seine », à exclure des analyses.
ARR_PARIS_FORMAT_RE = re.compile(r"^\d+e$")


def is_paris_arr(a: str) -> bool:
    return bool(ARR_PARIS_FORMAT_RE.match(a))

REMARQUABLES_URL = (
    "https://opendata.paris.fr/api/explore/v2.1/catalog/datasets/"
    "arbresremarquablesparis/records"
)
REMARQUABLES_CACHE_DIR = ROOT / "tools" / ".remarquables-cache"
REMARQUABLES_PAGE_SIZE = 100  # API V2 plafonne à 100 par page

ESSENCES_URL = (
    "https://opendata.paris.fr/api/explore/v2.1/catalog/datasets/"
    "fiches-essences-du-guide-des-essences-de-paris/records"
)
ESSENCES_CACHE_DIR = ROOT / "tools" / ".essences-cache"
ESSENCES_PAGE_SIZE = 100

WIKI_USER_AGENT = "canopaname-build/0.1 (personal Android app, https://github.com/m4xim1nus/CanoPaname)"
WIKIDATA_CACHE_DIR = ROOT / "tools" / ".wikidata-cache"
WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"
WIKIDATA_BATCH_SIZE = 50  # Wikidata accepte largement plus, 50 garde la requête lisible
WIKIDATA_TIMEOUT_S = 60   # SPARQL peut être lent sur des batches de 50
WIKI_REST_THROTTLE_S = 0.3  # ~3 req/s sur restbase une fois qu'on a les vrais titres
WIKI_REST_TIMEOUT_S = 10
WIKI_REST_RETRIES_429 = 4  # backoff 4, 8, 16, 32 s

# Format CSV : « PARIS 5E ARRDT », « PARIS 16E ARRDT », ...
ARR_PARIS_PATTERN = re.compile(r"^PARIS\s+(\d+)E\s+ARRDT$", re.IGNORECASE)

# Coquilles latines récurrentes du CSV OpenData. Appliqué AVANT le lookup
# `species_index` : la row est rebindée vers l'entrée canonique, son `sk`
# préservé. Si la canonique n'existait pas encore c'est elle qui hérite du
# nouveau sk. La clé typo reste dans `species-index.json` comme zombie (sk
# conservé pour les captures Room existantes) mais sans count à partir d'ici.
SPECIES_FIXUPS: dict[tuple[str, str], tuple[str, str]] = {
    ("Olea", "europea"): ("Olea", "europaea"),
}

# Formes d'épithète signalant une espèce non identifiée (genre connu, espèce
# imprécise ou non renseignée). Normalisées en `sp.` à l'ingestion ; les
# entrées `species-index.json` qui matchent portent le flag `u: true`.
UNKNOWN_ESPECE_FORMS = frozenset({"sp.", "n. sp."})


def apply_species_fixups(genre: str, espece: str) -> tuple[str, str]:
    """Remap les coquilles `(genre, espece)` vers leur forme canonique."""
    return SPECIES_FIXUPS.get((genre, espece), (genre, espece))


def is_unknown_species(genre: str, espece: str) -> bool:
    """True si l'entrée doit porter `u: true` dans `species-index.json`."""
    return (
        espece.strip().lower() in UNKNOWN_ESPECE_FORMS
        or genre.strip() == "Non spécifié"
    )


# Overrides manuels du nom vernaculaire FR. Toujours gagnant dans la cascade.
# Démarre vide ; Sprint 3 ajoutera les candidats remontés par les sanity checks
# (notamment les espèces > 1000 captures qui finissent en fallback construit).
VERNACULAR_OVERRIDES: dict[tuple[str, str], str] = {}


def first_p1843(vernacular_names: list[str]) -> str | None:
    """Sélectionne la 1re forme alphabétique parmi les P1843 d'une espèce.

    Wikidata stocke souvent plusieurs noms vernaculaires (ex. Quercus robur :
    « Chêne pédonculé », « Chêne rouvre »). On prend la 1re alphabétique pour
    avoir un choix déterministe, reproductible entre runs.
    """
    if not vernacular_names:
        return None
    cleaned = [n.strip() for n in vernacular_names if n and n.strip()]
    if not cleaned:
        return None
    return sorted(cleaned)[0]


def construct_vernacular(
    genre: str,
    espece: str,
    nc: str | None,
    is_unknown: bool,
) -> str:
    """Fallback ultime quand Wikidata + Wikipedia n'ont rien.

    - Unknown (sp./n.sp./Non spécifié) → suffixe « (espèce indéterminée) ».
    - Identifiées avec nc → « {nc} ({I}. {epithète}) » (« Chêne (Q. robur) »).
    - Identifiées sans nc → binôme latin nu (« Pistacia palaestina »).
    """
    espece_clean = espece.lstrip("×").lstrip("xX").strip() or espece
    if is_unknown:
        if nc and nc.strip().lower() != "non spécifié":
            return f"{nc.strip()} (espèce indéterminée)"
        if genre.strip() and genre.strip() != "Non spécifié":
            return f"{genre.strip()} (espèce indéterminée)"
        return "Espèce indéterminée"
    if nc:
        initial = (genre[:1] or "?").upper()
        return f"{nc.strip()} ({initial}. {espece_clean})"
    return f"{genre.strip()} {espece_clean}".strip()


def disambiguate_vernaculars(entries: list[dict]) -> int:
    """Suffixe les `nv` collidents par leur binôme latin. Mute `entries` en
    place. Retourne le nb d'entrées modifiées (= somme des tailles des groupes
    avec collision).

    Les paires `(genre, espece)` étant uniques par construction de
    `species_index`, le suffixe garantit l'unicité finale.
    """
    by_nv: dict[str, list[dict]] = defaultdict(list)
    for e in entries:
        by_nv[e["nv"]].append(e)
    changed = 0
    for nv, group in by_nv.items():
        if len(group) <= 1:
            continue
        for e in group:
            e["nv"] = f"{nv} ({e['g']} {e['e']})"
            changed += 1
    return changed


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


def normalize_arr(raw: str) -> str | None:
    """Normalise la valeur brute du CSV `arrondissement` en un libellé court.

    « PARIS 5E ARRDT » -> « 5e ». Les bois et exclaves restent capitalisés.
    Vide -> None (l'entrée n'est pas comptée pour les stats arr).
    """
    raw = (raw or "").strip()
    if not raw:
        return None
    m = ARR_PARIS_PATTERN.match(raw)
    if m:
        return f"{int(m.group(1))}e"
    upper = raw.upper()
    if upper == "BOIS DE BOULOGNE":
        return "Bois de Boulogne"
    if upper == "BOIS DE VINCENNES":
        return "Bois de Vincennes"
    if upper == "HAUTS-DE-SEINE":
        return "Hauts-de-Seine"
    return raw.title()


class WikiTransient(Exception):
    """Erreur transient (429, 5xx, timeout). On ne cache pas ces résultats."""


def _candidates_for(genre: str, espece: str) -> list[str]:
    """Variantes de nom binomial à passer à Wikidata P225.

    OpenData mélange « × hispanica » et « x hispanica » pour les hybrides ;
    Wikidata stocke généralement « Genre × espece » avec le multiplication
    sign. On envoie les deux variantes dans le VALUES SPARQL — coût marginal,
    bien moins que rater une espèce.
    """
    espece = (espece or "").strip()
    out = [f"{genre} {espece}"]
    espece_no_x = espece.lstrip("×").lstrip("xX").strip()
    if espece_no_x and espece_no_x != espece:
        out.append(f"{genre} × {espece_no_x}")
        out.append(f"{genre} {espece_no_x}")
    return out


def _sparql_query(values: list[str]) -> str:
    """Bâtit la requête SPARQL pour un batch de noms binomial.

    On résout par P225 (taxon name) et on récupère :
    - le titre Wikipedia FR s'il existe, sinon EN en fallback ;
    - les noms vernaculaires FR via P1843 (peut être multiple par taxon →
      plusieurs lignes retournées, agrégées côté Python).
    """
    # Le tag `@en` n'est pas requis par P225 (les taxon names sont mono-string)
    # — on passe des littéraux non typés. Échapper backslash et guillemet pour
    # rester valide même si un nom contient des caractères pénibles.
    encoded = " ".join(
        '"' + v.replace('\\', '\\\\').replace('"', '\\"') + '"'
        for v in values
    )
    return f"""
SELECT ?taxon ?taxonName ?frTitle ?enTitle ?vernacularName WHERE {{
  VALUES ?taxonName {{ {encoded} }}
  ?taxon wdt:P225 ?taxonName .
  OPTIONAL {{
    ?frArticle schema:about ?taxon ;
               schema:isPartOf <https://fr.wikipedia.org/> ;
               schema:name ?frTitle .
  }}
  OPTIONAL {{
    ?enArticle schema:about ?taxon ;
               schema:isPartOf <https://en.wikipedia.org/> ;
               schema:name ?enTitle .
  }}
  OPTIONAL {{
    ?taxon wdt:P1843 ?vernacularName .
    FILTER(LANG(?vernacularName) = "fr")
  }}
}}
"""


def _sparql_post(query: str) -> dict:
    data = urllib.parse.urlencode({"query": query, "format": "json"}).encode("utf-8")
    req = urllib.request.Request(
        WIKIDATA_SPARQL_URL,
        data=data,
        headers={
            "User-Agent": WIKI_USER_AGENT,
            "Accept": "application/sparql-results+json",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    with urllib.request.urlopen(req, timeout=WIKIDATA_TIMEOUT_S) as resp:
        return json.loads(resp.read())


def resolve_via_wikidata(
    pairs: list[tuple[int, str, str]],
) -> dict[tuple[str, str], dict]:
    """Résout (genre, espece) -> {qid, frTitle?, enTitle?, vernacularNames}
    via SPARQL batched.

    `pairs` : liste `(sk, genre, espece)`. La résolution est par binomial seul
    (sk n'est utilisé que pour les logs). Si une variante du candidat liste
    matche dans Wikidata (ex: `Platanus × hispanica` matchera là où
    `Platanus x hispanica` rate), on garde le 1er QID rencontré et on agrège
    les `vernacularName` (P1843 @fr) pour ce QID sur toutes les lignes
    SPARQL retournées (P1843 peut être multiple par taxon).

    Retour : dict indexé par `(genre, espece)` original. Espèces non résolues
    absentes du dict. `vernacularNames` toujours présent (liste, possiblement
    vide).
    """
    # Indexer chaque variante vers son (genre, espece) d'origine.
    variant_to_origin: dict[str, tuple[str, str]] = {}
    all_variants: list[str] = []
    for _, genre, espece in pairs:
        for variant in _candidates_for(genre, espece):
            if variant not in variant_to_origin:
                variant_to_origin[variant] = (genre, espece)
                all_variants.append(variant)

    print(f"[wd ] résolution Wikidata : {len(pairs)} espèces, {len(all_variants)} variantes")
    resolved: dict[tuple[str, str], dict] = {}
    for i in range(0, len(all_variants), WIKIDATA_BATCH_SIZE):
        batch = all_variants[i : i + WIKIDATA_BATCH_SIZE]
        try:
            payload = _sparql_post(_sparql_query(batch))
        except Exception as e:
            print(f"[wd ] batch {i}-{i + len(batch)} a échoué : {e}")
            continue
        for binding in payload.get("results", {}).get("bindings", []):
            taxon_name = binding.get("taxonName", {}).get("value")
            if not taxon_name:
                continue
            origin = variant_to_origin.get(taxon_name)
            if origin is None:
                continue
            qid_uri = binding.get("taxon", {}).get("value", "")
            qid = qid_uri.rsplit("/", 1)[-1] if qid_uri else None
            vern = binding.get("vernacularName", {}).get("value")
            existing = resolved.get(origin)
            if existing is None:
                resolved[origin] = {
                    "qid": qid,
                    "frTitle": binding.get("frTitle", {}).get("value"),
                    "enTitle": binding.get("enTitle", {}).get("value"),
                    "vernacularNames": [vern] if vern else [],
                }
            else:
                # Plusieurs lignes pour le même taxon (P1843 multiple) : on
                # garde le 1er qid/frTitle/enTitle et on agrège les vernacular.
                if vern and vern not in existing["vernacularNames"]:
                    existing["vernacularNames"].append(vern)
        print(f"[wd ] batch {i}-{i + len(batch)}: cumulé {len(resolved)} résolutions")
    print(f"[wd ] {len(resolved)}/{len(pairs)} espèces résolues via Wikidata")
    return resolved


def _wiki_fetch_summary_by_title(fr_title: str) -> dict | None:
    """Fetch summary REST sur un titre Wikipedia déjà canonique (issu de SPARQL).

    Quasi 100% de hit puisque le titre vient de Wikipedia lui-même. Reste un
    risque 429 sur restbase ; backoff exponentiel comme avant.
    """
    encoded = urllib.parse.quote(fr_title.replace(" ", "_"), safe="")
    url = f"https://fr.wikipedia.org/api/rest_v1/page/summary/{encoded}"
    req = urllib.request.Request(url, headers={"User-Agent": WIKI_USER_AGENT})
    last_err: Exception | None = None
    for attempt in range(WIKI_REST_RETRIES_429):
        try:
            with urllib.request.urlopen(req, timeout=WIKI_REST_TIMEOUT_S) as resp:
                return json.loads(resp.read())
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            last_err = e
            if e.code == 429 or 500 <= e.code < 600:
                wait = 2 ** (attempt + 2)
                print(f"[wp ] {e.code} sur '{fr_title}', backoff {wait}s")
                time.sleep(wait)
                continue
            raise
        except (urllib.error.URLError, TimeoutError) as e:
            last_err = e
            wait = 2 ** (attempt + 2)
            print(f"[wp ] timeout sur '{fr_title}', backoff {wait}s")
            time.sleep(wait)
            continue
    raise WikiTransient(f"épuisé sur '{fr_title}': {last_err}")


def fetch_species_info(
    sk: int,
    genre: str,
    espece: str,
    wikidata: dict[tuple[str, str], dict],
) -> dict | None:
    """Pipeline final pour une espèce :
    1. Lit le cache disque (hit, miss, ou absent).
    2. Sinon utilise la résolution Wikidata pré-calculée pour obtenir le titre
       FR + les noms vernaculaires P1843.
    3. Si titre FR connu → fetch summary REST → cache hit avec `vernacularNames`.
    4. Si pas de titre FR mais QID → cache `{qid, vernacularNames}` (lien
       Wikipedia FR absent côté UI, mais qid + P1843 toujours utiles cascade nv).
    5. Si rien → cache `{miss: true}`.

    Contrat cache : un hit avec qid porte toujours `vernacularNames: list[str]`
    (possiblement vide). Les caches pré-Sprint-2 sans cette clé doivent être
    backfillés via `backfill_vernacular_by_qid()`.
    """
    cache_path = WIKIDATA_CACHE_DIR / f"{sk}.json"
    if cache_path.exists():
        with cache_path.open("r", encoding="utf-8") as f:
            cached = json.load(f)
        if cached.get("miss"):
            return None
        return cached

    resolution = wikidata.get((genre, espece))
    if resolution is None:
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        with cache_path.open("w", encoding="utf-8") as f:
            json.dump({"miss": True}, f)
        return None

    qid = resolution.get("qid")
    fr_title = resolution.get("frTitle")
    vernacular_names = list(resolution.get("vernacularNames") or [])
    if not fr_title:
        # QID connu, mais pas de page Wikipedia FR : on cache sans summary.
        result: dict = {"qid": qid} if qid else {"miss": True}
        if qid:
            result["vernacularNames"] = vernacular_names
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        with cache_path.open("w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False)
        return result if qid else None

    time.sleep(WIKI_REST_THROTTLE_S)
    try:
        data = _wiki_fetch_summary_by_title(fr_title)
    except WikiTransient as e:
        print(f"[wp ] transient sur '{fr_title}', pas caché : {e}")
        return None
    if data is None:
        # 404 sur un titre que SPARQL nous a dit exister : très rare.
        result = {"qid": qid} if qid else {"miss": True}
        if qid:
            result["vernacularNames"] = vernacular_names
    else:
        summary = (data.get("extract") or "").strip()
        if not summary or data.get("type") == "disambiguation":
            result = {"qid": qid} if qid else {"miss": True}
            if qid:
                result["vernacularNames"] = vernacular_names
        else:
            result = {
                "wp": data.get("titles", {}).get("canonical")
                      or data.get("title")
                      or fr_title.replace(" ", "_"),
                "qid": qid,
                "summary": summary,
                "vernacularNames": vernacular_names,
            }
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    with cache_path.open("w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False)
    return result if "summary" in result or "qid" in result else None


def compute_vernacular_and_pokedex(
    species_index: dict[tuple[str, str], int],
    nom_commun_by_sk: dict[int, dict[str, int]],
    count_by_sk: dict[int, int],
) -> tuple[list[dict], dict[str, int], list[tuple[str, str, int]]]:
    """Lit les caches Wikidata pour chaque espèce, applique la cascade nv,
    désambigue les collisions, assigne les `n` Pokédex. Écrit le fichier
    `species-index.json` final.

    Cascade (par priorité décroissante) :
    1. `VERNACULAR_OVERRIDES[(g, e)]` (override manuel curaté)
    2. Wikidata P1843 @fr (1re alphabétique)
    3. Wikipedia frTitle (article title, déjà cached as `wp` underscores)
    4. Construit : `{nc} ({I}. {epithète})` ou `{genre} {epithète}` ou
       suffixe « (espèce indéterminée) » pour les `u: true`.

    `n` Pokédex assigné aux espèces avec `count_by_sk[sk] > 0` et non `u`,
    par `sk` croissant — stable d'un build à l'autre tant que `sk` est stable.

    Retour : (entries écrites, dict de compteurs cascade, candidats construits
    sur espèce > 1000 captures). Cette fonction ne raise plus sur la non-unicité
    des `nv` ; la vérification est portée par `verify_species_invariants` qui
    regroupe tous les invariants post-build au même endroit.
    """
    def best_nom_commun(sk: int) -> str | None:
        counts = nom_commun_by_sk.get(sk)
        if not counts:
            return None
        return max(counts.items(), key=lambda kv: kv[1])[0]

    # 1. Backfill incrémental des caches pré-Sprint-2 sans `vernacularNames`.
    qids_to_backfill: list[str] = []
    cache_state: dict[int, dict] = {}
    for (genre, espece), sk in species_index.items():
        cache_path = WIKIDATA_CACHE_DIR / f"{sk}.json"
        if not cache_path.exists():
            cache_state[sk] = {}
            continue
        try:
            with cache_path.open("r", encoding="utf-8") as f:
                cached = json.load(f)
        except (json.JSONDecodeError, OSError):
            cache_state[sk] = {}
            continue
        cache_state[sk] = cached
        if cached.get("miss"):
            continue
        if "vernacularNames" not in cached and cached.get("qid"):
            qids_to_backfill.append(cached["qid"])

    if qids_to_backfill:
        # Dédup conservant l'ordre.
        seen: set[str] = set()
        uniq = [q for q in qids_to_backfill if not (q in seen or seen.add(q))]
        backfilled = backfill_vernacular_by_qid(uniq)
        # Réinjecter dans cache_state + persister sur disque.
        for sk, cached in cache_state.items():
            qid = cached.get("qid")
            if not qid or "vernacularNames" in cached:
                continue
            cached["vernacularNames"] = backfilled.get(qid, [])
            cache_path = WIKIDATA_CACHE_DIR / f"{sk}.json"
            with cache_path.open("w", encoding="utf-8") as f:
                json.dump(cached, f, ensure_ascii=False)

    # 2. Cascade nv pour chaque espèce.
    counters = {
        "nv_via_overrides": 0,
        "nv_via_p1843": 0,
        "nv_via_frtitle": 0,
        "nv_via_construit": 0,
        "nv_disambiguations": 0,
        "pokedex_count": 0,
    }
    entries: list[dict] = []
    construit_high_count: list[tuple[str, str, int]] = []
    for (genre, espece), sk in species_index.items():
        nc = best_nom_commun(sk)
        is_unk = is_unknown_species(genre, espece)
        cached = cache_state.get(sk, {})
        nv: str | None = None

        override = VERNACULAR_OVERRIDES.get((genre, espece))
        if override:
            nv = override
            counters["nv_via_overrides"] += 1
        else:
            p1843 = first_p1843(cached.get("vernacularNames") or [])
            if p1843:
                nv = p1843
                counters["nv_via_p1843"] += 1
            else:
                wp = cached.get("wp")
                if wp:
                    # Le `wp` est `Underscored_Title` ; on délimite proprement.
                    nv = wp.replace("_", " ").strip() or None
                    if nv:
                        counters["nv_via_frtitle"] += 1
                if not nv:
                    nv = construct_vernacular(genre, espece, nc, is_unk)
                    counters["nv_via_construit"] += 1
                    sk_count = count_by_sk.get(sk, 0)
                    if sk_count > 1000 and not is_unk:
                        construit_high_count.append((genre, espece, sk_count))

        entry: dict = {"i": sk, "g": genre, "e": espece}
        if nc:
            entry["nc"] = nc
        if is_unk:
            entry["u"] = True
        entry["nv"] = nv
        entries.append(entry)

    # 3. Désambiguation des collisions sur nv. La vérification d'unicité finale
    # est portée par `verify_species_invariants` (cf. invariant #4).
    counters["nv_disambiguations"] = disambiguate_vernaculars(entries)

    # 4. Numérotation Pokédex par sk croissant, identifiées + count > 0.
    entries.sort(key=lambda e: e["i"])
    next_pokedex = 1
    for e in entries:
        if e.get("u"):
            continue
        if count_by_sk.get(e["i"], 0) <= 0:
            continue
        e["n"] = next_pokedex
        next_pokedex += 1
    counters["pokedex_count"] = next_pokedex - 1

    # 5. Écriture finale species-index.json.
    with OUT_SPECIES_INDEX.open("w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, separators=(",", ":"))

    construit_high_count.sort(key=lambda gec: -gec[2])
    return entries, counters, construit_high_count


def _sparql_query_p1843_by_qid(qids: list[str]) -> str:
    """SPARQL query par QID pour récupérer P1843 @fr seulement.

    Utilisée par le backfill des caches pré-Sprint-2 qui ont déjà résolu le
    qid + summary mais ne portent pas le champ `vernacularNames`. Beaucoup
    plus léger que la query principale (un seul triple à matcher).
    """
    encoded = " ".join(f"wd:{q}" for q in qids)
    return f"""
SELECT ?taxon ?vernacularName WHERE {{
  VALUES ?taxon {{ {encoded} }}
  ?taxon wdt:P1843 ?vernacularName .
  FILTER(LANG(?vernacularName) = "fr")
}}
"""


def backfill_vernacular_by_qid(qids: list[str]) -> dict[str, list[str]]:
    """Récupère P1843 @fr pour une liste de qids déjà connus, en batchs.

    Préserve l'ordre alphabétique d'arrivée — l'agrégation se fait via
    set-comme-list pour éviter les doublons. QIDs sans P1843 absents du dict.
    """
    if not qids:
        return {}
    by_qid: dict[str, list[str]] = {q: [] for q in qids}
    print(f"[bfl] backfill P1843 pour {len(qids)} qids")
    for i in range(0, len(qids), WIKIDATA_BATCH_SIZE):
        batch = qids[i : i + WIKIDATA_BATCH_SIZE]
        try:
            payload = _sparql_post(_sparql_query_p1843_by_qid(batch))
        except Exception as e:
            print(f"[bfl] batch {i}-{i + len(batch)} a échoué : {e}")
            continue
        for binding in payload.get("results", {}).get("bindings", []):
            qid_uri = binding.get("taxon", {}).get("value", "")
            qid = qid_uri.rsplit("/", 1)[-1] if qid_uri else None
            vern = binding.get("vernacularName", {}).get("value")
            if not qid or not vern:
                continue
            existing = by_qid.get(qid)
            if existing is None:
                continue
            if vern not in existing:
                existing.append(vern)
    hits = sum(1 for v in by_qid.values() if v)
    print(f"[bfl] {hits}/{len(qids)} qids ont au moins un P1843 @fr")
    # Filtrer les qids sans aucun nom (rien à backfill côté cache).
    return {q: v for q, v in by_qid.items() if v}


def _build_species_entry(
    sk: int,
    genre: str,
    espece: str,
    count: int,
    heights: list[int],
    circs: list[int],
    arr_counts: dict[str, int],
    arr_total: dict[str, int],
    total_arbres: int,
    wiki: dict | None,
    pdf_url: str | None,
) -> dict:
    """Calcule stats locales + assemble les champs Wikipedia déjà fetchés."""
    stats: dict = {
        "count": count,
        "proportion": round(count / total_arbres, 4) if total_arbres else 0.0,
    }
    if heights:
        stats["medianHm"] = int(round(statistics.median(heights)))
    if circs:
        stats["medianCircCm"] = int(round(statistics.median(circs)))

    top_abs = sorted(arr_counts.items(), key=lambda kv: -kv[1])[:3]
    stats["topArrAbs"] = [{"arr": a, "count": c} for a, c in top_abs]

    over: list[tuple[str, float, int]] = []
    for arr, c in arr_counts.items():
        if c < 5:
            continue
        if arr_total.get(arr, 0) <= 0 or count <= 0 or total_arbres <= 0:
            continue
        ratio = (c * total_arbres) / (count * arr_total[arr])
        over.append((arr, ratio, c))
    over.sort(key=lambda x: -x[1])
    stats["topArrOver"] = [
        {"arr": a, "ratio": round(r, 2), "count": c}
        for a, r, c in over[:3]
    ]

    entry: dict = {"i": sk, "stats": stats}
    if wiki:
        if wiki.get("wp"):
            entry["wp"] = wiki["wp"]
        if wiki.get("qid"):
            entry["qid"] = wiki["qid"]
        if wiki.get("summary"):
            entry["summary"] = wiki["summary"]
    if pdf_url:
        entry["pdf"] = pdf_url
    return entry


def write_species_info(
    species_index: dict[tuple[str, str], int],
    count_by_sk: dict[int, int],
    heights_by_sk: dict[int, list[int]],
    circs_by_sk: dict[int, list[int]],
    arr_by_sk: dict[int, dict[str, int]],
    arr_total: dict[str, int],
    total_arbres: int,
    essences_pdf: dict[tuple[str, str], str],
) -> None:
    WIKIDATA_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    print(
        f"[wp ] Wikipedia (Wikidata SPARQL + REST summary) pour {len(species_index)} espèces "
        f"(cache: {WIKIDATA_CACHE_DIR.relative_to(ROOT)})"
    )

    # 1. Identifier les paires non encore résolues (cache miss/hit confirmés).
    pairs_to_resolve: list[tuple[int, str, str]] = []
    for (genre, espece), sk in species_index.items():
        cache_path = WIKIDATA_CACHE_DIR / f"{sk}.json"
        if not cache_path.exists():
            pairs_to_resolve.append((sk, genre, espece))

    # 2. SPARQL batched pour les non résolus uniquement.
    wikidata_resolution: dict[tuple[str, str], dict] = {}
    if pairs_to_resolve:
        wikidata_resolution = resolve_via_wikidata(pairs_to_resolve)
    else:
        print("[wd ] tout est en cache, skip Wikidata SPARQL")

    # 3. Pour chaque espèce, fetch summary (cache disque géré dans la fonction).
    entries: list[dict] = []
    wiki_hit = 0
    pdf_hit = 0
    for (genre, espece), sk in species_index.items():
        wiki = fetch_species_info(sk, genre, espece, wikidata_resolution)
        pdf_url = essences_pdf.get((genre, espece))
        entry = _build_species_entry(
            sk=sk,
            genre=genre,
            espece=espece,
            count=count_by_sk.get(sk, 0),
            heights=heights_by_sk.get(sk, []),
            circs=circs_by_sk.get(sk, []),
            arr_counts=arr_by_sk.get(sk, {}),
            arr_total=arr_total,
            total_arbres=total_arbres,
            wiki=wiki,
            pdf_url=pdf_url,
        )
        if "summary" in entry:
            wiki_hit += 1
        if "pdf" in entry:
            pdf_hit += 1
        entries.append(entry)
        if len(entries) % 100 == 0:
            print(f"[wp ] {len(entries)}/{len(species_index)} traités, {wiki_hit} avec summary")

    entries.sort(key=lambda e: e["i"])
    with OUT_SPECIES_INFO.open("w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, separators=(",", ":"))

    info_kb = OUT_SPECIES_INFO.stat().st_size // 1024
    print(f"[wp ] {wiki_hit}/{len(species_index)} espèces avec summary Wikipedia")
    print(f"[ess ] {pdf_hit}/{len(species_index)} espèces avec fiche PDF Ville de Paris")
    print(f"       → {OUT_SPECIES_INFO.name} ({info_kb} Ko)")


_HTML_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"\s+")


def strip_html(s: str | None) -> str | None:
    """Strip HTML tags + decode entities + collapse whitespace.

    OpenData glisse parfois des `<p>`, `<br/>` ou `&nbsp;` dans `com_descriptif`
    — on normalise au build pour livrer du texte plat à Kotlin (pas de Html.fromHtml
    côté UI).
    """
    if not s:
        return None
    text = _HTML_TAG_RE.sub(" ", s)
    text = html.unescape(text)
    text = _WS_RE.sub(" ", text).strip()
    return text or None


def _fetch_remarquables_page(offset: int, limit: int) -> dict:
    params = urllib.parse.urlencode({"limit": limit, "offset": offset})
    url = f"{REMARQUABLES_URL}?{params}"
    req = urllib.request.Request(url, headers={"User-Agent": WIKI_USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


def _record_idbase(rec: dict) -> int | None:
    """Extrait `arbres_idbase` (parfois sérialisé en float `2002385.0`)."""
    raw = rec.get("arbres_idbase")
    if raw is None:
        return None
    try:
        return int(float(raw))
    except (ValueError, TypeError):
        return None


def fetch_remarquables() -> list[dict]:
    """Fetch tous les arbres remarquables via l'API V2 OpenData.

    Cache disque par `idbase` dans `tools/.remarquables-cache/{idbase}.json`
    (record OpenData brut). On refetch systématiquement la liste paginée
    (cheap, ~2 pages pour 185 records), mais on lit le payload depuis le
    cache si présent — idempotent et résistant aux re-runs.
    """
    REMARQUABLES_CACHE_DIR.mkdir(parents=True, exist_ok=True)

    records: list[dict] = []
    offset = 0
    total: int | None = None
    print(f"[rmq ] fetch arbresremarquablesparis (cache: {REMARQUABLES_CACHE_DIR.relative_to(ROOT)})")

    while True:
        page = _fetch_remarquables_page(offset, REMARQUABLES_PAGE_SIZE)
        if total is None:
            total = int(page.get("total_count", 0))
            print(f"[rmq ] total_count={total}")
        page_records = page.get("results", [])
        if not page_records:
            break
        for rec in page_records:
            idbase = _record_idbase(rec)
            if idbase is None:
                continue
            cache_path = REMARQUABLES_CACHE_DIR / f"{idbase}.json"
            if cache_path.exists():
                with cache_path.open("r", encoding="utf-8") as f:
                    records.append(json.load(f))
            else:
                with cache_path.open("w", encoding="utf-8") as f:
                    json.dump(rec, f, ensure_ascii=False)
                records.append(rec)
        offset += len(page_records)
        if total is not None and offset >= total:
            break
    print(f"[rmq ] {len(records)} records récupérés")
    return records


def _annee_plantation(rec: dict) -> str | None:
    """Année de plantation propre.

    `com_annee_plantation` contient soit "1860", soit "Inconnue", soit None.
    `arbres_dateplantation` synthétise `1700-01-01T00:09:21+00:00` pour les
    inconnues — inutilisable. On préfère `com_annee_plantation` filtré.
    """
    raw = to_str_or_none(rec.get("com_annee_plantation", ""))
    if raw is None:
        return None
    if raw.lower().startswith("inconn"):
        return None
    return raw


def write_remarquables_info(records: list[dict], ids_in_csv: set[int]) -> None:
    """Filtre + transforme + écrit `remarquables-info.json`.

    On ne garde que les idbase présents dans la DB Room (sinon fiche inatteignable).
    Champs vides/null omis pour compresser la sortie.
    """
    entries: list[dict] = []
    orphans = 0
    with_resume = 0
    with_desc = 0
    with_date = 0
    with_qualif = 0

    for rec in records:
        idbase = _record_idbase(rec)
        if idbase is None:
            continue
        if idbase not in ids_in_csv:
            orphans += 1
            continue

        resume = to_str_or_none(rec.get("com_resume", ""))
        desc = strip_html(rec.get("com_descriptif"))
        annee = _annee_plantation(rec)
        cultivar = to_str_or_none(rec.get("arbres_varieteoucultivar", ""))
        qualif = to_str_or_none(rec.get("com_qualification_rem", ""))

        entry: dict = {"id": idbase}
        if qualif:
            entry["qualif"] = qualif
            with_qualif += 1
        if resume:
            entry["resume"] = resume
            with_resume += 1
        if desc:
            entry["desc"] = desc
            with_desc += 1
        if annee:
            entry["plante"] = annee
            with_date += 1
        if cultivar:
            entry["cultivar"] = cultivar

        # Skip si tout vide : la fiche affichera juste le badge ★ sans card.
        if len(entry) == 1:
            continue
        entries.append(entry)

    entries.sort(key=lambda e: e["id"])
    with OUT_REMARQUABLES_INFO.open("w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, separators=(",", ":"))

    info_kb = OUT_REMARQUABLES_INFO.stat().st_size // 1024
    print(f"[rmq ] {len(entries)} arbres remarquables enrichis")
    print(f"        - avec qualification: {with_qualif}")
    print(f"        - avec resume: {with_resume}")
    print(f"        - avec desc:   {with_desc}")
    print(f"        - avec annee plantation: {with_date}")
    if orphans:
        print(f"        - {orphans} orphelins ignorés (idbase absent du CSV)")
    print(f"        → {OUT_REMARQUABLES_INFO.name} ({info_kb} Ko)")


def _fetch_essences_page(offset: int, limit: int) -> dict:
    params = urllib.parse.urlencode({
        "limit": limit,
        "offset": offset,
        "select": "nom_latin,nom_commun,nom_fichier_pdf_associe",
    })
    url = f"{ESSENCES_URL}?{params}"
    req = urllib.request.Request(url, headers={"User-Agent": WIKI_USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


def fetch_essences() -> list[dict]:
    """Fetch les ~200 fiches PDF du dataset `fiches-essences-du-guide-des-essences-de-paris`.

    Cache disque par `pdf_id` (id de fichier OpenData) dans
    `tools/.essences-cache/{pdf_id}.json` — payload OpenData brut. La liste
    paginée est refetchée à chaque run (cheap, 2 pages), mais les records sont
    relus depuis le cache si présents. Records sans PDF associé skippés.
    """
    ESSENCES_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    records: list[dict] = []
    offset = 0
    total: int | None = None
    print(f"[ess ] fetch fiches-essences (cache: {ESSENCES_CACHE_DIR.relative_to(ROOT)})")

    while True:
        page = _fetch_essences_page(offset, ESSENCES_PAGE_SIZE)
        if total is None:
            total = int(page.get("total_count", 0))
            print(f"[ess ] total_count={total}")
        page_records = page.get("results", [])
        if not page_records:
            break
        for rec in page_records:
            pdf = rec.get("nom_fichier_pdf_associe") or {}
            pdf_id = pdf.get("id")
            if not pdf_id:
                continue
            cache_path = ESSENCES_CACHE_DIR / f"{pdf_id}.json"
            if cache_path.exists():
                with cache_path.open("r", encoding="utf-8") as f:
                    records.append(json.load(f))
            else:
                with cache_path.open("w", encoding="utf-8") as f:
                    json.dump(rec, f, ensure_ascii=False)
                records.append(rec)
        offset += len(page_records)
        if total is not None and offset >= total:
            break
    print(f"[ess ] {len(records)} fiches récupérées")
    return records


def _parse_essence_taxon(nom_latin: str) -> tuple[str, str] | None:
    """Extrait `(genre, espece)` d'un `nom_latin` du dataset essences.

    Format général : `Genre espece [...trailing cultivar/subsp...]`.
    Conventions à reproduire pour matcher `species-index.json` :
    - Hybrides « Genre x espece » → `e = "x espece"` (espace après le x),
      cohérent avec la sortie de `tools/build_dataset.py` sur le CSV
      les-arbres (cf. `Platanus`/`x hispanica`).
    - Hybrides intergénériques « X Genre espece » (ex: « X Chitalpa
      tashkentensis ») : skippés, le species-index ne les contient pas.
    - Cultivars (`'Globosum'`, `subsp. nigra`, etc.) : ignorés ; on retombe
      sur l'espèce mère, plusieurs records peuvent matcher la même paire et
      `_build_essences_index` choisit le PDF le plus générique.
    """
    parts = (nom_latin or "").strip().split()
    if not parts:
        return None
    if parts[0] in ("X", "×") and len(parts) >= 3:
        return None
    if len(parts) < 2:
        return None
    genre = parts[0]
    if len(parts) >= 3 and parts[1] in ("x", "×"):
        espece = f"x {parts[2]}"
    else:
        espece = parts[1]
    # Skipper « Ulmus 'Sapporo' » & co — cultivar sans nom d'espèce parent,
    # impossible à matcher contre species-index (qui exige `(genre, espece)`).
    if espece[:1] in ("'", '"', "‘", "’"):
        return None
    return (genre, espece)


def _build_essences_index(records: list[dict]) -> dict[tuple[str, str], str]:
    """`(genre, espece) → url_pdf`, en privilégiant le record le plus générique.

    Plusieurs fiches peuvent partager une même paire `(genre, espece)` (ex:
    `Acer platanoides` et plusieurs cultivars `Acer platanoides 'Globosum'`).
    On garde la fiche au `nom_latin` le plus court — c'est l'espèce nue, pas
    un cultivar. Égalité → premier rencontré.
    """
    chosen: dict[tuple[str, str], tuple[int, str]] = {}
    for rec in records:
        nom_latin = (rec.get("nom_latin") or "").strip()
        taxon = _parse_essence_taxon(nom_latin)
        if taxon is None:
            continue
        pdf = rec.get("nom_fichier_pdf_associe") or {}
        url = pdf.get("url")
        if not url:
            continue
        score = len(nom_latin)
        prev = chosen.get(taxon)
        if prev is None or score < prev[0]:
            chosen[taxon] = (score, url)
    return {k: v[1] for k, v in chosen.items()}


def _format_int_fr(n: int) -> str:
    """`213042 -> "213 042"` (espace insécable narrow), pour rendu visuel."""
    return f"{n:,}".replace(",", " ")


def _capitalize_fr(s: str) -> str:
    return s[:1].upper() + s[1:] if s else s


def _percent(num: int, denom: int) -> float:
    return (num * 100.0 / denom) if denom else 0.0


def write_splash_tips(
    species_index: dict[tuple[str, str], int],
    count_by_sk: dict[int, int],
    heights_by_sk: dict[int, list[int]],
    circs_by_sk: dict[int, list[int]],
    arr_by_sk: dict[int, dict[str, int]],
    arr_total: dict[str, int],
    total_arbres: int,
    total_remarquables: int,
    nom_commun_by_sk: dict[int, dict[str, int]],
) -> None:
    """Génère `app/src/main/assets/splash-tips.json` (fusion static + dataset).

    Static : `tools/splash-tips-static.json` (intro + history + popculture +
    player templates écrits à la main). Dataset : tips générés depuis les
    agrégats du CSV. Ton homogène pince-sans-rire / décalé.

    Format de sortie :
      { "version": 1,
        "intro": [ tipId, ... 10 ids ],   # ordre figé pour le 1er lancement
        "tips": [ { id, category, text, requires? }, ... ] }
    """
    if not STATIC_SPLASH_TIPS.exists():
        raise FileNotFoundError(f"manque {STATIC_SPLASH_TIPS}")
    with STATIC_SPLASH_TIPS.open("r", encoding="utf-8") as f:
        static = json.load(f)

    intro_tips: list[dict] = list(static.get("intro", []))
    other_tips: list[dict] = list(static.get("tips", []))

    if len(intro_tips) != 10:
        raise ValueError(
            f"intro doit contenir exactement 10 tips, trouvé {len(intro_tips)}"
        )
    for t in intro_tips:
        if t.get("requires"):
            raise ValueError(
                f"intro tip '{t['id']}' ne peut pas avoir de `requires` "
                f"(la 1re session a 0 capture)"
            )

    # --- Sanity check placeholders sur l'ensemble static (intro + tips). -----
    # Un placeholder absent du set runtime = bug silencieux à l'écran, on
    # bloque le build. Les tips dataset générés ci-dessous sont contrôlés
    # à la source (pas de `{xxx}` côté template Python).
    for t in intro_tips + other_tips:
        text = t.get("text", "")
        for ph in PLACEHOLDER_RE.findall(text):
            if ph not in SUPPORTED_PLACEHOLDERS:
                raise ValueError(
                    f"tip '{t['id']}' utilise placeholder inconnu '{{{ph}}}' "
                    f"(supportés: {sorted(SUPPORTED_PLACEHOLDERS)})"
                )

    # --- Génération des tips dataset. ----------------------------------------
    dataset_tips: list[dict] = []

    def push(tip_id: str, text: str) -> None:
        dataset_tips.append({"id": tip_id, "category": "dataset", "text": text})

    def species_label(sk: int, fallback_genre: str, fallback_espece: str) -> str:
        """Nom commun le plus fréquent ou fallback sur le binomial.

        Renvoie tel quel — les noms du CSV sont capitalisés (« Platane »,
        « Marronnier »). Les phrases qui les insèrent en milieu de texte
        appellent `_lower_label` ; celles qui les utilisent en début de
        phrase les laissent tels quels.
        """
        counts = nom_commun_by_sk.get(sk)
        if counts:
            return max(counts.items(), key=lambda kv: kv[1])[0]
        return f"{fallback_genre} {fallback_espece}"

    def _lower_label(label: str) -> str:
        """Met le 1er mot en minuscule si majuscule simple ; preserve les
        noms à particule ou les binomes scientifiques (Genre espece)."""
        if not label:
            return label
        # Heuristique : on ne lower que si le label est un nom commun mono-mot
        # ou commence par une majuscule simple (« Platane », « Marronnier »).
        # Pour les binomes scientifiques (« Acer platanoides »), le 1er mot est
        # déjà capitalisé par convention, donc on lower aussi.
        return label[0].lower() + label[1:]

    def _le_or_l(label: str) -> str:
        """« Le marronnier » vs « L'érable ». Élision devant voyelle ou h muet."""
        if not label:
            return "Le "
        first = label[0].lower()
        if first in "aeiouyhâàäéèêëîïôöûü":
            return "L'"
        return "Le "

    # 1. Stats globales.
    push(
        "dataset.total_arbres",
        f"{_format_int_fr(total_arbres)} arbres parisiens dans la base. "
        f"Personne ne les a comptés à la main."
    )
    push(
        "dataset.total_especes",
        f"{len(species_index)} espèces différentes vivent à Paris. Plus de "
        f"diversité que dans certains menus."
    )
    push(
        "dataset.total_remarquables",
        f"{total_remarquables} arbres parisiens sont qualifiés de "
        f"« remarquables ». Les autres font sans."
    )

    # 2. Top espèces — concentration et noms.
    sorted_sk = sorted(count_by_sk.items(), key=lambda kv: -kv[1])
    sk_to_pair = {sk: pair for pair, sk in species_index.items()}
    if sorted_sk:
        top_sk, top_count = sorted_sk[0]
        top_genre, top_espece = sk_to_pair[top_sk]
        top_label = species_label(top_sk, top_genre, top_espece)
        top_pct = _percent(top_count, total_arbres)
        push(
            "dataset.top_species_first",
            f"L'espèce la plus courante à Paris : {top_label}. "
            f"{_format_int_fr(top_count)} arbres, soit {top_pct:.0f} % du total."
        )

    if len(sorted_sk) >= 2:
        sk2, c2 = sorted_sk[1]
        g2, e2 = sk_to_pair[sk2]
        label2 = species_label(sk2, g2, e2)
        ll2 = _lower_label(label2)
        push(
            "dataset.top_species_second",
            f"{_le_or_l(ll2)}{ll2} occupe la 2e place du Paris arboré "
            f"({_format_int_fr(c2)} arbres). Solide podium."
        )

    if len(sorted_sk) >= 3:
        sk3, c3 = sorted_sk[2]
        g3, e3 = sk_to_pair[sk3]
        label3 = species_label(sk3, g3, e3)
        ll3 = _lower_label(label3)
        push(
            "dataset.top_species_third",
            f"{_le_or_l(ll3)}{ll3} complète le podium parisien "
            f"({_format_int_fr(c3)} arbres). Sans bruit."
        )

    if len(sorted_sk) >= 5:
        top5_sum = sum(c for _, c in sorted_sk[:5])
        top5_pct = _percent(top5_sum, total_arbres)
        push(
            "dataset.top5_share",
            f"Les 5 espèces les plus nombreuses pèsent {top5_pct:.0f} % du "
            f"total. Le club fermé."
        )

    if len(sorted_sk) >= 10:
        top10_sum = sum(c for _, c in sorted_sk[:10])
        top10_pct = _percent(top10_sum, total_arbres)
        push(
            "dataset.top10_share",
            f"10 espèces représentent {top10_pct:.0f} % des arbres parisiens. "
            f"Les 897 autres se partagent le reste."
        )

    # 3. Espèces rares — orphelines, doublons, presque-rien.
    singletons = sum(1 for _, c in sorted_sk if c == 1)
    duos = sum(1 for _, c in sorted_sk if c == 2)
    petits = sum(1 for _, c in sorted_sk if 1 <= c <= 5)
    if singletons:
        push(
            "dataset.singletons",
            f"{singletons} espèces n'ont qu'un seul représentant à Paris. "
            f"Solitude statistique."
        )
    if duos:
        push(
            "dataset.duos",
            f"{duos} espèces n'ont que 2 représentants à Paris. "
            f"Une amitié botanique."
        )
    if petits:
        push(
            "dataset.under_five",
            f"{petits} espèces ont 5 arbres ou moins dans la ville. "
            f"On parle d'espèces confidentielles."
        )

    # 4. Démesure — hauteur et circonférence max.
    # Le CSV OpenData contient des outliers de saisie (10778 m pour un
    # marronnier, etc). On filtre par seuils plausibles : un arbre parisien
    # culmine raisonnablement à 50-55 m (peuplier de plein-vent) et un tronc
    # n'excède pas ~12 m de circonférence (vieux platane historique).
    HEIGHT_PLAUSIBLE_MAX_M = 60
    CIRC_PLAUSIBLE_MAX_CM = 1200

    max_height = 0
    max_height_sk = None
    for sk, hs in heights_by_sk.items():
        for h in hs:
            if 0 < h <= HEIGHT_PLAUSIBLE_MAX_M and h > max_height:
                max_height = h
                max_height_sk = sk
    if max_height_sk is not None and max_height >= 20:
        g, e = sk_to_pair[max_height_sk]
        label = species_label(max_height_sk, g, e)
        push(
            "dataset.giant_height",
            f"Le plus grand arbre recensé fait {max_height} m. "
            f"C'est un {_lower_label(label)}."
        )

    max_circ = 0
    max_circ_sk = None
    for sk, cs in circs_by_sk.items():
        for c in cs:
            if 0 < c <= CIRC_PLAUSIBLE_MAX_CM and c > max_circ:
                max_circ = c
                max_circ_sk = sk
    if max_circ_sk is not None and max_circ >= 200:
        g, e = sk_to_pair[max_circ_sk]
        label = species_label(max_circ_sk, g, e)
        push(
            "dataset.giant_circ",
            f"Le tronc le plus large fait {max_circ} cm de tour. "
            f"Un {_lower_label(label)}, sans surprise."
        )

    # 5. Arrondissements.
    arr_sorted = sorted(arr_total.items(), key=lambda kv: -kv[1])
    paris_arrs = [(a, c) for a, c in arr_sorted if is_paris_arr(a)]
    if paris_arrs:
        top_arr, top_arr_c = paris_arrs[0]
        push(
            "dataset.top_arr",
            f"L'arrondissement le mieux fourni est le {top_arr}, "
            f"avec {_format_int_fr(top_arr_c)} arbres recensés."
        )
        bottom_arr, bottom_arr_c = paris_arrs[-1]
        push(
            "dataset.bottom_arr",
            f"L'arrondissement le moins arboré est le {bottom_arr}, "
            f"avec {_format_int_fr(bottom_arr_c)} arbres seulement. "
            f"Ce n'est pas leur faute."
        )

    bois = [(a, c) for a, c in arr_total.items() if "Bois" in a]
    if bois:
        bois_total = sum(c for _, c in bois)
        push(
            "dataset.bois",
            f"Les bois de Boulogne et Vincennes pèsent {_format_int_fr(bois_total)} "
            f"arbres à eux deux. Sans eux, Paris respirerait moins fort."
        )

    # 6. Hauteur médiane (statistique de calme).
    all_heights: list[int] = []
    for hs in heights_by_sk.values():
        all_heights.extend(hs)
    if all_heights:
        median_h = int(round(statistics.median(all_heights)))
        if median_h >= 5:
            push(
                "dataset.median_height",
                f"Un arbre parisien médian fait {median_h} m de haut. "
                f"Pas de quoi se vanter, pas de quoi rougir."
            )

    # 7. Quelques espèces emblématiques — formulation évitant la pluralisation
    # automatique (les noms communs CSV mélangent singulier/pluriel et compléments).
    iconic_named = [
        ("Aesculus", "hippocastanum", "marronnier d'Inde"),
        ("Tilia", "platyphyllos", "tilleul à grandes feuilles"),
        ("Tilia", "cordata", "tilleul à petites feuilles"),
        ("Quercus", "robur", "chêne pédonculé"),
        ("Acer", "platanoides", "érable plane"),
        ("Robinia", "pseudoacacia", "robinier faux-acacia"),
        ("Ginkgo", "biloba", "ginkgo"),
        ("Cedrus", "libani", "cèdre du Liban"),
    ]
    for genre, espece, label in iconic_named:
        sk = species_index.get((genre, espece))
        if sk is None:
            continue
        c = count_by_sk.get(sk, 0)
        if c <= 0:
            continue
        push(
            f"dataset.iconic.{genre.lower()}_{espece.lower()}",
            f"{_le_or_l(label)}{label} a {_format_int_fr(c)} représentants à Paris. "
            f"On en croise sans le savoir."
        )

    # 8. Distribution des counts — clubs, Pareto, médiane des espèces.
    n_above_10000 = sum(1 for _, c in sorted_sk if c >= 10_000)
    n_above_1000 = sum(1 for _, c in sorted_sk if c >= 1_000)
    n_above_100 = sum(1 for _, c in sorted_sk if c >= 100)
    n_under_10 = sum(1 for _, c in sorted_sk if 1 <= c < 10)
    if n_above_10000:
        push(
            "dataset.club_10k",
            f"{n_above_10000} espèces dépassent les 10 000 individus à Paris. "
            f"Le club très fermé."
        )
    if n_above_1000:
        push(
            "dataset.club_1k",
            f"{n_above_1000} espèces comptent au moins 1 000 arbres à Paris. "
            f"Tout le reste joue moins fort."
        )
    if n_above_100:
        push(
            "dataset.club_100",
            f"{n_above_100} espèces parviennent à 100 individus ou plus. "
            f"Sur 907. Les autres sont rares à divers degrés."
        )
    if n_under_10:
        push(
            "dataset.under_10",
            f"{n_under_10} espèces ont moins de 10 arbres dans Paris. "
            f"Une absence remarquable d'espèce."
        )

    # Pareto : combien d'espèces pour atteindre 50 % et 80 % du total.
    cumul = 0
    n_for_50 = 0
    n_for_80 = 0
    for i, (_, c) in enumerate(sorted_sk, start=1):
        cumul += c
        if n_for_50 == 0 and cumul >= total_arbres * 0.5:
            n_for_50 = i
        if n_for_80 == 0 and cumul >= total_arbres * 0.8:
            n_for_80 = i
            break
    if n_for_50:
        push(
            "dataset.pareto_50",
            f"Connaître {n_for_50} espèces, c'est connaître la moitié des arbres "
            f"parisiens. Excellent retour sur effort."
        )
    if n_for_80:
        push(
            "dataset.pareto_80",
            f"{n_for_80} espèces couvrent 80 % des arbres de Paris. "
            f"Les 20 % restants se cachent."
        )

    if len(sorted_sk) >= 50:
        top50_sum = sum(c for _, c in sorted_sk[:50])
        top50_pct = _percent(top50_sum, total_arbres)
        push(
            "dataset.top50_share",
            f"Les 50 espèces les plus courantes pèsent {top50_pct:.0f} % "
            f"des arbres parisiens."
        )

    # Espèce médiane (rang n/2 par count décroissant).
    if len(sorted_sk) >= 2:
        median_rank = len(sorted_sk) // 2
        median_sk, median_c = sorted_sk[median_rank]
        if median_c >= 1:
            mg, me = sk_to_pair[median_sk]
            mlabel = species_label(median_sk, mg, me)
            push(
                "dataset.median_species",
                f"L'espèce médiane (rang {median_rank} sur {len(sorted_sk)}) "
                f"compte {median_c} arbre{'s' if median_c > 1 else ''} : "
                f"{_lower_label(mlabel)}."
            )

    # 9. Démesure étendue — combien d'arbres dépassent les seuils.
    all_heights: list[int] = [h for hs in heights_by_sk.values() for h in hs
                              if 0 < h <= HEIGHT_PLAUSIBLE_MAX_M]
    n_h_30 = sum(1 for h in all_heights if h >= 30)
    n_h_20 = sum(1 for h in all_heights if h >= 20)
    n_h_under_5 = sum(1 for h in all_heights if h < 5)
    if n_h_30:
        push(
            "dataset.height_30",
            f"{_format_int_fr(n_h_30)} arbres parisiens dépassent les 30 m. "
            f"Pas si fréquent en ville."
        )
    if n_h_20:
        push(
            "dataset.height_20",
            f"{_format_int_fr(n_h_20)} arbres parisiens font 20 m ou plus. "
            f"On les remarque souvent sans les voir."
        )
    if n_h_under_5:
        push(
            "dataset.height_under_5",
            f"{_format_int_fr(n_h_under_5)} arbres parisiens font moins de 5 m. "
            f"Jeunes plants ou essences modestes."
        )

    all_circs: list[int] = [c for cs in circs_by_sk.values() for c in cs
                            if 0 < c <= CIRC_PLAUSIBLE_MAX_CM]
    n_c_400 = sum(1 for c in all_circs if c >= 400)
    n_c_200 = sum(1 for c in all_circs if c >= 200)
    if n_c_400:
        push(
            "dataset.circ_400",
            f"{_format_int_fr(n_c_400)} troncs parisiens font plus de 4 m de tour. "
            f"Tu sauras qui embrasser."
        )
    if n_c_200:
        push(
            "dataset.circ_200",
            f"{_format_int_fr(n_c_200)} troncs parisiens dépassent les 2 m de circonférence. "
            f"De vieilles connaissances."
        )

    # 10. Arrondissements — top 5, intra-muros vs bois, diversité, espèce dominante.
    intra_muros_total = sum(c for a, c in arr_total.items() if is_paris_arr(a))
    bois_total = sum(c for a, c in arr_total.items() if "Bois" in a)
    if intra_muros_total and bois_total:
        push(
            "dataset.intra_vs_bois",
            f"Paris intra-muros : {_format_int_fr(intra_muros_total)} arbres. "
            f"Les bois : {_format_int_fr(bois_total)}. La répartition surprend."
        )

    paris_arrs_sorted = sorted(
        ((a, c) for a, c in arr_total.items() if is_paris_arr(a)),
        key=lambda kv: -kv[1]
    )
    if len(paris_arrs_sorted) >= 5:
        top5_arrs = paris_arrs_sorted[:5]
        top5_arrs_sum = sum(c for _, c in top5_arrs)
        top5_arrs_pct = _percent(top5_arrs_sum, intra_muros_total) if intra_muros_total else 0
        push(
            "dataset.top5_arr",
            f"Les 5 arrondissements les plus arborés cumulent {top5_arrs_pct:.0f} % "
            f"des arbres intra-muros. Le reste se serre."
        )
        labels = ", ".join(a for a, _ in top5_arrs[:3])
        push(
            "dataset.top3_arr_named",
            f"Top 3 des arrondissements arborés : {labels}. "
            f"Pas forcément ceux qu'on attendait."
        )

    if len(paris_arrs_sorted) >= 2:
        worst_two = paris_arrs_sorted[-2:]
        names = " et ".join(a for a, _ in worst_two)
        push(
            "dataset.bottom2_arr",
            f"Les arrondissements les moins arborés sont le {names}. "
            f"Conséquence du tissu urbain dense."
        )

    # Diversité par arrondissement : nb d'espèces distinctes.
    arr_species_count: dict[str, int] = defaultdict(int)
    for sk, arr_counts in arr_by_sk.items():
        for arr in arr_counts.keys():
            arr_species_count[arr] += 1
    paris_diversity = sorted(
        ((a, n) for a, n in arr_species_count.items() if is_paris_arr(a)),
        key=lambda kv: -kv[1]
    )
    if paris_diversity:
        most_div, n_most = paris_diversity[0]
        push(
            "dataset.diversity_top",
            f"L'arrondissement le plus divers en espèces est le {most_div}, "
            f"avec {n_most} espèces différentes recensées."
        )
        least_div, n_least = paris_diversity[-1]
        push(
            "dataset.diversity_bottom",
            f"L'arrondissement le moins divers ne compte que {n_least} espèces : "
            f"le {least_div}. La place manque pour la variété."
        )

    # Espèce dominante par arrondissement parisien : on agrège pour
    # signaler la sur-domination (ex. platane gagne dans 16/20 arr.) et on
    # liste les arrondissements « atypiques » (où ce n'est pas la 1re espèce
    # parisienne globale qui domine localement).
    arr_winner: dict[str, int] = {}  # arr -> sk gagnant
    for arr in [a for a, _ in paris_arrs_sorted]:
        best_sk = None
        best_count = 0
        for sk, arr_counts in arr_by_sk.items():
            c = arr_counts.get(arr, 0)
            if c > best_count:
                best_count = c
                best_sk = sk
        if best_sk is not None:
            arr_winner[arr] = best_sk

    if arr_winner and sorted_sk:
        global_top_sk = sorted_sk[0][0]
        n_dominated_by_top = sum(1 for sk in arr_winner.values() if sk == global_top_sk)
        if n_dominated_by_top >= 2:
            top_g, top_e = sk_to_pair[global_top_sk]
            top_label = species_label(global_top_sk, top_g, top_e)
            push(
                "dataset.arr_dominance",
                f"{_le_or_l(top_label)}{_lower_label(top_label)} est l'espèce "
                f"dominante dans {n_dominated_by_top} arrondissements parisiens "
                f"sur {len(arr_winner)}. Hégémonie locale."
            )

        # Cas atypiques : un arrondissement où une autre espèce gagne.
        atypical = [(arr, sk) for arr, sk in arr_winner.items() if sk != global_top_sk]
        if atypical:
            arr, sk = atypical[0]
            g, e = sk_to_pair[sk]
            label = species_label(sk, g, e)
            ll = _lower_label(label)
            article = "l'" if _le_or_l(ll) == "L'" else "le "
            push(
                "dataset.arr_atypical",
                f"Dans le {arr}, l'espèce dominante n'est pas la 1re de Paris : "
                f"c'est {article}{ll} qui mène. Atypique."
            )

    # 11. Genres — diversité de genres représentés.
    genre_count: dict[str, int] = defaultdict(int)
    genre_species: dict[str, set] = defaultdict(set)
    for (g, e), sk in species_index.items():
        c = count_by_sk.get(sk, 0)
        genre_count[g] += c
        genre_species[g].add(e)

    genres_sorted = sorted(genre_count.items(), key=lambda kv: -kv[1])
    if len(genres_sorted) >= 3:
        top3_genres = genres_sorted[:3]
        names = ", ".join(g for g, _ in top3_genres)
        push(
            "dataset.top3_genres",
            f"Les 3 genres les plus présents à Paris : {names}. "
            f"Ils s'occupent de la majorité du paysage."
        )

    most_diverse_genre = max(genre_species.items(), key=lambda kv: len(kv[1]))
    if len(most_diverse_genre[1]) >= 5:
        push(
            "dataset.diverse_genus",
            f"Le genre {most_diverse_genre[0]} est le plus diversifié à Paris : "
            f"{len(most_diverse_genre[1])} espèces différentes recensées."
        )

    push(
        "dataset.total_genres",
        f"Paris recense {len(genre_count)} genres botaniques. "
        f"Linné aurait fait des fiches."
    )

    # 12. Espèces 4e à 8e (au-delà du podium). On disambigue par le binôme
    # scientifique quand le nom commun est ambigu (ex. deux érables différents).
    seen_labels: set[str] = set()
    for rank, idx in enumerate([3, 4, 5, 6, 7], start=4):
        if idx >= len(sorted_sk):
            break
        sk, c = sorted_sk[idx]
        g, e = sk_to_pair[sk]
        nom = species_label(sk, g, e)
        # Si le nom commun est déjà utilisé pour un autre rang, ajoute le
        # binôme scientifique entre parenthèses pour différencier.
        if nom.lower() in seen_labels:
            display = f"{_lower_label(nom)} ({g} {e})"
        else:
            display = _lower_label(nom)
        seen_labels.add(nom.lower())
        push(
            f"dataset.rank_{rank}",
            f"Rang {rank} des arbres parisiens : {display} "
            f"({_format_int_fr(c)} individus). Solide mais discret."
        )

    # 13. Curiosités et comparaisons.
    if sorted_sk:
        top_sk, top_count = sorted_sk[0]
        n_rares = sum(1 for _, c in sorted_sk if c < 100)
        rares_total = sum(c for _, c in sorted_sk if c < 100)
        if rares_total and top_count > rares_total and n_rares:
            top_g, top_e = sk_to_pair[top_sk]
            top_label = species_label(top_sk, top_g, top_e)
            ll = _lower_label(top_label)
            push(
                "dataset.top_vs_rares",
                f"{_le_or_l(ll)}{ll} compte plus d'arbres à lui seul que les "
                f"{n_rares} espèces rares (< 100 ind.) réunies."
            )

    if len(species_index) >= 100:
        avg_per_species = total_arbres // len(species_index)
        push(
            "dataset.uniform_avg",
            f"Si la diversité était parfaitement répartie, chaque espèce aurait "
            f"~{_format_int_fr(avg_per_species)} arbres. Ce n'est pas le cas."
        )

    if total_remarquables and total_arbres:
        ratio = total_arbres // total_remarquables
        push(
            "dataset.remarquable_ratio",
            f"Un arbre sur {_format_int_fr(ratio)} est qualifié de remarquable. "
            f"Une élite très sélective."
        )

    # Espèce avec un seul individu, exemple aléatoire intéressant.
    if singletons:
        push(
            "dataset.singletons_total",
            f"Mises bout à bout, les {singletons} espèces uniques pèsent... "
            f"{singletons} arbres. C'est le principe."
        )

    # Hauteur moyenne des arbres parisiens (en plus de la médiane).
    if all_heights:
        mean_h = sum(all_heights) / len(all_heights)
        push(
            "dataset.mean_height",
            f"La hauteur moyenne d'un arbre parisien tourne autour de "
            f"{mean_h:.1f} m. Étonnamment modeste."
        )

    # Circonférence moyenne.
    if all_circs:
        mean_c = sum(all_circs) / len(all_circs)
        push(
            "dataset.mean_circ",
            f"La circonférence moyenne d'un tronc parisien fait environ "
            f"{mean_c:.0f} cm. Enlace librement."
        )

    # Comparaison platane vs cumul d'autres top espèces.
    if len(sorted_sk) >= 4:
        top_c = sorted_sk[0][1]
        next3_sum = sum(c for _, c in sorted_sk[1:4])
        if top_c > 0 and next3_sum > 0:
            ratio = top_c / next3_sum
            if abs(ratio - 1.0) > 0.15:
                comparator = "plus" if ratio > 1 else "moins"
                push(
                    "dataset.top_vs_next3",
                    f"L'espèce n°1 à Paris compte {comparator} d'arbres que les "
                    f"places 2, 3 et 4 réunies. Domination notable."
                )

    # --- Fusion finale. ------------------------------------------------------
    intro_ids = [t["id"] for t in intro_tips]
    seen_ids: set[str] = set()
    final_tips: list[dict] = []
    for t in intro_tips + other_tips + dataset_tips:
        if t["id"] in seen_ids:
            raise ValueError(f"id en doublon : {t['id']}")
        seen_ids.add(t["id"])
        # On ne sérialise que les champs utiles côté Kotlin. Le `". "` séparant
        # les phrases est rendu en `\n` à la sortie : le splash Compose les
        # affiche sur des lignes distinctes (cf. `MapOverlays.ColdStartSplash`,
        # `Text(..., maxLines = 4)`). Idempotent — `.replace(". ", ".\n")` est
        # no-op sur un texte déjà splitté. À ne pas faire : utiliser des
        # abréviations type « M. Dupont » ou « cf. … » dans les tips, elles
        # seraient splittées au mauvais endroit.
        out = {
            "id": t["id"],
            "category": t["category"],
            "text": t["text"].replace(". ", ".\n"),
        }
        if t.get("requires"):
            out["requires"] = list(t["requires"])
        final_tips.append(out)

    # Validation : tous les ids de l'intro existent bien dans tips.
    final_ids = {t["id"] for t in final_tips}
    for iid in intro_ids:
        if iid not in final_ids:
            raise ValueError(f"intro réfère un id absent de tips : {iid}")

    payload = {
        "version": 1,
        "intro": intro_ids,
        "tips": final_tips,
    }
    OUT_SPLASH_TIPS.parent.mkdir(parents=True, exist_ok=True)
    with OUT_SPLASH_TIPS.open("w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))

    print(
        f"[tip ] {len(final_tips)} tips ({len(intro_tips)} intro + "
        f"{len(other_tips)} static + {len(dataset_tips)} dataset)"
    )
    print(f"        → {OUT_SPLASH_TIPS.name} ({OUT_SPLASH_TIPS.stat().st_size // 1024} Ko)")


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


class SpeciesIndexCorrupt(Exception):
    """Raised when species-index.json exists but is unreadable."""


def load_existing_species_index(path: Path) -> dict[tuple[str, str], int]:
    """Charge l'index existant pour préserver les int speciesIndex entre runs."""
    if not path.exists():
        return {}
    try:
        with path.open("r", encoding="utf-8") as f:
            entries = json.load(f)
        return {(e["g"], e["e"]): e["i"] for e in entries}
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        raise SpeciesIndexCorrupt(f"{path.name} illisible : {exc}") from exc


def load_pre_build_state(
    species_index_path: Path,
    species_info_path: Path,
) -> tuple[frozenset[int], dict[int, bool]]:
    """Snapshot pré-build de l'état persisté : sk déjà connus + présence WP par sk.

    Retour : `(sk_set, wp_present_by_sk)`. Les deux fichiers absents (premier
    build, ou clone fraîchement initialisé) → état vide, sans raise. Doit être
    appelé AVANT `write_species_info()` (qui écrase `species-info.json`) et
    AVANT `compute_vernacular_and_pokedex()` (qui écrase `species-index.json`).

    Sémantique du marqueur WP : `wp_present_by_sk[sk] == True` ssi l'entrée
    `species-info.json` portait la clé `"wp"`. La cascade actuelle omet la clé
    quand la page Wikipedia FR n'est pas résolue ; un futur refactor qui
    écrirait `"wp": null` casserait silencieusement le check #1 — un test
    offline est ajouté pour verrouiller cette sémantique.
    """
    sk_set: frozenset[int]
    if species_index_path.exists():
        try:
            with species_index_path.open("r", encoding="utf-8") as f:
                idx_entries = json.load(f)
            sk_set = frozenset(e["i"] for e in idx_entries)
        except (json.JSONDecodeError, KeyError, TypeError):
            sk_set = frozenset()
    else:
        sk_set = frozenset()

    wp_present_by_sk: dict[int, bool] = {}
    if species_info_path.exists():
        try:
            with species_info_path.open("r", encoding="utf-8") as f:
                info_entries = json.load(f)
            for e in info_entries:
                sk = e.get("i")
                if isinstance(sk, int):
                    wp_present_by_sk[sk] = "wp" in e
        except (json.JSONDecodeError, KeyError, TypeError):
            pass

    return sk_set, wp_present_by_sk


def verify_species_invariants(
    pre_sk_set: frozenset[int],
    pre_wp_present_by_sk: dict[int, bool],
    entries: list[dict],
    count_by_sk: dict[int, int],
    non_specifie_count: int,
    construit_high_count: list[tuple[str, str, int]],
    cache_dir: Path = WIKIDATA_CACHE_DIR,
) -> None:
    """Vérifie 5 invariants post-build (cf. ROADMAP cycle Catalogue ligne 20).

    Raise sur les régressions structurelles, warn (stderr) sur les signaux
    éditoriaux. À appeler APRÈS `compute_vernacular_and_pokedex()`. Note : si
    un raise tombe ici, le `species-index.json` dégradé est déjà persisté sur
    disque ; `git diff` révèle le delta et le prochain run est idempotent.
    Acceptable trade-off pour un sprint de hardening — déplacer l'écriture
    finale après les checks demanderait un refactor invasif.

    Invariants :
    1. **raise** si un `sk` connu pré-build a disparu du nouvel index.
    2. **raise** si une espèce avec count > 100 a perdu sa page WP entre 2
       builds (cache `.wikidata-cache/{sk}.json` actuellement `miss=True` ou
       sans `wp` non vide, alors que `pre_wp_present_by_sk[sk] == True`).
    3. **raise** si > 50 rows OpenData portent `genre == "Non spécifié"` strict
       (signe que le drop dur amorcé sprint 1 régresse — le 2026-05 baseline
       est ~811 mais ils étaient pré-existants ; au-delà du seuil c'est un
       drift dataset à investiguer).
    4. **raise** si les `nv` finaux ne sont pas uniques (déplacé depuis
       `compute_vernacular_and_pokedex` sprint 2 — regroupement cohérent et
       débloque le test des collisions résiduelles).
    5. **warn** liste explicite des espèces tombées sur la branche `construit`
       avec count > 1000 (candidats `VERNACULAR_OVERRIDES` à arbitrer).
    """
    # 1. sk disparu.
    new_sk_set = frozenset(e["i"] for e in entries)
    disappeared = pre_sk_set - new_sk_set
    if disappeared:
        sample = sorted(disappeared)[:10]
        raise AssertionError(
            f"sk disparus entre 2 builds : {sample}{'...' if len(disappeared) > 10 else ''} "
            f"({len(disappeared)} au total). Casse les captures Room existantes — "
            f"restaurer species-index.json depuis git, ou supprimer la DB asset "
            f"explicitement pour repartir de zéro."
        )

    # 2. WP perdue sur espèce > 100 captures.
    sk_to_entry = {e["i"]: e for e in entries}
    wp_loss_high_count: list[tuple[int, str, str, int]] = []
    for sk, was_present in pre_wp_present_by_sk.items():
        if not was_present:
            continue
        sk_count = count_by_sk.get(sk, 0)
        if sk_count <= 100:
            continue
        cache_path = cache_dir / f"{sk}.json"
        wp_now: str | None = None
        if cache_path.exists():
            try:
                with cache_path.open("r", encoding="utf-8") as f:
                    cached = json.load(f)
                if not cached.get("miss"):
                    wp_now = cached.get("wp") or None
            except (json.JSONDecodeError, OSError):
                wp_now = None
        if not wp_now:
            entry = sk_to_entry.get(sk, {})
            wp_loss_high_count.append((
                sk,
                entry.get("g", "?"),
                entry.get("e", "?"),
                sk_count,
            ))
    if wp_loss_high_count:
        sample = wp_loss_high_count[:5]
        raise AssertionError(
            f"page Wikipedia FR perdue pour {len(wp_loss_high_count)} espèce(s) "
            f"avec count > 100 : {sample}. Investiguer le cache "
            f"`.wikidata-cache/` (rename article ? page supprimée ?) ; ne pas "
            f"livrer cet asset, ces espèces ne s'afficheraient plus en fiche."
        )

    # 3. Non spécifié seuil.
    if non_specifie_count > 50:
        raise AssertionError(
            f"{non_specifie_count} rows OpenData avec genre='Non spécifié' "
            f"(seuil = 50). Le drop dur sprint 1 régresse ou OpenData a changé "
            f"de format — investiguer avant de livrer."
        )

    # 4. Unicité nv finale.
    nvs = [e["nv"] for e in entries]
    if len(set(nvs)) != len(nvs):
        from collections import Counter as _C
        dup = [k for k, c in _C(nvs).items() if c > 1]
        raise AssertionError(
            f"nv non-unique après désambiguation : {dup[:5]}"
            f"{'...' if len(dup) > 5 else ''} ({len(dup)} collisions résiduelles). "
            f"Bug dans `disambiguate_vernaculars` ou collision binôme latin."
        )

    # 5. Warn fallback construit > 1000 captures.
    if construit_high_count:
        print(
            f"[warn] {len(construit_high_count)} espèce(s) > 1000 captures "
            f"sans nv Wikidata/Wikipedia (candidats VERNACULAR_OVERRIDES) :",
            file=sys.stderr,
        )
        for genre, espece, c in construit_high_count[:20]:
            print(f"       - {genre} {espece} ({c} captures)", file=sys.stderr)
        if len(construit_high_count) > 20:
            print(f"       ... +{len(construit_high_count) - 20}", file=sys.stderr)


def build(csv_path: Path, db_path: Path, geojson_path: Path) -> None:
    # Garde-fou : regénérer la DB sans species-index réindexerait à zéro et
    # casserait les captures Room déjà stockées chez l'utilisateur (qui
    # réfèrent les espèces par leur int). Si l'index manque ou est illisible
    # alors qu'une DB asset existe, c'est probablement un mauvais clone ou un
    # conflit — mieux vaut crash que silently break.
    db_exists = db_path.exists()
    index_missing = not OUT_SPECIES_INDEX.exists()
    try:
        species_index = load_existing_species_index(OUT_SPECIES_INDEX)
    except SpeciesIndexCorrupt as exc:
        if db_exists:
            print(
                f"[err] {exc}\n"
                f"      {db_path.name} existe — regénérer les indices casserait\n"
                f"      les captures déjà stockées en Room. Restaurer\n"
                f"      {OUT_SPECIES_INDEX.name} depuis git, ou supprimer\n"
                f"      {db_path.name} explicitement pour repartir de zéro.",
                file=sys.stderr,
            )
            sys.exit(1)
        print(f"[warn] {exc} — repart de zéro (pas de DB existante)")
        species_index = {}
    if index_missing and db_exists:
        print(
            f"[err] {OUT_SPECIES_INDEX.name} absent mais {db_path.name} existe.\n"
            f"      Regénérer les indices casserait les captures déjà stockées\n"
            f"      en Room. Restaurer {OUT_SPECIES_INDEX.name} depuis git, ou\n"
            f"      supprimer {db_path.name} explicitement pour repartir de zéro.",
            file=sys.stderr,
        )
        sys.exit(1)

    # Snapshot pré-build : doit être lu AVANT que `write_species_info` et
    # `compute_vernacular_and_pokedex` ne réécrasent les fichiers d'assets.
    # Comparé en fin de pipeline par `verify_species_invariants`.
    pre_sk_set, pre_wp_present_by_sk = load_pre_build_state(
        OUT_SPECIES_INDEX, OUT_SPECIES_INFO,
    )

    if db_path.exists():
        db_path.unlink()
    db_path.parent.mkdir(parents=True, exist_ok=True)
    geojson_path.parent.mkdir(parents=True, exist_ok=True)

    next_index = (max(species_index.values()) + 1) if species_index else 0
    if species_index:
        print(f"[idx ] {len(species_index)} espèces déjà indexées (next={next_index})")

    print(f"[db  ] création {db_path}")
    con = sqlite3.connect(db_path)
    cur = con.cursor()
    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")
    # Room lit `PRAGMA user_version` pour décider du chemin de migration.
    # L'asset ship en v1 (table `arbre` seule) ; MIGRATION_1_2 côté Kotlin
    # ajoute la table `capture` à la 1re ouverture. Sans ce pragma, Room voit
    # v0 sans migration 0→2 et tombe en `fallbackToDestructiveMigration`,
    # qui re-copie l'asset à v0 → boucle, table `capture` jamais créée.
    cur.execute("PRAGMA user_version = 1")
    for stmt in SCHEMA_SQL:
        cur.execute(stmt)

    inserted = 0
    skipped = 0
    skipped_genre_empty = 0
    non_specifie_count = 0
    normalized_empty_espece = 0
    normalized_n_sp = 0
    fixups_applied = 0
    remarquables = 0
    seen_ids: set[int] = set()

    # Agrégats par espèce, pour species-info.json (médianes + top arr).
    heights_by_sk: dict[int, list[int]] = defaultdict(list)
    circs_by_sk: dict[int, list[int]] = defaultdict(list)
    arr_by_sk: dict[int, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    count_by_sk: dict[int, int] = defaultdict(int)
    arr_total: dict[str, int] = defaultdict(int)
    # Pour species-index.json : on retient le nom commun (`libellefrancais`)
    # le plus fréquent par espèce. Le CSV en colle plusieurs variantes ("Tilleul
    # à grandes feuilles" / "Tilleul" / "" / null) sur des arbres de la même
    # espèce, donc on prend la mode statistique. Les écrans Arboretum (liste
    # et Pokédex) affichent ce nom plutôt que le binomial scientifique.
    nom_commun_by_sk: dict[int, dict[str, int]] = defaultdict(lambda: defaultdict(int))

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
                if genre is None:
                    skipped_genre_empty += 1
                    continue
                if genre == "Non spécifié":
                    non_specifie_count += 1
                    continue

                # Espece vide / "n. sp." → forme canonique "sp." (taggée
                # unknownSpecies à l'écriture). Récupère ~4813 rows que
                # to_str_or_none filtrait silencieusement avant + collapse les
                # "n. sp." vers le bucket "sp." du même genre.
                espece_raw = to_str_or_none(row.get("espece", ""))
                if espece_raw is None:
                    espece = "sp."
                    normalized_empty_espece += 1
                elif espece_raw.lower() == "n. sp.":
                    espece = "sp."
                    normalized_n_sp += 1
                else:
                    espece = espece_raw

                # SPECIES_FIXUPS appliqué AVANT lookup species_index : la
                # coquille est rebindée vers la canonique, dont le `sk` est
                # réutilisé (ou attribué neuf si elle n'existait pas encore).
                fixed = apply_species_fixups(genre, espece)
                if fixed != (genre, espece):
                    fixups_applied += 1
                genre, espece = fixed

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

                count_by_sk[sk] += 1
                if hauteur is not None:
                    heights_by_sk[sk].append(hauteur)
                if circonference is not None:
                    circs_by_sk[sk].append(circonference)
                if nom_commun is not None:
                    nom_commun_by_sk[sk][nom_commun] += 1
                arr_norm = normalize_arr(row.get("arrondissement", ""))
                if arr_norm:
                    arr_by_sk[sk][arr_norm] += 1
                    arr_total[arr_norm] += 1

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

    # dataset-stats.json : `totalEspecesIdentifiees` exclut les zombies (count
    # = 0 post-fixup) et les `u: true`. ROADMAP « X / ~800 » du compteur
    # Arboretum principal.
    identified_count = sum(
        1 for (g, e), sk in species_index.items()
        if count_by_sk.get(sk, 0) > 0 and not is_unknown_species(g, e)
    )
    stats = {
        "totalArbres": inserted,
        "totalEspeces": len(species_index),
        "totalEspecesIdentifiees": identified_count,
        "totalRemarquables": remarquables,
    }
    with OUT_DATASET_STATS.open("w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)

    essences_records = fetch_essences()
    essences_pdf = _build_essences_index(essences_records)
    matched = sum(1 for k in essences_pdf if k in species_index)
    print(
        f"[ess ] {len(essences_pdf)} taxons distincts dans le dataset, "
        f"{matched} matchent une espèce du species-index"
    )

    write_species_info(species_index, count_by_sk, heights_by_sk, circs_by_sk,
                       arr_by_sk, arr_total, total_arbres=inserted,
                       essences_pdf=essences_pdf)

    # species-index.json écrit ICI (post write_species_info) : la cascade nv
    # consomme les caches Wikidata fraîchement peuplés (`vernacularNames`,
    # `wp`). Migration incrémentale des caches pré-Sprint-2 incluse.
    entries, vernacular_counters, construit_high_count = compute_vernacular_and_pokedex(
        species_index=species_index,
        nom_commun_by_sk=nom_commun_by_sk,
        count_by_sk=count_by_sk,
    )
    print(
        f"[vrn ] {vernacular_counters['nv_via_overrides']} overrides, "
        f"{vernacular_counters['nv_via_p1843']} via P1843, "
        f"{vernacular_counters['nv_via_frtitle']} via frTitle, "
        f"{vernacular_counters['nv_via_construit']} construits "
        f"({vernacular_counters['nv_disambiguations']} désambiguations) "
        f"→ {vernacular_counters['pokedex_count']} #N Pokédex"
    )

    # Sanity checks : invariants post-build (sk préservés, WP non perdue,
    # `Non spécifié` sous seuil, nv unique). Warns sur les candidats overrides.
    verify_species_invariants(
        pre_sk_set=pre_sk_set,
        pre_wp_present_by_sk=pre_wp_present_by_sk,
        entries=entries,
        count_by_sk=count_by_sk,
        non_specifie_count=non_specifie_count,
        construit_high_count=construit_high_count,
    )

    remarquables_records = fetch_remarquables()
    write_remarquables_info(remarquables_records, ids_in_csv=seen_ids)

    write_splash_tips(
        species_index=species_index,
        count_by_sk=count_by_sk,
        heights_by_sk=heights_by_sk,
        circs_by_sk=circs_by_sk,
        arr_by_sk=arr_by_sk,
        arr_total=arr_total,
        total_arbres=inserted,
        total_remarquables=remarquables,
        nom_commun_by_sk=nom_commun_by_sk,
    )

    db_mb = db_path.stat().st_size // 1_000_000
    gj_mb = geojson_path.stat().st_size // 1_000_000
    print(
        f"[done] {inserted} arbres "
        f"({skipped_genre_empty} genre vide, "
        f"{non_specifie_count} 'Non spécifié' filtrés, "
        f"{normalized_empty_espece} espèces vides → sp., "
        f"{normalized_n_sp} 'n. sp.' → sp., "
        f"{fixups_applied} fixups appliqués, "
        f"{skipped} autres ignorés)"
    )
    print(f"       → {db_path.name} ({db_mb} Mo)")
    print(f"       → {geojson_path.name} ({gj_mb} Mo)")
    print(f"       → {OUT_SPECIES_INDEX.name} ({len(species_index)} espèces)")
    print(f"       → {OUT_DATASET_STATS.name} ({remarquables} remarquables)")
    print(f"       → {OUT_REMARQUABLES_INFO.name}")
    print(f"       → {OUT_SPLASH_TIPS.name}")


def main() -> int:
    download(CSV_URL, RAW_CSV)
    build(RAW_CSV, OUT_DB, OUT_GEOJSON)
    return 0


if __name__ == "__main__":
    sys.exit(main())
