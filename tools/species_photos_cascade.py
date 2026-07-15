#!/usr/bin/env python3
"""Cascade de photos de référence pour les espèces sans photo officielle (S10).

S9 a extrait ~433 WebP officiels (Ville de Paris) des fiches-essences PDF.
Restent des centaines d'espèces *identifiées* (hors entrées genre-level `sp.`)
sans aucune photo. Ce module comble chaque trou par une **cascade réseau** :

    Wikidata P18 (image Commons du taxon)  →  Commons imageinfo/licence
        →  sinon iNaturalist (photo communautaire, matching taxon strict)

avec **filtre de licence strict** (CC0, domaine public, CC-BY toutes versions ;
rejet -SA / -NC / -ND), ré-encodage WebP (cap 800 px, quality 78), et un cache
disque à chaque niveau pour des re-runs instantanés et déterministes (offline).

Séparation stricte **pur / réseau** (testabilité) :
  - Pur : `commons_license_key`, `inat_license_key`, `clean_artist_html`,
    `parse_commons_imageinfo`, `taxon_name_matches`, `pick_inat_photo`,
    `filename_from_p18_value`, `build_fallback_manifest_entry`.
  - Réseau (cache + retry/backoff) : `fetch_p18_batch`, `fetch_commons_meta`,
    `fetch_inat_taxon`, `fetch_image_bytes`.

Import de `build_dataset` / `essence_pdf` en **lazy** (dans les fonctions) :
`build_dataset` importe ce module, l'import top-level serait circulaire.

Interface consommée par `build_dataset.py` : `resolve_fallback_photos(...)`
(orchestrateur, ne lève jamais) et `build_fallback_manifest_entry(...)` (pur).

Principe produit INVARIANT : **ne jamais inventer**. Miss, licence rejetée,
mauvais taxon ou erreur réseau → sk absent du résultat, jamais de crash.
"""
from __future__ import annotations

import hashlib
import html
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE_DIR = Path(__file__).resolve().parent / ".species-photos-cache"
WIKIDATA_CACHE_DIR = Path(__file__).resolve().parent / ".wikidata-cache"

CAP = 800          # cap long-edge px des vignettes de référence
QUALITY = 78       # quality WebP
INAT_THROTTLE_S = 1.0   # ~60 req/min max sur l'API iNat
CACHE_SCHEMA = 1        # version du ledger resolved/{sk}.json

_RETRIES = 4            # 4 essais, backoff 4 / 8 / 16 / 32 s (cf. build_dataset)
_HTTP_TIMEOUT_S = 30
_P18_BATCH = 50
_ARTIST_MAX = 120

_COMMONS_API = "https://commons.wikimedia.org/w/api.php"
_INAT_TAXA = "https://api.inaturalist.org/v1/taxa"

# Genre-level : une photo sur « Quercus sp. » serait une espèce arbitraire.
_GENRE_LEVEL = {"sp.", ""}

_last_inat_call = 0.0   # horodatage du dernier appel réseau iNat réel (throttle)


# ---------------------------------------------------------------------------
# Modèle & manifest (PUR)
# ---------------------------------------------------------------------------

@dataclass
class FallbackPhoto:
    webp: bytes
    lic: str      # "cc0" | "pd" | "cc-by"
    src: str      # "wikimedia-commons" | "inaturalist"
    by: str       # auteur/attribution nettoyé (non vide)
    u: str        # URL de la page source


def build_fallback_manifest_entry(sk: int, photo: FallbackPhoto) -> dict:
    """Entrée manifest d'un trou comblé (PURE). Forme miroir de `_photo_manifest_entries`.

    Toujours `{sk}-0.webp`, role `p` (1 seule photo par trou, pas de détails).
    """
    return {
        "f": f"{sk}-0.webp",
        "r": "p",
        "src": photo.src,
        "lic": photo.lic,
        "by": photo.by,
        "u": photo.u,
    }


