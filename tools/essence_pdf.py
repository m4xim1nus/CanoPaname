#!/usr/bin/env python3
"""Extraction des PDF fiches-essences (Guide des Essences de Paris 2024).

Pipeline build-time (jamais au runtime app) : depuis les 2 pages A4 de chaque
fiche PDF Ville de Paris, on extrait deux familles d'attributs qui n'existent
PAS dans le record OpenData structuré consommé par `build_dataset.py` :

  1. **Calendriers Floraison / Fructification** (page 0) → deux bitfields de
     12 bits. Contrat miroir avec `SpeciesInfo.kt` côté app :
     **bit 0 = janvier … bit 11 = décembre**. Un mois est actif si le centre
     de sa lettre tombe dans une case peinte (fill coloré).
  2. **« À RETENIR »** (page 1) → deux listes de puces `atouts` / `limites`.

Stratégie d'ancrage (figée, validée botaniquement sur 5 fiches témoins) :
  - Calendriers : on ancre sur les *rangées de 12 lettres* J F M A M J J A S O N D
    (les mots « Floraison » / « Fructification » apparaissent aussi dans le
    texte courant), puis on valide chaque rangée par le label le plus proche
    au-dessus. Cases actives = rects de fill non blancs de `get_drawings()`,
    pouvant couvrir plusieurs mois contigus.
  - Ancien template (6 fiches du corpus 2024 : Tilia ×4, Fagus, Betula nigra) :
    les labels sont rasterisés en images. Fallback à double signal : rangée
    haute = floraison, basse = fructification, accepté SEULEMENT si les
    couleurs des cases confirment (rose = floraison, orange = fructification,
    constantes sur tout le corpus). Le texte des puces « À RETENIR » y est
    lui aussi rasterisé → atouts/limites restent inextractibles (pas d'OCR).
  - À RETENIR : split gauche/droite sur les clusters x des puces `•` (pas sur
    les x des headings — les puces débordent à gauche des headings).

Principe produit INVARIANT : **ne jamais inventer**. Toute extraction douteuse
→ None / liste vide + warning explicite, jamais de valeur partielle ou devinée.
Bitfield 0 (aucune case colorée) = échec → None + warning.

pymupdf (`fitz`) est une dépendance build-time uniquement (cf.
`tools/requirements.txt`). Les helpers purs de ce module fonctionnent sans lui.

Debug d'une fiche :
    python3 tools/essence_pdf.py --dump <pdf_id|chemin>
"""
from __future__ import annotations

import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

try:
    import fitz  # PyMuPDF
except ImportError:  # pragma: no cover - dépend de l'install
    fitz = None

# ROOT = parent du dossier tools/ (racine du repo).
ROOT = Path(__file__).resolve().parent.parent
PDF_CACHE_DIR = ROOT / "tools" / ".essences-pdf-cache"

MONTH_SEQ = list("JFMAMJJASOND")
WHITE_EPS = 0.05  # écart d'un canal à 1.0 au-delà duquel un fill est « coloré »

# Couleurs des cases actives, constantes sur tout le corpus (anciens et récents
# templates) — servent de signal de confirmation au fallback sans labels.
_FLOR_FILL = (1.0, 0.8, 0.8)      # rose
_FRUCT_FILL = (0.957, 0.694, 0.514)  # orange
_FILL_EPS = 0.02

# Bornes de validation des puces « À RETENIR » (par section, indépendantes).
_BULLETS_MIN = 1
_BULLETS_MAX = 8  # max réel observé sur le corpus = 7 (Fraxinus excelsior)
_BULLET_CHARS_MIN = 3
_BULLET_CHARS_MAX = 300

_USER_AGENT = (
    "canopaname-build/0.1 (personal Android app, "
    "https://github.com/m4xim1nus/CanoPaname)"
)


@dataclass
class EssenceExtras:
    """Attributs extraits d'une fiche PDF. Champs None/vides = extraction ratée."""

    flor: int | None            # bitfield 12 bits, bit 0 = janvier
    fruct: int | None           # bitfield 12 bits, bit 0 = janvier
    atouts: list[str] = field(default_factory=list)
    limites: list[str] = field(default_factory=list)
    # --- S6 : champs textuels du template récent (colonnes gauche/droite) ---
    fam: str | None = None      # famille botanique (« Magnoliacées »)
    haut: str | None = None     # hauteur chiffrée (« 10 m »)
    env: str | None = None      # envergure du houppier (« 8 m »)
    croiss: str | None = None   # vitesse de croissance (« Moyenne »)
    long: str | None = None     # longévité (« Moyenne (100 à 200 ans) »)
    iddesc: dict[str, str] = field(default_factory=dict)  # ecorce/feuillage/floraison/fructification
    paris: str | None = None    # encart éditorial « L'essence à Paris »
    svc: dict[str, str] = field(default_factory=dict)     # climat/eau/biodiv
    warnings: list[str] = field(default_factory=list)


# ---------------------------------------------------------------------------
# S6 — Constantes de parsing des champs textuels (ancrage 100 % géométrique)
# ---------------------------------------------------------------------------

