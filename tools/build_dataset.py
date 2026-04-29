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

WIKI_USER_AGENT = "arbre-app-build/0.1 (personal Android app, https://github.com/local/arbre-app)"
WIKIDATA_CACHE_DIR = ROOT / "tools" / ".wikidata-cache"
WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"
WIKIDATA_BATCH_SIZE = 50  # Wikidata accepte largement plus, 50 garde la requête lisible
WIKIDATA_TIMEOUT_S = 60   # SPARQL peut être lent sur des batches de 50
WIKI_REST_THROTTLE_S = 0.3  # ~3 req/s sur restbase une fois qu'on a les vrais titres
WIKI_REST_TIMEOUT_S = 10
WIKI_REST_RETRIES_429 = 4  # backoff 4, 8, 16, 32 s

# Format CSV : « PARIS 5E ARRDT », « PARIS 16E ARRDT », ...
ARR_PARIS_PATTERN = re.compile(r"^PARIS\s+(\d+)E\s+ARRDT$", re.IGNORECASE)


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

    On résout par P225 (taxon name) et on récupère le titre Wikipedia FR
    s'il existe, sinon EN en fallback (juste pour distinguer « pas trouvé du
    tout » de « trouvé mais pas de page FR »).
    """
    # Le tag `@en` n'est pas requis par P225 (les taxon names sont mono-string)
    # — on passe des littéraux non typés. Échapper backslash et guillemet pour
    # rester valide même si un nom contient des caractères pénibles.
    encoded = " ".join(
        '"' + v.replace('\\', '\\\\').replace('"', '\\"') + '"'
        for v in values
    )
    return f"""
SELECT ?taxon ?taxonName ?frTitle ?enTitle WHERE {{
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
    """Résout (genre, espece) -> {qid, frTitle?, enTitle?} via SPARQL batched.

    `pairs` : liste `(sk, genre, espece)`. La résolution est par binomial seul
    (sk n'est utilisé que pour les logs). Si une variante du candidat liste
    matche dans Wikidata (ex: `Platanus × hispanica` matchera là où
    `Platanus x hispanica` rate), on garde le 1er hit.

    Retour : dict indexé par `(genre, espece)` original. Espèces non résolues
    absentes du dict.
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
            if origin in resolved:
                # Plusieurs taxons partagent ce nom canonique : on garde le 1er.
                continue
            qid_uri = binding.get("taxon", {}).get("value", "")
            qid = qid_uri.rsplit("/", 1)[-1] if qid_uri else None
            resolved[origin] = {
                "qid": qid,
                "frTitle": binding.get("frTitle", {}).get("value"),
                "enTitle": binding.get("enTitle", {}).get("value"),
            }
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
    2. Sinon utilise la résolution Wikidata pré-calculée pour obtenir le titre FR.
    3. Si titre FR connu → fetch summary REST → cache hit.
    4. Si pas de titre FR mais QID → cache `{qid, noFr: true}` (le lien
       Wikipedia FR sera absent côté UI mais on garde le QID pour info).
    5. Si rien → cache `{miss: true}`.
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
    if not fr_title:
        # QID connu, mais pas de page Wikipedia FR : on cache sans summary.
        result = {"qid": qid} if qid else {"miss": True}
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        with cache_path.open("w", encoding="utf-8") as f:
            json.dump(result, f)
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
    else:
        summary = (data.get("extract") or "").strip()
        if not summary or data.get("type") == "disambiguation":
            result = {"qid": qid} if qid else {"miss": True}
        else:
            result = {
                "wp": data.get("titles", {}).get("canonical")
                      or data.get("title")
                      or fr_title.replace(" ", "_"),
                "qid": qid,
                "summary": summary,
            }
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    with cache_path.open("w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False)
    return result if "summary" in result or "qid" in result else None


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

    # species-index.json : trié par index pour un diff lisible. Pour chaque
    # entrée on injecte `nc` (nom commun) = la mode statistique des
    # `libellefrancais` rencontrés ; clé absente si aucun arbre de l'espèce
    # n'a de nom commun renseigné. Côté Kotlin, `SpeciesIndex` lit le champ
    # via `optStringOrNull` — tolérant à l'absence.
    def best_nom_commun(sk: int) -> str | None:
        counts = nom_commun_by_sk.get(sk)
        if not counts:
            return None
        return max(counts.items(), key=lambda kv: kv[1])[0]

    species_entries = []
    for (g, e), i in species_index.items():
        entry: dict[str, object] = {"i": i, "g": g, "e": e}
        nc = best_nom_commun(i)
        if nc:
            entry["nc"] = nc
        species_entries.append(entry)
    species_entries.sort(key=lambda e: e["i"])
    with OUT_SPECIES_INDEX.open("w", encoding="utf-8") as f:
        json.dump(species_entries, f, ensure_ascii=False, separators=(",", ":"))

    stats = {
        "totalArbres": inserted,
        "totalEspeces": len(species_index),
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

    remarquables_records = fetch_remarquables()
    write_remarquables_info(remarquables_records, ids_in_csv=seen_ids)

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
    print(f"       → {OUT_REMARQUABLES_INFO.name}")


def main() -> int:
    download(CSV_URL, RAW_CSV)
    build(RAW_CSV, OUT_DB, OUT_GEOJSON)
    return 0


if __name__ == "__main__":
    sys.exit(main())