# ---------------------------------------------------------------------------
# Filtres de licence (PURS)
# ---------------------------------------------------------------------------

def commons_license_key(value: str | None) -> str | None:
    """Normalise un slug/short-name de licence Commons → clé whitelist ou None.

    Rejet **immédiat** si le texte contient `sa` / `nc` / `nd` (share-alike,
    non-commercial, no-derivatives). Sinon : `cc0` si « cc0 », `pd` si « public
    domain » / « pd », `cc-by` si commence par « cc-by » / « cc by ». Inconnu →
    None. Le slug machine `License` est préféré au `LicenseShortName` par
    l'appelant (`parse_commons_imageinfo`).
    """
    if not value:
        return None
    v = value.strip().lower()
    if not v:
        return None
    if "sa" in v or "nc" in v or "nd" in v:
        return None
    if "cc0" in v:
        return "cc0"
    if "public domain" in v or v == "pd":
        return "pd"
    if v.startswith("cc-by") or v.startswith("cc by"):
        return "cc-by"
    return None


def inat_license_key(code: str | None) -> str | None:
    """Normalise un `license_code` iNat → clé whitelist ou None.

    Strict : seuls `cc0` et `cc-by` (sans suffixe) passent. `cc-by-nc`,
    `cc-by-sa`, `cc-by-nd`, `null`, « all rights reserved » → None.
    """
    if not code:
        return None
    c = code.strip().lower()
    if c == "cc0":
        return "cc0"
    if c == "cc-by":
        return "cc-by"
    return None


# ---------------------------------------------------------------------------
# Nettoyage attribution HTML (PUR)
# ---------------------------------------------------------------------------

_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"\s+")


def clean_artist_html(raw: str | None) -> str | None:
    """Nettoie un champ `Artist` Commons (HTML) → texte plat, ou None si vide.

    Strip des balises, `html.unescape`, compactage des espaces, troncature
    ~120 car. Vide → None (l'appelant substitue « Wikimedia Commons »).
    """
    if not raw:
        return None
    text = _TAG_RE.sub(" ", raw)
    text = html.unescape(text)
    text = _WS_RE.sub(" ", text).strip()
    if not text:
        return None
    if len(text) > _ARTIST_MAX:
        text = text[:_ARTIST_MAX].rstrip() + "…"
    return text


# ---------------------------------------------------------------------------
# Wikidata P18 → nom de fichier Commons (PUR)
# ---------------------------------------------------------------------------

def filename_from_p18_value(value: str) -> str | None:
    """Décode une valeur P18 (URL Special:FilePath/<Nom>) → nom de fichier.

    P18 arrive en `http://commons.wikimedia.org/wiki/Special:FilePath/<Nom>` ;
    on prend le dernier segment, `urllib.parse.unquote`, et on remet les `_`
    en espaces (convention titre Commons).
    """
    if not value:
        return None
    last = value.rstrip("/").rsplit("/", 1)[-1]
    name = urllib.parse.unquote(last).replace("_", " ").strip()
    return name or None


# ---------------------------------------------------------------------------
# Commons imageinfo (PUR)
# ---------------------------------------------------------------------------

def _extmeta_val(extmeta: dict, key: str) -> str | None:
    node = extmeta.get(key)
    if isinstance(node, dict):
        val = node.get("value")
        return val if isinstance(val, str) else None
    return None