# Ancres des 5 headings de niveau titre (taille > 11 pt sur le template récent),
# repérées par `_norm` exact. Absence de « descriptif de l'essence » = template
# ancien rasterisé → aucun champ textuel extractible (cf. les 6 fiches Tilia/
# Fagus/Betula nigra), skip complet + warning.
_HEADING_ANCHORS = (
    "l'essence a paris",
    "descriptif de l'essence",
    "paysage et cadre de vie",
    "sites de plantation recommandes",
    "services ecosystemiques rendus",
)
_HEADING_MIN_SIZE = 11.0  # les titres sont à 12-13 pt, le corps à 9-9.7 pt.

# Labels du bloc Descriptif (colonne gauche). Clé = nom de champ interne ;
# valeur = texte du label (déjà `_norm`é, sans diacritiques). `origine`,
# `indigenat`, `statut`, `port` sont matchés comme labels (pour BORNER
# correctement les valeurs voisines) puis JETÉS (doublonnent l'API).
_DESC_LABEL_TEXTS: dict[str, str] = {
    "famille": "famille",
    "origine": "origine",
    "indigenat": "indigenat",
    "statut": "statut",
    "hauteur": "hauteur",
    "envergure": "envergure du houppier",
    "port": "port",
    "croissance": "vitesse de croissance",
    "longevite": "longevite",
    "feuillage": "feuillage",
    "ecorce": "ecorce",
    "floraison": "floraison",
    "fructification": "fructification",
}
# Index inverse nospace (label sans espaces → champ), pour la variante « glyphe
# scindé » (`É`/`corce` en 2 spans → « e corce » → nospace « ecorce »).
_DESC_FIELD_BY_NOSPACE: dict[str, str] = {
    text.replace(" ", ""): fieldname for fieldname, text in _DESC_LABEL_TEXTS.items()
}
# Pour la variante « Famille » sans deux-points : matching par préfixe avec
# espaces, labels les plus longs d'abord (évite que « port » masque un préfixe).
_DESC_TEXTS_BY_LEN: list[tuple[str, str]] = sorted(
    ((text, fieldname) for fieldname, text in _DESC_LABEL_TEXTS.items()),
    key=lambda it: len(it[0]), reverse=True,
)

# Labels des 3 services écosystémiques (colonne droite basse). Le préfixe
# biodiversité tolère la variante sans « la » (Cedrus deodara).
_SVC_LABELS: tuple[tuple[str, str], ...] = (
    ("regulation du climat local", "climat"),
    ("regulation quantitative de la ressource en eau", "eau"),
    ("interet pour la biodiversite", "biodiv"),
    ("interet pour biodiversite", "biodiv"),
)

# Bornes de validation par champ (invariant « ne jamais inventer » : hors bornes
# → champ omis + warning, jamais de valeur partielle). Cf. annexe S6.
_DESC_BOUNDS: dict[str, tuple[int, int]] = {
    "fam": (3, 30),
    "haut": (2, 12),
    "env": (2, 12),
    "croiss": (3, 20),
    "long": (3, 40),
    "ecorce": (3, 120),
    "feuillage": (3, 160),
    "floraison": (3, 120),
    "fructification": (3, 120),
}
_PARIS_BOUNDS = (20, 800)
_SVC_BOUNDS = (30, 700)


def _require_fitz() -> None:
    """Lève une SystemExit claire si pymupdf n'est pas installé."""
    if fitz is None:
        raise SystemExit(
            "pymupdf (fitz) requis pour l'extraction des fiches-essences.\n"
            "Installe les dépendances build : pip install -r tools/requirements.txt"
        )


def _norm(s: str) -> str:
    """NFD + retrait des diacritiques + lower — pour matcher les ancres texte.

    Normalise aussi les apostrophes typographiques (`’` U+2019, `ʼ` U+02BC) en
    apostrophe ASCII : les headings du template récent (« l'essence à Paris »,
    « descriptif de l'essence ») en contiennent une courbe. `_norm` reste 1:1
    en longueur sur le latin précomposé (offset de tranche fiable côté S6).
    """
    s = s.replace("’", "'").replace("ʼ", "'")
    return "".join(
        c for c in unicodedata.normalize("NFD", s.lower())
        if unicodedata.category(c) != "Mn"
    ).strip()


# ---------------------------------------------------------------------------
# Helpers purs (testables sans pymupdf)
# ---------------------------------------------------------------------------

def _bits_from_flags(flags: list[bool]) -> int:
    """Liste de booléens mensuels → bitfield. flags[0]=janvier (bit 0) … flags[11]=décembre."""
    bits = 0
    for i, on in enumerate(flags):
        if on:
            bits |= 1 << i
    return bits


def _group_lines(items: list[tuple[float, float, str]]) -> list[list[tuple[float, str]]]:
    """Regroupe des spans (x0, y0, texte) en lignes.

    Tolérance y < 3 pt (la puce `•` et son texte ont des y0 qui diffèrent de
    ~0.6 pt). Chaque ligne = liste de (x0, texte) triée par x.
    """
    lines: list[list] = []
    for x0, y0, t in sorted(items, key=lambda it: it[1]):
        if lines and abs(lines[-1][0] - y0) < 3:
            lines[-1][1].append((x0, t))
        else:
            lines.append([y0, [(x0, t)]])
    return [sorted(parts) for _, parts in lines]


def _bullets_from_lines(lines: list[list[tuple[float, str]]]) -> list[str]:
    """Lignes (triées par y) → puces. Ligne commençant par `•` = nouvelle puce,
    sinon continuation rattachée à la puce précédente."""
    bullets: list[str] = []
    for parts in lines:
        text = " ".join(t for _, t in parts).strip()
        if parts and parts[0][1].startswith("•"):
            bullets.append(text.lstrip("• ").strip())
        elif bullets:
            bullets[-1] += " " + text
    return bullets


def _extract_bullets(items: list[tuple[float, float, str]]) -> list[str]:
    """Pipeline pur : spans (x0, y0, texte) d'une colonne → liste de puces."""
    return _bullets_from_lines(_group_lines(items))


def _validate_bullets(bullets: list[str]) -> tuple[list[str], str | None]:
    """Valide une section : 1-6 puces, chacune 3-300 caractères.

    Hors bornes → ([], warning) pour CETTE section (les deux sections sont
    indépendantes). Ne jamais renvoyer de valeur partielle.
    """
    if not (_BULLETS_MIN <= len(bullets) <= _BULLETS_MAX):
        return [], f"{len(bullets)} puces (hors bornes {_BULLETS_MIN}-{_BULLETS_MAX})"
    for b in bullets:
        if not (_BULLET_CHARS_MIN <= len(b) <= _BULLET_CHARS_MAX):
            return [], (
                f"puce de {len(b)} caractères "
                f"(hors bornes {_BULLET_CHARS_MIN}-{_BULLET_CHARS_MAX})"
            )
    return bullets, None


def _split_x_from_bullets(
    bullet_xs: list[float], atouts_x0: float, limites_x0: float
) -> tuple[float | None, str | None]:
    """Détermine l'abscisse de split gauche/droite à partir des clusters x des puces.

    gap > 50 pt entre deux x de puces = nouveau cluster. 2 clusters → split =
    x0 du cluster droit − 5. 1 cluster → côté déterminé vs le milieu des deux
    headings. Autre → échec.
    """
    if not bullet_xs:
        return None, "aucune puce sous les ancres"
    xs = sorted(bullet_xs)
    clusters = [[xs[0]]]
    for x in xs[1:]:
        if x - clusters[-1][-1] > 50:
            clusters.append([x])
        else:
            clusters[-1].append(x)
    if len(clusters) == 2:
        return clusters[1][0] - 5, None
    if len(clusters) == 1:
        mid = (atouts_x0 + limites_x0) / 2
        return (mid if clusters[0][0] < mid else clusters[0][0] - 5), None
    return None, f"{len(clusters)} clusters de puces"


def fmt_bits(bits: int | None) -> str:
    """Rendu lisible d'un bitfield mensuel (pattern debug du prototype)."""
    if bits is None:
        return "None"
    body = "".join(MONTH_SEQ[i] if bits >> i & 1 else "·" for i in range(12))
    return f"{body} ({bits})"


# ---------------------------------------------------------------------------
# Extraction (nécessite pymupdf)
# ---------------------------------------------------------------------------

def _spans(page):
    """Yield (texte strippé, fitz.Rect) pour chaque span non vide de la page."""
    for block in page.get_text("dict")["blocks"]:
        if block["type"] != 0:
            continue
        for line in block["lines"]:
            for span in line["spans"]:
                text = span["text"].strip()
                if text:
                    yield text, fitz.Rect(span["bbox"])


def _month_rows(spans) -> list[tuple[float, list]]:
    """Rangées de 12 spans mono-lettre formant exactement J F M A M J J A S O N D."""
    singles = [(t, r) for t, r in spans if len(t) == 1 and t in "JFMASOND"]
    rows: dict[float, list] = {}
    for t, r in singles:
        placed = False
        for y in list(rows):
            if abs(y - r.y0) < 3:
                rows[y].append((t, r))
                placed = True
                break
        if not placed:
            rows[r.y0] = [(t, r)]
    out = []
    for y, items in sorted(rows.items()):
        items.sort(key=lambda x: x[1].x0)
        if [t for t, _ in items] == MONTH_SEQ:
            out.append((y, [r for _, r in items]))
    return out


def _is_colored(fill) -> bool:
    """Fill non blanc : au moins un canal s'écarte de 1.0 de plus que WHITE_EPS."""
    return fill is not None and any(abs(c - 1.0) > WHITE_EPS for c in fill)


def _colored_fills(page) -> list[tuple]:
    """Rects de fill colorés, filtrés (width < 300, height < 40) pour écarter
    photos décoratives et bandeaux pleine page."""
    out = []
    for d in page.get_drawings():
        if d["type"] != "f" or not _is_colored(d.get("fill")):
            continue
        rect = d["rect"]
        if rect.width < 300 and rect.height < 40:
            out.append((rect, d["fill"]))
    return out


def _label_for_row(spans, y: float, first_letter_x0: float) -> str | None:
    """Label calendrier le plus proche au-dessus d'une rangée (même colonne gauche)."""
    label = None
    best_dy = 45.0
    for t, r in spans:
        n = _norm(t)
        if n in ("floraison", "fructification") and r.x0 < first_letter_x0 + 60:
            dy = y - r.y1
            if 0 <= dy < best_dy:
                best_dy, label = dy, n
    return label