def parse_commons_imageinfo(api_json: dict) -> dict | None:
    """Extrait `{license_key, artist, page_url, download_url}` d'une réponse
    MediaWiki `imageinfo` (formatversion=2), ou None (page/imageinfo absent).

    `license_key` : slug machine `License` préféré au `LicenseShortName` pour
    le filtrage (peut rester None → l'appelant rejette). `artist` nettoyé.
    """
    try:
        pages = api_json.get("query", {}).get("pages", [])
    except AttributeError:
        return None
    if not pages:
        return None
    page = pages[0]
    if not isinstance(page, dict):
        return None
    imageinfo = page.get("imageinfo")
    if not imageinfo:
        return None
    ii = imageinfo[0]
    extmeta = ii.get("extmetadata", {}) or {}
    license_key = (
        commons_license_key(_extmeta_val(extmeta, "License"))
        or commons_license_key(_extmeta_val(extmeta, "LicenseShortName"))
    )
    artist = clean_artist_html(_extmeta_val(extmeta, "Artist"))
    return {
        "license_key": license_key,
        "artist": artist,
        "page_url": ii.get("descriptionurl"),
        "download_url": ii.get("thumburl") or ii.get("url"),
    }


# ---------------------------------------------------------------------------
# Matching taxon iNat (PUR)
# ---------------------------------------------------------------------------

def _normalize_binomial(latin: str) -> tuple[str, str] | None:
    """(genre, espece) normalisé : minuscules, `×`→`x`, marqueur hybride `x` retiré."""
    if not latin:
        return None
    s = latin.replace("×", "x").lower()
    tokens = [t for t in _WS_RE.split(s.strip()) if t and t != "x"]
    if len(tokens) < 2:
        return None
    return tokens[0], tokens[1]


def taxon_name_matches(query_latin: str, taxon_name: str) -> bool:
    """True si les deux noms latins concordent sur le genre ET l'espèce.

    Normalisation : minuscules, espaces compactés, `×` ↔ `x`, marqueur hybride
    `x` isolé ignoré (`Platanus × hispanica` ≡ `Platanus x hispanica`).
    """
    a = _normalize_binomial(query_latin)
    b = _normalize_binomial(taxon_name)
    if a is None or b is None:
        return False
    return a == b


def _inat_image_url(photo: dict) -> str | None:
    """URL medium/large d'une photo iNat (préférences décroissantes)."""
    for key in ("medium_url", "large_url"):
        val = photo.get(key)
        if val:
            return val
    url = photo.get("url")
    if url:
        # `url` est la vignette square ; on remonte en medium.
        return url.replace("square", "medium")
    return None


def pick_inat_photo(api_json: dict, latin: str) -> dict | None:
    """Choisit la 1re photo licence-valide du 1er taxon concordant (PURE).

    Parcourt `results[]`, retient le premier dont `name` concorde avec `latin`
    (anti-faux-positif), puis inspecte `default_photo` puis `taxon_photos[].photo`
    et garde la 1re dont `license_code ∈ {cc0, cc-by}`. Retourne
    `{license_key, by, page_url, image_url}` ou None.
    """
    results = api_json.get("results") if isinstance(api_json, dict) else None
    if not results:
        return None
    for r in results:
        if not isinstance(r, dict):
            continue
        if not taxon_name_matches(latin, r.get("name") or ""):
            continue
        candidates: list[dict] = []
        default_photo = r.get("default_photo")
        if isinstance(default_photo, dict):
            candidates.append(default_photo)
        for tp in r.get("taxon_photos") or []:
            if isinstance(tp, dict) and isinstance(tp.get("photo"), dict):
                candidates.append(tp["photo"])
        for photo in candidates:
            lic = inat_license_key(photo.get("license_code"))
            if lic is None:
                continue
            image_url = _inat_image_url(photo)
            if not image_url:
                continue
            by = clean_artist_html(photo.get("attribution")) or "iNaturalist"
            pid = photo.get("id")
            page_url = (
                f"https://www.inaturalist.org/photos/{pid}" if pid else None
            )
            return {
                "license_key": lic,
                "by": by,
                "page_url": page_url,
                "image_url": image_url,
            }
        # Taxon concordant mais aucune photo licence-valide : on s'arrête là
        # (le 1er concordant fait foi ; ne pas piocher un homonyme suivant).
        return None
    return None