def _match_fill(fill, ref: tuple[float, float, float]) -> bool:
    """Le fill (RGB) correspond-il à une couleur de référence du template ?"""
    return (fill is not None and len(fill) == 3
            and all(abs(c - r) <= _FILL_EPS for c, r in zip(fill, ref)))


def _row_flags(letter_rects, fills) -> tuple[list[bool], list[tuple]]:
    """Flags mensuels d'une rangée + couleurs des fills qui ont activé des cases.

    Un mois est actif si le centre x de sa lettre tombe dans un fill coloré dont
    la bande y contient le centre y de la rangée (±2 pt).
    """
    band_y = (letter_rects[0].y0 + letter_rects[0].y1) / 2
    flags = [False] * 12
    colors = []
    for i, lr in enumerate(letter_rects):
        cx = (lr.x0 + lr.x1) / 2
        for rect, fill in fills:
            if rect.y0 - 2 <= band_y <= rect.y1 + 2 and rect.x0 <= cx <= rect.x1:
                flags[i] = True
                colors.append(fill)
                break
    return flags, colors


def _parse_calendars(page) -> dict[str, tuple[int | None, str | None]]:
    """Ancre sur les rangées de lettres, valide par le label au-dessus.

    Retourne {"flor": (bits|None, warn|None), "fruct": (bits|None, warn|None)}.
    bits=0 → None + warn. Si AUCUN label n'est trouvé (ancien template, labels
    rasterisés) : fallback à double signal — rangée haute = floraison, basse =
    fructification, accepté seulement si les couleurs des cases des DEUX
    rangées confirment (_FLOR_FILL / _FRUCT_FILL). Jamais d'assignation sur
    l'ordre seul.
    """
    spans = list(_spans(page))
    rows = _month_rows(spans)
    fills = _colored_fills(page)
    result: dict[str, tuple[int | None, str | None]] = {
        "flor": (None, "rangée introuvable"),
        "fruct": (None, "rangée introuvable"),
    }
    unlabeled: list[tuple[float, list]] = []
    labeled_any = False
    for y, letter_rects in rows:
        label = _label_for_row(spans, y, letter_rects[0].x0)
        if label is None:
            unlabeled.append((y, letter_rects))
            continue
        labeled_any = True
        key = "flor" if label == "floraison" else "fruct"
        flags, _colors = _row_flags(letter_rects, fills)
        bits = _bits_from_flags(flags)
        if result[key][0] is not None:
            result[key] = (None, "deux rangées pour le même label")
        else:
            result[key] = (bits, None) if bits else (None, "aucune case colorée")
    if not labeled_any and len(unlabeled) == 2:
        (_, top_rects), (_, bot_rects) = sorted(unlabeled)
        top_flags, top_colors = _row_flags(top_rects, fills)
        bot_flags, bot_colors = _row_flags(bot_rects, fills)
        if (top_colors and all(_match_fill(c, _FLOR_FILL) for c in top_colors)
                and bot_colors and all(_match_fill(c, _FRUCT_FILL) for c in bot_colors)):
            result["flor"] = (_bits_from_flags(top_flags), None)
            result["fruct"] = (_bits_from_flags(bot_flags), None)
        else:
            warn = "labels rasterisés, couleurs non concluantes"
            result = {"flor": (None, warn), "fruct": (None, warn)}
    return result


def _find_a_retenir(doc):
    """Localise la page « À RETENIR » et ses ancres. Retourne
    (spans, atouts_rect, limites_rect) ou None si introuvable."""
    for page in doc:
        spans = list(_spans(page))
        atouts_r = limites_r = None
        for t, r in spans:
            n = _norm(t)
            if n == "atouts":
                atouts_r = r
            elif n.startswith("limites et contraintes"):
                limites_r = r
        if atouts_r is not None and limites_r is not None:
            return page, spans, atouts_r, limites_r
    return None


def _parse_a_retenir(doc) -> tuple[list[str], list[str], list[str]]:
    """Extrait (atouts, limites, warnings). Sections validées indépendamment."""
    found = _find_a_retenir(doc)
    if found is None:
        return [], [], ["à retenir: ancres Atouts/Limites introuvables"]
    _page, spans, atouts_r, limites_r = found
    top_y = max(atouts_r.y1, limites_r.y1)
    # Corps sous les ancres, footer exclu.
    body = [
        (r, t) for t, r in spans
        if r.y0 >= top_y and "guide des essences" not in _norm(t)
    ]
    bullet_xs = [r.x0 for r, t in body if t.startswith("•")]
    split_x, warn = _split_x_from_bullets(bullet_xs, atouts_r.x0, limites_r.x0)
    if split_x is None:
        return [], [], [f"à retenir: {warn}"]
    cols: dict[str, list] = {"atouts": [], "limites": []}
    for r, t in body:
        cols["atouts" if r.x0 < split_x else "limites"].append((r.x0, r.y0, t))
    warnings: list[str] = []
    out: dict[str, list[str]] = {}
    for key, items in cols.items():
        bullets = _extract_bullets(items)
        validated, w = _validate_bullets(bullets)
        out[key] = validated
        if w is not None:
            warnings.append(f"{key}: {w}")
    return out["atouts"], out["limites"], warnings


# ---------------------------------------------------------------------------
# S6 — Extraction des champs textuels (Descriptif / L'essence à Paris / Services)
# ---------------------------------------------------------------------------

def _spans_sized(page):
    """Comme `_spans` mais yield aussi la taille de police (filtre corps/titre)."""
    for block in page.get_text("dict")["blocks"]:
        if block["type"] != 0:
            continue
        for line in block["lines"]:
            for span in line["spans"]:
                text = span["text"].strip()
                if text:
                    yield text, fitz.Rect(span["bbox"]), span["size"]


def _find_headings(spans_sized) -> dict[str, "fitz.Rect"]:
    """Localise les headings de niveau titre (taille > 11 pt) par `_norm` exact.

    Retourne {ancre: Rect}. Ancres absentes = clé absente. « descriptif de
    l'essence » absent → template ancien rasterisé.
    """
    heads: dict[str, "fitz.Rect"] = {}
    for text, rect, size in spans_sized:
        if size <= _HEADING_MIN_SIZE:
            continue
        n = _norm(text)
        if n in _HEADING_ANCHORS and n not in heads:
            heads[n] = rect
    return heads


def _clean(s: str) -> str:
    """Collapse des espaces multiples + recollage de la ponctuation.

    Corrige les artefacts de reconstruction par `" ".join` de spans (espace
    parasite avant `, ; . : ! ? ) ] »` ou après `( [ «`).
    """
    s = re.sub(r"\s+", " ", s).strip()
    s = re.sub(r"\s+([,;.:!?)\]»])", r"\1", s)
    s = re.sub(r"([(\[«])\s+", r"\1", s)
    return s


def _match_desc_label(full: str) -> tuple[str | None, str | None]:
    """Une ligne du Descriptif → (champ, valeur) si elle ouvre un label, sinon
    (None, None) — c'est alors une continuation de la valeur courante.

    Deux stratégies, robustes aux 3 variantes du corpus :
      A. Deux-points présent (cas général, colon attaché au label ou à la
         valeur, et « glyphe scindé » `É`/`corce`) → on compare le texte AVANT
         le premier `:`, espaces retirés, à l'index nospace des labels.
      B. Pas de deux-points (variante « Famille Rosacées », 11 fiches) → match
         par préfixe avec espaces (label le plus long d'abord), borne de mot.
    `_norm` étant 1:1 en longueur sur le latin précomposé, on peut trancher la
    valeur par offset.
    """
    n = _norm(full)
    if ":" in full:
        left = full.split(":", 1)[0]
        key = _norm(left).replace(" ", "")
        fieldname = _DESC_FIELD_BY_NOSPACE.get(key)
        if fieldname:
            return fieldname, full.split(":", 1)[1].strip()
        return None, None
    for text, fieldname in _DESC_TEXTS_BY_LEN:
        if n.startswith(text) and (len(n) == len(text) or n[len(text)] == " "):
            return fieldname, full[len(text):].strip()
    return None, None


def _parse_descriptif(spans, heads) -> tuple[dict, list[str]]:
    """Bloc « Descriptif de l'essence » (colonne gauche) → dict + warnings.

    Retourne {fam, haut, env, croiss, long, iddesc:{ecorce,feuillage,floraison,
    fructification}} (clés omises si vides). Machine à états label→valeur ancrée
    par bbox : zone Y sous le heading Descriptif jusqu'à Paysage (fallback
    Services), zone X à gauche de la colonne « L'essence à Paris ». Les lignes-
    calendrier (mono-lettres J F M A … D) sont ignorées. `origine/indigenat/
    statut/port` bornent mais sont jetés.
    """
    warnings: list[str] = []
    desc = heads["descriptif de l'essence"]
    y_end = heads.get("paysage et cadre de vie") or heads.get(
        "services ecosystemiques rendus")
    y1 = y_end.y0 if y_end is not None else desc.y1 + 400.0
    ep = heads.get("l'essence a paris")
    right_x = (ep.x0 - 15.0) if ep is not None else 285.0

    items = [
        (r.x0, r.y0, t) for t, r in spans
        if desc.y1 <= r.y0 < y1 and r.x0 < right_x
        and not (len(t) == 1 and t in "JFMASOND")
    ]
    fields: dict[str, str] = {}
    current: str | None = None
    for parts in _group_lines(items):
        full = " ".join(t for _, t in parts)
        fieldname, value = _match_desc_label(full)
        if fieldname is not None:
            fields[fieldname] = value or ""
            current = fieldname
        elif current is not None:
            fields[current] = (fields[current] + " " + full).strip()

    out: dict = {}
    iddesc: dict[str, str] = {}

    def keep(fieldname: str, key: str, extra_ok) -> None:
        if fieldname not in fields:
            return
        value = _clean(fields[fieldname])
        lo, hi = _DESC_BOUNDS[key]
        if not (lo <= len(value) <= hi) or not extra_ok(value):
            warnings.append(f"{key}: valeur rejetée ({len(value)} c.: {value!r:.40})")
            return
        if key in ("ecorce", "feuillage", "floraison", "fructification"):
            iddesc[key] = value
        else:
            out[key] = value

    # Pills scalaires.
    if "hauteur" in fields:
        fields["hauteur"] = re.split(
            r"\s[-–]\s", _clean(fields["hauteur"]), maxsplit=1)[0].strip()
    if "envergure" in fields:
        fields["envergure"] = re.split(
            r"\s[-–]\s", _clean(fields["envergure"]), maxsplit=1)[0].strip()
    keep("famille", "fam",
         lambda v: ":" not in v and not any(c.isdigit() for c in v) and v.count(" ") <= 1)
    # Hauteur/envergure : « 10 m », « 8 m », mais aussi décimales « 8,5 m ».
    keep("hauteur", "haut", lambda v: bool(re.match(r"^\d+([.,]\d+)?\s*m", v)))
    keep("envergure", "env", lambda v: bool(re.match(r"^\d+([.,]\d+)?\s*m", v)))
    keep("croissance", "croiss", lambda v: not any(c.isdigit() for c in v))
    keep("longevite", "long", lambda v: True)
    # Descriptions d'identification.
    keep("ecorce", "ecorce", lambda v: True)
    keep("feuillage", "feuillage", lambda v: True)
    keep("floraison", "floraison", lambda v: True)
    keep("fructification", "fructification", lambda v: True)

    # Renommage clés scalaires internes → clés `ess`.
    result: dict = {}
    for internal, key in (("fam", "fam"), ("haut", "haut"), ("env", "env"),
                          ("croiss", "croiss"), ("long", "long")):
        if key in out:
            result[key] = out[key]
    if iddesc:
        result["iddesc"] = iddesc
    return result, warnings