# ---------------------------------------------------------------------------
# Cache disque (helpers)
# ---------------------------------------------------------------------------

def _cache_sub(name: str) -> Path:
    p = CACHE_DIR / name
    p.mkdir(parents=True, exist_ok=True)
    return p


def _sha1(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()


def _read_json(path: Path) -> dict | None:
    if not path.exists():
        return None
    try:
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return None


def _write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)


def _slug(latin: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", latin.strip().lower()).strip("-") or "x"


# ---------------------------------------------------------------------------
# HTTP avec retry/backoff (réseau)
# ---------------------------------------------------------------------------

def _user_agent() -> str:
    from build_dataset import WIKI_USER_AGENT  # lazy : évite l'import circulaire
    return WIKI_USER_AGENT


def _http_get(url: str) -> bytes | None:
    """GET brut avec retry/backoff 429/5xx (4 essais, 4/8/16/32 s). None sinon."""
    req = urllib.request.Request(url, headers={"User-Agent": _user_agent()})
    for attempt in range(_RETRIES):
        try:
            with urllib.request.urlopen(req, timeout=_HTTP_TIMEOUT_S) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code == 429 or 500 <= e.code < 600:
                time.sleep(2 ** (attempt + 2))
                continue
            return None
        except (urllib.error.URLError, TimeoutError, OSError):
            time.sleep(2 ** (attempt + 2))
            continue
    return None


def _http_get_json(url: str) -> dict | None:
    raw = _http_get(url)
    if raw is None:
        return None
    try:
        return json.loads(raw)
    except (json.JSONDecodeError, ValueError):
        return None


# ---------------------------------------------------------------------------
# Wikidata P18 (réseau, batché)
# ---------------------------------------------------------------------------

def fetch_p18_batch(qids: list[str]) -> dict[str, str | None]:
    """QID → nom de fichier Commons via SPARQL P18 batché (50/appel).

    Lit d'abord le cache `p18/{qid}.json` (`{"file": name|null}`) ; ne
    ré-interroge que les QID inconnus. Un QID sans P18 est caché `null` (pas de
    re-tentative). Ne lève jamais.
    """
    cache_dir = _cache_sub("p18")
    out: dict[str, str | None] = {}
    todo: list[str] = []
    for qid in qids:
        cached = _read_json(cache_dir / f"{qid}.json")
        if cached is not None and "file" in cached:
            out[qid] = cached["file"]
        else:
            todo.append(qid)

    from build_dataset import _sparql_post  # lazy
    for i in range(0, len(todo), _P18_BATCH):
        batch = todo[i : i + _P18_BATCH]
        values = " ".join(f"wd:{q}" for q in batch)
        query = (
            "SELECT ?item ?image WHERE { "
            f"VALUES ?item {{ {values} }} "
            "?item wdt:P18 ?image . }"
        )
        found: dict[str, str] = {}
        try:
            payload = _sparql_post(query)
            for binding in payload.get("results", {}).get("bindings", []):
                item_uri = binding.get("item", {}).get("value", "")
                qid = item_uri.rsplit("/", 1)[-1] if item_uri else None
                image = binding.get("image", {}).get("value")
                if qid and image and qid not in found:
                    name = filename_from_p18_value(image)
                    if name:
                        found[qid] = name
        except Exception as e:  # pragma: no cover - réseau/SPARQL
            print(f"[cas ] P18 batch {i}-{i + len(batch)} a échoué : {e}")
            # On ne cache pas ce batch : re-tentable au prochain run.
            for qid in batch:
                out.setdefault(qid, None)
            continue
        for qid in batch:
            name = found.get(qid)
            out[qid] = name
            _write_json(cache_dir / f"{qid}.json", {"file": name})
    return out


# ---------------------------------------------------------------------------
# Commons imageinfo (réseau)
# ---------------------------------------------------------------------------

def fetch_commons_meta(filename: str) -> dict | None:
    """Métadonnées + thumburl d'un fichier Commons (un seul appel MediaWiki).

    Cache `commons/{sha1(filename)}.json` (dict parsé ou `{"miss": true}`).
    Retourne `{license_key, artist, page_url, download_url}` ou None.
    """
    cache_dir = _cache_sub("commons")
    cache_path = cache_dir / f"{_sha1(filename)}.json"
    cached = _read_json(cache_path)
    if cached is not None:
        return None if cached.get("miss") else cached

    params = {
        "action": "query",
        "format": "json",
        "formatversion": "2",
        "titles": f"File:{filename}",
        "prop": "imageinfo",
        "iiprop": "extmetadata|url|mime",
        "iiurlwidth": str(CAP),
        "iiextmetadatafilter": "LicenseShortName|License|Artist|LicenseUrl|AttributionRequired",
    }
    url = f"{_COMMONS_API}?{urllib.parse.urlencode(params)}"
    api_json = _http_get_json(url)
    meta = parse_commons_imageinfo(api_json) if api_json else None
    if meta is None:
        _write_json(cache_path, {"miss": True})
        return None
    _write_json(cache_path, meta)
    return meta


# ---------------------------------------------------------------------------
# iNaturalist (réseau, throttlé)
# ---------------------------------------------------------------------------

def fetch_inat_taxon(latin: str) -> dict | None:
    """Réponse `/v1/taxa` pour un nom latin (throttle 1 s entre appels réels).

    Cache `inat/{slug}.json` (JSON API brut, ou `{"miss": true}`). Retourne le
    JSON API (à passer à `pick_inat_photo`) ou None.
    """
    global _last_inat_call
    cache_dir = _cache_sub("inat")
    cache_path = cache_dir / f"{_slug(latin)}.json"
    cached = _read_json(cache_path)
    if cached is not None:
        return None if cached.get("miss") else cached

    # Throttle uniquement sur les appels réseau réels (pas les cache hits).
    elapsed = time.monotonic() - _last_inat_call
    if elapsed < INAT_THROTTLE_S:
        time.sleep(INAT_THROTTLE_S - elapsed)
    params = {
        "q": latin,
        "rank": "species,hybrid",
        "per_page": "5",
        "locale": "fr",
    }
    url = f"{_INAT_TAXA}?{urllib.parse.urlencode(params)}"
    api_json = _http_get_json(url)
    _last_inat_call = time.monotonic()
    if api_json is None:
        _write_json(cache_path, {"miss": True})
        return None
    _write_json(cache_path, api_json)
    return api_json


def fetch_inat_detail(taxon_id: int) -> dict | None:
    """Fiche taxon `/v1/taxa/{id}` (seule à exposer `taxon_photos`).

    La recherche `/v1/taxa?q=` ne renvoie que `default_photo` (souvent « all
    rights reserved ») ; les photos CC0/CC-BY d'un taxon ne sont accessibles que
    via cette fiche détail. Cache `inat/detail-{id}.json` (JSON API ou miss),
    throttle 1 s sur appel réel. Retourne le JSON API (à passer à
    `pick_inat_photo`) ou None.
    """
    global _last_inat_call
    cache_dir = _cache_sub("inat")
    cache_path = cache_dir / f"detail-{taxon_id}.json"
    cached = _read_json(cache_path)
    if cached is not None:
        return None if cached.get("miss") else cached

    elapsed = time.monotonic() - _last_inat_call
    if elapsed < INAT_THROTTLE_S:
        time.sleep(INAT_THROTTLE_S - elapsed)
    url = f"{_INAT_TAXA}/{taxon_id}"
    api_json = _http_get_json(url)
    _last_inat_call = time.monotonic()
    if api_json is None:
        _write_json(cache_path, {"miss": True})
        return None
    _write_json(cache_path, api_json)
    return api_json


# ---------------------------------------------------------------------------
# Téléchargement image (réseau)
# ---------------------------------------------------------------------------

def fetch_image_bytes(url: str) -> bytes | None:
    """Octets bruts d'une image, cachés `images/{sha1(url)}.bin`. None si échec."""
    cache_dir = _cache_sub("images")
    cache_path = cache_dir / f"{_sha1(url)}.bin"
    if cache_path.exists():
        try:
            return cache_path.read_bytes()
        except OSError:  # pragma: no cover
            pass
    raw = _http_get(url)
    if not raw:
        return None
    try:
        cache_path.write_bytes(raw)
    except OSError:  # pragma: no cover
        pass
    return raw


# ---------------------------------------------------------------------------
# Orchestrateur
# ---------------------------------------------------------------------------

def _read_qid(sk: int) -> str | None:
    data = _read_json(WIKIDATA_CACHE_DIR / f"{sk}.json")
    if not data:
        return None
    qid = data.get("qid")
    return qid if isinstance(qid, str) and qid.startswith("Q") else None


def _encode(raw: bytes) -> bytes | None:
    from essence_pdf import encode_raw_to_webp  # lazy
    return encode_raw_to_webp(raw, CAP, QUALITY)


def _reconstruct_from_ledger(ledger: dict) -> FallbackPhoto | None:
    """Reconstruit un FallbackPhoto depuis un ledger + l'image brute cachée.

    Ré-encode via `encode_raw_to_webp` (déterministe). None si `chosen == none`,
    image absente, ou ré-encodage raté.
    """
    if ledger.get("chosen") in (None, "none"):
        return None
    img_sha = ledger.get("img")
    if not img_sha:
        return None
    img_path = CACHE_DIR / "images" / f"{img_sha}.bin"
    if not img_path.exists():
        return None
    try:
        raw = img_path.read_bytes()
    except OSError:
        return None
    webp = _encode(raw)
    if not webp:
        return None
    src = "wikimedia-commons" if ledger.get("chosen") == "p18" else "inaturalist"
    return FallbackPhoto(
        webp=webp,
        lic=ledger.get("lic", ""),
        src=ledger.get("src", src),
        by=ledger.get("by") or "",
        u=ledger.get("u") or "",
    )


def _write_ledger(sk: int, *, chosen: str, lic: str | None = None,
                  src: str | None = None, by: str | None = None,
                  u: str | None = None, img: str | None = None) -> None:
    _write_json(_cache_sub("resolved") / f"{sk}.json", {
        "sk": sk,
        "chosen": chosen,
        "lic": lic,
        "src": src,
        "by": by,
        "u": u,
        "img": img,
        "v": CACHE_SCHEMA,
    })


def _try_p18(sk: int, qid: str, p18: dict[str, str | None]) -> FallbackPhoto | None:
    filename = p18.get(qid)
    if not filename:
        return None
    meta = fetch_commons_meta(filename)
    if meta is None:
        return None
    lic = meta.get("license_key")
    if lic is None:
        return None
    download_url = meta.get("download_url")
    if not download_url:
        return None
    raw = fetch_image_bytes(download_url)
    if not raw:
        return None
    webp = _encode(raw)
    if not webp:
        return None
    by = meta.get("artist") or "Wikimedia Commons"
    u = meta.get("page_url") or download_url
    _write_ledger(sk, chosen="p18", lic=lic, src="wikimedia-commons",
                  by=by, u=u, img=_sha1(download_url))
    return FallbackPhoto(webp=webp, lic=lic, src="wikimedia-commons", by=by, u=u)


def _find_inat_taxon_id(api_json: dict, latin: str) -> int | None:
    """Id du 1er résultat de recherche concordant (anti-faux-positif)."""
    for r in api_json.get("results") or []:
        if isinstance(r, dict) and taxon_name_matches(latin, r.get("name") or ""):
            tid = r.get("id")
            return tid if isinstance(tid, int) else None
    return None


def _try_inat(sk: int, latin: str) -> FallbackPhoto | None:
    search = fetch_inat_taxon(latin)
    if search is None:
        return None
    taxon_id = _find_inat_taxon_id(search, latin)
    if taxon_id is None:
        return None
    # La recherche n'expose pas `taxon_photos` : passer par la fiche détail pour
    # accéder aux photos CC0/CC-BY (la `default_photo` est souvent réservée).
    detail = fetch_inat_detail(taxon_id)
    if detail is None:
        return None
    pick = pick_inat_photo(detail, latin)
    if pick is None:
        return None
    image_url = pick["image_url"]
    raw = fetch_image_bytes(image_url)
    if not raw:
        return None
    webp = _encode(raw)
    if not webp:
        return None
    lic = pick["license_key"]
    by = pick["by"] or "iNaturalist"
    u = pick.get("page_url") or image_url
    _write_ledger(sk, chosen="inat", lic=lic, src="inaturalist",
                  by=by, u=u, img=_sha1(image_url))
    return FallbackPhoto(webp=webp, lic=lic, src="inaturalist", by=by, u=u)


def resolve_fallback_photos(
    species_index: dict[tuple[str, str], int],
    covered_sks: set[int],
    *, offline: bool = False,
) -> dict[int, FallbackPhoto]:
    """Résout 1 photo de référence par trou via la cascade P18 → iNat.

    Trous : `sk ∉ covered_sks`, espèce identifiée (hors `sp.`), par sk croissant.
    Pour chaque : ledger `resolved/{sk}.json` (v==CACHE_SCHEMA) reconstruit sans
    réseau ; sinon (et si `not offline`) cascade réseau écrivant le ledger.
    Ne lève JAMAIS : un échec/miss/licence-rejetée = sk absent du dict.
    """
    trous: list[tuple[int, str, str]] = []
    for (genre, espece), sk in species_index.items():
        if espece in _GENRE_LEVEL:
            continue
        if sk in covered_sks:
            continue
        trous.append((sk, genre, espece))
    trous.sort(key=lambda t: t[0])

    # Pré-fetch P18 batché pour les QID des trous non encore résolus (online).
    p18: dict[str, str | None] = {}
    if not offline:
        pending_qids: list[str] = []
        for sk, _genre, _espece in trous:
            ledger = _read_json(_cache_sub("resolved") / f"{sk}.json")
            if ledger is not None and ledger.get("v") == CACHE_SCHEMA:
                continue
            qid = _read_qid(sk)
            if qid:
                pending_qids.append(qid)
        if pending_qids:
            p18 = fetch_p18_batch(pending_qids)

    result: dict[int, FallbackPhoto] = {}
    n_p18 = n_inat = n_reused = 0
    for sk, genre, espece in trous:
        latin = f"{genre} {espece}"
        ledger = _read_json(_cache_sub("resolved") / f"{sk}.json")
        if ledger is not None and ledger.get("v") == CACHE_SCHEMA:
            photo = _reconstruct_from_ledger(ledger)
            if photo is not None:
                result[sk] = photo
                n_reused += 1
            continue
        if offline:
            continue  # pas de ledger, offline → miss

        try:
            photo = None
            qid = _read_qid(sk)
            if qid:
                photo = _try_p18(sk, qid, p18)
            if photo is not None:
                result[sk] = photo
                n_p18 += 1
                continue
            photo = _try_inat(sk, latin)
            if photo is not None:
                result[sk] = photo
                n_inat += 1
                continue
            _write_ledger(sk, chosen="none")
        except Exception as e:  # pragma: no cover - robustesse totale
            print(f"[cas ] sk {sk} ({latin}) : erreur ignorée {e}")
            continue

    print(
        f"[cas ] {len(result)}/{len(trous)} trous comblés "
        f"({n_p18} P18, {n_inat} iNat, {n_reused} cache)"
    )
    return result