def _parse_essence_paris(spans_sized, heads) -> tuple[str | None, list[str]]:
    """Encart « L'essence à Paris » (colonne droite haute) → prose + warnings.

    Zone Y : sous le heading « L'essence à Paris » jusqu'à « Sites de plantation »
    (fallback Services). Zone X : colonne droite. Footer crédits/attribution
    exclu. `" ".join` des lignes + `_clean`, validation de longueur.
    """
    warnings: list[str] = []
    ep = heads.get("l'essence a paris")
    if ep is None:
        return None, ["essence-paris: heading absent"]
    y_end = (heads.get("sites de plantation recommandes")
             or heads.get("services ecosystemiques rendus"))
    y1 = y_end.y0 if y_end is not None else ep.y1 + 200.0
    right_x = ep.x0 - 15.0
    items = [
        (r.x0, r.y0, t) for t, r, size in spans_sized
        if ep.y1 <= r.y0 < y1 and r.x0 > right_x and size < 11.5
        and not _norm(t).startswith(("credits photos", "guide des essences"))
    ]
    if not items:
        return None, ["essence-paris: aucune ligne dans la zone"]
    prose = _clean(" ".join(
        " ".join(t for _, t in parts) for parts in _group_lines(items)))
    lo, hi = _PARIS_BOUNDS
    if not (lo <= len(prose) <= hi):
        return None, [f"essence-paris: prose rejetée ({len(prose)} c.)"]
    return prose, warnings


def _parse_services(spans, heads, page_bottom: float) -> tuple[dict, list[str]]:
    """Services écosystémiques (colonne droite basse) → {climat,eau,biodiv}.

    Ancrage par ligne sur les 3 labels. Les scores pictos sont des **spans
    isolés** dont le texte est un entier 1-2 chiffres (note /10, valeurs 0-10) :
    on les écarte AU NIVEAU DU SPAN, avant regroupement en lignes. C'est le
    discriminant sûr — vérifié sur tout le corpus (716 occurrences, toutes des
    scores) : un nombre légitime de prose (« Plus de 35 espèces », « 150 000 € »)
    n'est jamais un span isolé purement numérique, il vit dans un span de texte.
    L'ancien filtre par seuil x laissait fuir les scores « 10 » (x0 ~210, au
    milieu de la ligne de prose après tri par x). Une fois scores et footer
    écartés, la zone ne contient plus que la prose des 3 services : toute ligne
    non-label est rattachée au service courant.
    """
    warnings: list[str] = []
    sv = heads.get("services ecosystemiques rendus")
    if sv is None:
        return {}, ["services: heading absent"]
    # Tolérance de 3 pt vers le haut : sur certains layouts le 1er label
    # (« Régulation du climat local ») chevauche la ligne de base du heading
    # (y0 ~0.3 pt au-dessus de son y1). On exclut le heading lui-même par `_norm`.
    items = [
        (r.x0, r.y0, t) for t, r in spans
        if r.y0 >= sv.y1 - 3 and r.y0 < page_bottom
        and _norm(t) != "services ecosystemiques rendus"
        and not _norm(t).startswith(("credits photos", "guide des essences"))
        and not re.fullmatch(r"\d{1,2}", t)  # score picto = span isolé 1-2 chiffres
    ]
    acc: dict[str, list[str]] = {}
    order: list[str] = []
    current: str | None = None
    for parts in _group_lines(items):
        full = " ".join(t for _, t in parts)
        n = _norm(full)
        label_key = None
        for prefix, key in _SVC_LABELS:
            if n.startswith(prefix):
                label_key, prefix_len = key, len(prefix)
                break
        if label_key is not None:
            current = label_key
            value = full[prefix_len:].lstrip(" :")
            if label_key not in acc:
                acc[label_key] = []
                order.append(label_key)
            acc[label_key].append(value)
        elif current is not None:
            acc[current].append(full)
    out: dict = {}
    for key in order:
        prose = _clean(" ".join(acc[key]))
        lo, hi = _SVC_BOUNDS
        if lo <= len(prose) <= hi:
            out[key] = prose
        else:
            warnings.append(f"svc.{key}: prose rejetée ({len(prose)} c.)")
    return out, warnings


def extract_extras(pdf_path: Path) -> EssenceExtras:
    """Extrait calendriers + À RETENIR + champs textuels d'une fiche. Nécessite pymupdf."""
    _require_fitz()
    warnings: list[str] = []
    fam = haut = env = croiss = long = paris = None
    iddesc: dict[str, str] = {}
    svc: dict[str, str] = {}
    with fitz.open(pdf_path) as doc:
        page0 = doc[0]
        cals = _parse_calendars(page0)
        flor, fwarn = cals["flor"]
        if fwarn:
            warnings.append(f"floraison: {fwarn}")
        fruct, frwarn = cals["fruct"]
        if frwarn:
            warnings.append(f"fructification: {frwarn}")
        atouts, limites, awarns = _parse_a_retenir(doc)
        warnings.extend(awarns)

        # Champs textuels S6 (page 0, template récent uniquement).
        spans_sized = list(_spans_sized(page0))
        heads = _find_headings(spans_sized)
        if "descriptif de l'essence" not in heads:
            warnings.append(
                "descriptif: ancien template (heading absent), "
                "champs textuels non extraits")
        else:
            spans = [(t, r) for t, r, _ in spans_sized]
            desc, dwarn = _parse_descriptif(spans, heads)
            warnings.extend(dwarn)
            fam = desc.get("fam")
            haut = desc.get("haut")
            env = desc.get("env")
            croiss = desc.get("croiss")
            long = desc.get("long")
            iddesc = desc.get("iddesc", {})
            paris, pwarn = _parse_essence_paris(spans_sized, heads)
            warnings.extend(pwarn)
            svc, swarn = _parse_services(spans, heads, page0.rect.y1)
            warnings.extend(swarn)

    return EssenceExtras(
        flor=flor, fruct=fruct, atouts=atouts, limites=limites,
        fam=fam, haut=haut, env=env, croiss=croiss, long=long,
        iddesc=iddesc, paris=paris, svc=svc, warnings=warnings,
    )


def report_clips(doc) -> dict[str, tuple[int, "fitz.Rect"]]:
    """Zones de crop pour le rapport HTML.

    {"calendars": (0, rect englobant les deux strips + labels),
     "aretenir": (pno, rect englobant headings + puces)}.
    Clé absente si la zone est introuvable.
    """
    _require_fitz()
    clips: dict[str, tuple[int, "fitz.Rect"]] = {}

    # Calendriers (page 0) : union des rects des lettres + labels.
    page0 = doc[0]
    spans0 = list(_spans(page0))
    rows = _month_rows(spans0)
    if rows:
        rect = fitz.Rect(rows[0][1][0])
        for y, letter_rects in rows:
            for lr in letter_rects:
                rect |= lr
            label = _label_for_row(spans0, y, letter_rects[0].x0)
            if label is not None:
                for t, r in spans0:
                    if _norm(t) == label:
                        rect |= r
        clips["calendars"] = (0, rect)

    # À RETENIR : union des headings + puces.
    found = _find_a_retenir(doc)
    if found is not None:
        page, spans, atouts_r, limites_r = found
        rect = fitz.Rect(atouts_r)
        rect |= limites_r
        top_y = max(atouts_r.y1, limites_r.y1)
        for t, r in spans:
            if r.y0 >= top_y and "guide des essences" not in _norm(t):
                rect |= r
        clips["aretenir"] = (page.number, rect)

    # S6 — Descriptif (colonne gauche) & colonne droite (Paris + Services).
    spans_sized0 = list(_spans_sized(page0))
    heads = _find_headings(spans_sized0)
    desc = heads.get("descriptif de l'essence")
    if desc is not None:
        ep = heads.get("l'essence a paris")
        right_x = (ep.x0 - 15.0) if ep is not None else 285.0
        y_end = heads.get("paysage et cadre de vie") or heads.get(
            "services ecosystemiques rendus")
        y1 = y_end.y0 if y_end is not None else desc.y1 + 400.0
        d_rect = fitz.Rect(desc)
        for t, r, _size in spans_sized0:
            if desc.y1 <= r.y0 < y1 and r.x0 < right_x:
                d_rect |= r
        clips["descriptif"] = (0, d_rect)

        # Colonne droite : encart Paris (sous son heading) + Services.
        ps_rect = None
        if ep is not None:
            sites = heads.get("sites de plantation recommandes")
            py1 = sites.y0 if sites is not None else ep.y1 + 200.0
            ps_rect = fitz.Rect(ep)
            for t, r, size in spans_sized0:
                if ep.y1 <= r.y0 < py1 and r.x0 > (ep.x0 - 15.0) and size < 11.5:
                    ps_rect |= r
        sv = heads.get("services ecosystemiques rendus")
        if sv is not None:
            # Colonne de prose seulement (x >= 200) : on écarte les scores pictos
            # de la colonne gauche qui élargiraient inutilement le crop.
            sv_rect = None
            for t, r, _size in spans_sized0:
                if (r.y0 >= sv.y1 - 3 and r.x0 >= 200
                        and not _norm(t).startswith(("credits photos",
                                                     "guide des essences"))):
                    sv_rect = fitz.Rect(r) if sv_rect is None else (sv_rect | r)
            if sv_rect is not None:
                ps_rect = sv_rect if ps_rect is None else (ps_rect | sv_rect)
        if ps_rect is not None:
            clips["paris_svc"] = (0, ps_rect)

    return clips


# ---------------------------------------------------------------------------
# Réseau : fetch download-once
# ---------------------------------------------------------------------------

def fetch_pdf(pdf_id: str, url: str) -> Path:
    """Télécharge (une seule fois) la fiche PDF dans PDF_CACHE_DIR/{pdf_id}.pdf.

    Si le fichier existe déjà, le retourne sans réseau. urllib stdlib + User-Agent
    + retry simple (pattern des autres fetchs de build_dataset.py).
    """
    PDF_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    dest = PDF_CACHE_DIR / f"{pdf_id}.pdf"
    if dest.exists():
        return dest
    req = urllib.request.Request(url, headers={"User-Agent": _USER_AGENT})
    last_err: Exception | None = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = resp.read()
            dest.write_bytes(data)
            return dest
        except (urllib.error.URLError, TimeoutError) as e:  # pragma: no cover
            last_err = e
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"échec téléchargement PDF {pdf_id} ({url}): {last_err}")


def extract_all(records: list[dict]) -> dict[str, EssenceExtras]:
    """Extrait les extras de tous les records (dédup par pdf_id).

    records = les 200 records API bruts (clé `nom_fichier_pdf_associe` =
    {id, url, filename}). Records sans PDF skippés. Log sobre (une ligne / dizaine).
    """
    _require_fitz()
    # Dédup par pdf_id, en préservant l'ordre de première rencontre.
    todo: dict[str, str] = {}
    for rec in records:
        pdf = rec.get("nom_fichier_pdf_associe") or {}
        pdf_id, url = pdf.get("id"), pdf.get("url")
        if pdf_id and url and pdf_id not in todo:
            todo[pdf_id] = url
    print(f"[pdf ] extraction de {len(todo)} fiches PDF (cache: "
          f"{PDF_CACHE_DIR.relative_to(ROOT)})")
    out: dict[str, EssenceExtras] = {}
    for i, (pdf_id, url) in enumerate(todo.items(), 1):
        try:
            path = fetch_pdf(pdf_id, url)
            out[pdf_id] = extract_extras(path)
        except Exception as e:  # pragma: no cover - robustesse réseau/PDF
            out[pdf_id] = EssenceExtras(
                flor=None, fruct=None,
                warnings=[f"extraction échouée: {e}"],
            )
        if i % 10 == 0 or i == len(todo):
            print(f"[pdf ]   {i}/{len(todo)} fiches traitées")
    return out


# ---------------------------------------------------------------------------
# Entrée debug
# ---------------------------------------------------------------------------

def _dump(target: str) -> None:
    _require_fitz()
    p = Path(target)
    path = p if p.exists() else PDF_CACHE_DIR / f"{target}.pdf"
    if not path.exists():
        raise SystemExit(f"PDF introuvable : {path}")
    extras = extract_extras(path)
    print(f"=== {path.name} ===")
    print(f"  flor   {fmt_bits(extras.flor)}")
    print(f"  fruct  {fmt_bits(extras.fruct)}")
    for label, items in (("atouts", extras.atouts), ("limites", extras.limites)):
        print(f"  {label} ({len(items)}):")
        for it in items:
            print(f"    • {it}")
    print(f"  fam    {extras.fam!r}")
    print(f"  haut   {extras.haut!r}")
    print(f"  env    {extras.env!r}")
    print(f"  croiss {extras.croiss!r}")
    print(f"  long   {extras.long!r}")
    print("  iddesc :")
    for k in ("ecorce", "feuillage", "floraison", "fructification"):
        if k in extras.iddesc:
            print(f"    {k}: {extras.iddesc[k]}")
    print(f"  paris  {extras.paris!r}")
    print("  svc :")
    for k in ("climat", "eau", "biodiv"):
        if k in extras.svc:
            print(f"    {k}: {extras.svc[k]}")
    if extras.warnings:
        print("  warnings :")
        for w in extras.warnings:
            print(f"    ! {w}")


def main(argv: list[str]) -> int:
    if len(argv) == 3 and argv[1] == "--dump":
        _dump(argv[2])
        return 0
    print("usage: python3 tools/essence_pdf.py --dump <pdf_id|chemin>",
          file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
