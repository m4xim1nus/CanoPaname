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
    python3 tools/essence_pdf.py --dump <pdf_id|chemin>     # extras texte
    python3 tools/essence_pdf.py --photos <pdf_id|chemin>   # photos S9
"""
from __future__ import annotations

import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from io import BytesIO
from pathlib import Path

try:
    import fitz  # PyMuPDF
except ImportError:  # pragma: no cover - dépend de l'install
    fitz = None

try:
    from PIL import Image, ImageStat  # Pillow — encodage WebP + variance couleur
except ImportError:  # pragma: no cover - dépend de l'install
    Image = None
    ImageStat = None

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


# ---------------------------------------------------------------------------
# S9 — Constantes d'extraction des photos officielles (page 0)
# ---------------------------------------------------------------------------
# Heuristiques validées empiriquement sur les 200 fiches (rendus vérifiés) :
# la photo principale est la plus grosse image « colonne droite » (aire native
# ≥ 70 k px, hors bandeaux/fonds pleine largeur), les détails les images
# secondaires. Certaines photos sont scindées en fragments verticaux — on les
# recolle (voir `select_photos`). Toutes les mesures de bbox sont en points PDF,
# les mesures d'image (width/height/nbytes) sont natives (pixels/octets bruts).
_PH_FULLW = 0.9          # bbox_w >= 0.9*page_w -> bandeau/fond pleine page, exclu
# Garde d'en-tête (y0 minimal) à DEUX niveaux. Principale : 25 pt — à 70, le
# garde perdait 3 vrais portraits colonne-droite haut placés (Prunus avium
# y0=60.4, Platanus orientalis 30.1, occidentalis 32.9) au profit d'un gros
# plan feuille/fleur ; sûr car aucune image colonne-droite >= 70 000 px natifs
# n'a y0 < 30 sur le corpus, et le logo d'en-tête (46 200 px) est déjà écarté
# par _PH_PRINCIPAL_MIN_PX. Détails : 70 pt — les détails n'ont pas de plancher
# d'aire native, et la bande 25-70 contient des schémas de « port » stylisés
# (silhouette d'arbre, Populus tremula 'Erecta') qui fuiteraient sinon comme
# faux détails.
_PH_Y_TOP = 25.0
_PH_Y_TOP_DETAIL = 70.0
_PH_Y_BOT = 745.0        # y1 > 745 pt -> logos/crédits de pied
_PH_DS_MAX = 10.0        # native_w/bbox_w > 10 -> bande décorative squeezée
_PH_DS_MIN = 1.0         # native_w/bbox_w < 1 -> vignette 2x2 étirée (déco), exclue
_PH_MIN_AREA = 4000.0    # aire bbox (pt²) minimale
_PH_XEPS = 2.5           # tolérance d'alignement x (empilement de fragments)
_PH_GAP = 12.0           # écart vertical max (pt) entre deux fragments empilés
_PH_ASPECT_WIDE = 1.8    # w/h >= 1.8 -> « tranche » (fragment horizontal fin)
_PH_PRINCIPAL_MIN_PX = 70_000  # aire native mini d'une candidate principale
_PH_MAX_DETAILS = 3

# Garde anti-aplat : rejette les **aplats décoratifs quasi-uniformes** (panneaux
# de couleur unie posés en fond de colonne) qui, à l'occasion, ont une grande
# bbox et une aire native élevée, et rafleraient donc la principale — cas unique
# vérifié : Cedrus atlantica (sk 99), un panneau bleu uni 711×948 recouvrant le
# vrai portrait. Le discriminant est la **variance couleur** (moyenne des 3
# variances de canal sur une vignette 32×32), PAS les octets/pixel : le corpus
# a montré que l'aplat compresse à ~0,029 octet/px, indissociable des vraies
# photos haute résolution (min réel ~0,049 o/px, séparation ×1,7 seulement — un
# seuil o/px n'atteint pas la marge ×3 exigée). La variance, elle, sépare
# franchement : aplat sk 99 = 0,6 vs plus basse vraie photo retenue = 271
# (séparation ×430). Seuil 30 → marge ×48 côté aplat, ×9 côté vraies photos.
# La variance étant impure (décodage pixels), elle est calculée dans
# `_page0_inventory` et transportée par le champ `colvar` de l'inventaire ; le
# FILTRE reste dans la fonction pure `select_photos` (applicable à TOUTE
# candidate — un aplat ne doit jamais devenir principale NI détail).
_PH_VAR_SIZE = 32       # côté de la vignette de mesure de variance couleur
_PH_MIN_COLVAR = 30.0   # variance couleur mini d'une vraie photo (sinon = aplat)
_CAP_PRINCIPAL = 1100    # cap long-edge px (principale)
_CAP_DETAIL = 800        # cap long-edge px (détail)
_WEBP_QUALITY = 80
_WEBP_METHOD = 6

# Placeholder générique « Photos à venir » de la Ville de Paris (illustration de
# feuilles tamponnées) embarqué comme visuel unique sur les fiches mono-photo
# sans cliché réel. Le corpus en contient 8 ré-encodages distincts : 2 variantes
# « grande » (1182×1004) et 6 « petites » (455-647 px de large). Un blocklist md5
# des octets bruts ne rattrapait que 2 md5 → les 6 petites variantes fuyaient
# comme photo principale (sk 167, 192, 403, 474 + 2 cultivars Ulmus latents). On
# passe donc à un **dHash perceptuel 8×8** (cf. `_dhash`), robuste au ré-encodage
# et au redimensionnement : les 8 variantes tombent à Hamming <= 2 des références,
# la 1re vraie photo du corpus est à Hamming 21 → seuil <= 8 (marge des 2 côtés).
# Principe « ne jamais inventer » : rejeté à l'inventaire — mieux vaut 0 photo
# qu'un faux visuel présenté comme la photo officielle de l'espèce. Références =
# dHash des octets natifs des 2 variantes « grande » (mesuré au build).
_PH_PLACEHOLDER_DHASHES = (
    544520902464865219,  # variante A (grande, 1182×1004)
    544520902397756359,  # variante B (grande, 1182×1004)
)
_PH_PLACEHOLDER_DHASH_MAX_DIST = 8


def _require_fitz() -> None:
    """Lève une SystemExit claire si pymupdf n'est pas installé."""
    if fitz is None:
        raise SystemExit(
            "pymupdf (fitz) requis pour l'extraction des fiches-essences.\n"
            "Installe les dépendances build : pip install -r tools/requirements.txt"
        )


def _require_pillow() -> None:
    """Lève une SystemExit claire si Pillow n'est pas installé.

    Pillow n'est nécessaire QUE pour l'extraction des photos (encodage WebP,
    hors de portée de PyMuPDF). Import protégé + garde tardive : le reste du
    module (calendriers, À RETENIR, champs textuels) tourne sans Pillow.
    """
    if Image is None:
        raise SystemExit(
            "Pillow requis pour l'extraction des photos des fiches-essences.\n"
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


def _hamming(a: int, b: int) -> int:
    """Distance de Hamming entre deux hashes entiers (nombre de bits différents)."""
    return bin(a ^ b).count("1")


def _dhash(gray) -> int:
    """dHash perceptuel 8×8 → entier 64 bits (row-major, bit de poids fort en tête).

    `gray` est soit une **PIL.Image** (convertie en niveaux de gris puis réduite
    à 9×8 par LANCZOS), soit une **séquence plate de 72 niveaux de gris** déjà en
    8 rangées × 9 colonnes (row-major) — cette seconde forme rend la fonction
    PURE et testable sans Pillow. Chaque bit compare deux pixels horizontalement
    adjacents (gauche > droite → 1), soit 8×8 = 64 comparaisons. Contrairement au
    md5 des octets bruts, un dHash survit au ré-encodage et au redimensionnement,
    ce qui permet de re-identifier un même visuel embarqué sous plusieurs formes.
    """
    if hasattr(gray, "convert"):  # PIL.Image
        gray = list(gray.convert("L").resize((9, 8), Image.LANCZOS).getdata())
    bits = 0
    for row in range(8):
        base = row * 9
        for col in range(8):
            bits = (bits << 1) | (1 if gray[base + col] > gray[base + col + 1] else 0)
    return bits


def _is_placeholder_dhash(dh: int | None) -> bool:
    """Le dHash correspond-il à une variante du placeholder « Photos à venir » ?

    True si à distance de Hamming <= `_PH_PLACEHOLDER_DHASH_MAX_DIST` d'une des
    références. `None` (décodage raté) → False : on n'écarte jamais une image sur
    un doute de mesure (« ne jamais inventer » vaut dans les deux sens).
    """
    return dh is not None and any(
        _hamming(dh, ref) <= _PH_PLACEHOLDER_DHASH_MAX_DIST
        for ref in _PH_PLACEHOLDER_DHASHES)


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
                "descriptif: PDF rasterisé (heading absent), "
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
# S9 — Extraction des photos officielles (page 0 → WebP)
# ---------------------------------------------------------------------------
# Les fiches embarquent des photos botaniques (crédit Ville de Paris, ODbL v1.0)
# toutes en page 0 : une principale (colonne droite, arbre entier) + 0-3 détails
# (feuille/écorce/fruit). `select_photos` est PURE (dicts d'inventaire natifs →
# groupes) et testable sans fitz/Pillow. `extract_photos` orchestre l'extraction
# et l'encodage WebP. Principe INVARIANT « ne jamais inventer » : échec →
# ([], warnings), jamais de crash ni de photo devinée.


@dataclass
class SpeciesPhoto:
    """Une photo extraite et encodée en WebP, prête à écrire en asset.

    `role` ∈ {"principal", "detail"}. `src_xrefs` = xrefs des fragments recollés
    (1 si photo entière, N si scindée), dans l'ordre haut→bas.
    """

    role: str
    webp: bytes
    width: int
    height: int
    src_xrefs: tuple[int, ...]


def _photo_group(xrefs, width, height, bbox, nbytes, role, colvar=float("inf")):
    """Construit le dict-groupe manipulé par `select_photos` (format interne).

    `colvar` = variance couleur du groupe (garde anti-aplat) ; défaut `inf` =
    « pas un aplat » pour les appels/tests qui ne renseignent pas ce signal.
    """
    return {
        "xrefs": tuple(xrefs),
        "width": width,
        "height": height,
        "bbox": bbox,
        "nbytes": nbytes,
        "role": role,
        "colvar": colvar,
    }


def _chains(a: dict, b: dict) -> bool:
    """`b` (juste en dessous de `a`, tri par y0) empile-t-il sur `a` ?

    Même largeur native, bords x alignés (< `_PH_XEPS`), écart vertical
    bord-à-bord ∈ [-2, `_PH_GAP`] pt (léger chevauchement toléré).
    """
    if a["width"] != b["width"]:
        return False
    if abs(a["bbox"][0] - b["bbox"][0]) >= _PH_XEPS:
        return False
    if abs(a["bbox"][2] - b["bbox"][2]) >= _PH_XEPS:
        return False
    gap = b["bbox"][1] - a["bbox"][3]  # y0(b) - y1(a)
    return -2.0 <= gap <= _PH_GAP


def _is_tranche(item: dict) -> bool:
    """« Tranche » = fragment horizontal fin (aspect natif w/h >= `_PH_ASPECT_WIDE`)."""
    h = item["height"]
    return h > 0 and (item["width"] / h) >= _PH_ASPECT_WIDE


def _fuse_run(run: list[dict]) -> dict:
    """Fusionne un run d'empilement en un seul groupe (xrefs ordonnés par y0)."""
    ordered = sorted(run, key=lambda it: it["bbox"][1])
    xrefs = [it["xref"] for it in ordered]
    width = ordered[0]["width"]
    height = sum(it["height"] for it in ordered)
    nbytes = sum(it["nbytes"] for it in ordered)
    x0 = min(it["bbox"][0] for it in ordered)
    y0 = min(it["bbox"][1] for it in ordered)
    x1 = max(it["bbox"][2] for it in ordered)
    y1 = max(it["bbox"][3] for it in ordered)
    # Variance du groupe = max des membres : un run n'est « aplat » que si TOUS
    # ses fragments le sont (une seule tranche texturée suffit à le rendre réel).
    colvar = max(it.get("colvar", float("inf")) for it in ordered)
    return _photo_group(
        xrefs, width, height, (x0, y0, x1, y1), nbytes, None, colvar)


def select_photos(
    inventory: list[dict], page_w: float
) -> tuple[dict | None, list[dict]]:
    """Choisit la photo principale + les détails depuis l'inventaire page 0.

    PURE (aucune dépendance fitz/Pillow) : testable sur inventaires synthétiques.
    `inventory[i] = {"xref", "bbox": (x0,y0,x1,y1), "width", "height", "nbytes",
    "colvar"}` (bbox en points, width/height/nbytes natifs ; `colvar` = variance
    couleur mesurée en amont par `_page0_inventory`, `inf` si absent). Retourne
    `(principale|None, détails)` où chaque photo = groupe `_photo_group(...)`.
    Jamais d'exception.

    Algo :
      1. exclure les images pleine largeur (bandeaux/fonds) ;
      2. trier par y0 ;
      3. runs maximaux empilables par chaînage transitif (`_chains`) ;
      4. fusionner un run ssi ≤ 1 membre « non-tranche » (sinon deux détails
         distincts empilés — cas Celtis feuille/écorce — émis séparément) ;
      5. filtrer : y0 >= `_PH_Y_TOP`, y1 <= `_PH_Y_BOT`, downscale
         native_w/bbox_w ∈ [`_PH_DS_MIN`, `_PH_DS_MAX`], aire bbox >=
         `_PH_MIN_AREA`, et variance couleur >= `_PH_MIN_COLVAR` (garde
         anti-aplat décoratif, appliquée à TOUTE candidate) ;
      6. principale = groupe de plus grande **aire de bbox** (pt², après
         recollage) parmi les groupes d'aire native ≥ `_PH_PRINCIPAL_MIN_PX`
         (aucun → `(None, [])`). Choisir l'aire de bbox — et non les octets —
         privilégie le portrait d'arbre (grande vignette colonne droite) sur un
         gros plan écorce/feuille (petite vignette mais lourde en octets).
         Tie-break déterministe : nbytes décroissants puis plus petit xref ;
      7. détails = le reste avec y0 >= `_PH_Y_TOP_DETAIL` (la bande 25-70 pt
         n'est admise que pour la principale — sinon schémas/pictos d'en-tête),
         trié par y0, tronqué à `_PH_MAX_DETAILS`.
    """
    # (1) exclure pleine largeur.
    kept = [
        it for it in inventory
        if (it["bbox"][2] - it["bbox"][0]) < _PH_FULLW * page_w
    ]
    # (2) tri par y0.
    kept.sort(key=lambda it: it["bbox"][1])

    # (3) runs maximaux empilables (chaînage sur le dernier membre ajouté).
    runs: list[list[dict]] = []
    for it in kept:
        if runs and _chains(runs[-1][-1], it):
            runs[-1].append(it)
        else:
            runs.append([it])

    # (4) fusion conditionnelle.
    groups: list[dict] = []
    for run in runs:
        n_non_tranche = sum(1 for it in run if not _is_tranche(it))
        if len(run) == 1 or n_non_tranche <= 1:
            groups.append(_fuse_run(run))
        else:
            # Deux détails distincts empilés : ne pas recoller, émettre chacun.
            for it in run:
                groups.append(_photo_group(
                    [it["xref"]], it["width"], it["height"],
                    it["bbox"], it["nbytes"], None,
                    it.get("colvar", float("inf"))))

    # (5) filtres géométriques.
    def _ok(g: dict) -> bool:
        x0, y0, x1, y1 = g["bbox"]
        bbox_w = x1 - x0
        bbox_h = y1 - y0
        if y0 < _PH_Y_TOP or y1 > _PH_Y_BOT:
            return False
        if bbox_w <= 0:
            return False
        downscale = g["width"] / bbox_w
        # Bande déco squeezée (ratio haut) OU vignette 2x2 étirée (ratio bas) :
        # une vraie photo est stockée en sur-résolution, ratio ~2-3.
        if not (_PH_DS_MIN <= downscale <= _PH_DS_MAX):
            return False
        if bbox_w * bbox_h < _PH_MIN_AREA:
            return False
        # Aplat décoratif quasi-uniforme (variance couleur ~0) : rejeté pour
        # TOUTE candidate (jamais principale NI détail). Cf. `_PH_MIN_COLVAR`.
        if g.get("colvar", float("inf")) < _PH_MIN_COLVAR:
            return False
        return True

    groups = [g for g in groups if _ok(g)]
    if not groups:
        return None, []

    # (6) principale = plus grande aire de bbox parmi les groupes d'aire native
    # suffisante (favorise le portrait d'arbre sur un gros plan lourd mais petit).
    # Tie-break déterministe : nbytes décroissants puis plus petit xref.
    candidates = [
        g for g in groups if g["width"] * g["height"] >= _PH_PRINCIPAL_MIN_PX
    ]
    if not candidates:
        return None, []

    def _bbox_area(g: dict) -> float:
        x0, y0, x1, y1 = g["bbox"]
        return (x1 - x0) * (y1 - y0)

    principal = max(
        candidates, key=lambda g: (_bbox_area(g), g["nbytes"], -min(g["xrefs"])))
    principal["role"] = "principal"

    # (7) détails = le reste, trié par y0, tronqué. Garde d'en-tête renforcée :
    # la bande 25-70 pt n'est admise que pour la principale (portraits haut
    # placés) — un groupe non-principal qui y traîne est un schéma/picto.
    details = [
        g for g in groups
        if g is not principal and g["bbox"][1] >= _PH_Y_TOP_DETAIL
    ]
    details.sort(key=lambda g: g["bbox"][1])
    details = details[:_PH_MAX_DETAILS]
    for g in details:
        g["role"] = "detail"
    return principal, details


def _image_metrics(raw: bytes) -> tuple[float, int | None]:
    """Décode `raw` UNE fois → (variance couleur, dHash perceptuel 8×8).

    Mutualise le décodage Pillow entre les deux signaux mesurés à l'inventaire :
      - **variance couleur** (moyenne des 3 variances de canal sur une vignette
        `_PH_VAR_SIZE`², déterministe, dénuit le JPEG) → garde anti-aplat
        décoratif (un aplat quasi-uniforme tombe proche de 0 ; cf. `_PH_MIN_COLVAR`) ;
      - **dHash 8×8** → garde anti-placeholder « Photos à venir »
        (cf. `_PH_PLACEHOLDER_DHASHES` / `_is_placeholder_dhash`).
    Impur (Pillow) — c'est pourquoi il vit ici et non dans les fonctions pures
    `select_photos` / `_is_placeholder_dhash`. Toute erreur de décodage →
    `(inf, None)` : on n'écarte JAMAIS une image par excès de prudence (« ne
    jamais inventer » vaut dans les deux sens, un doute de mesure ne doit pas
    faire disparaître une vraie photo).
    """
    try:
        img = Image.open(BytesIO(raw)).convert("RGB")
    except Exception:  # pragma: no cover - image pathologique
        return float("inf"), None
    var = ImageStat.Stat(img.resize((_PH_VAR_SIZE, _PH_VAR_SIZE))).var
    return sum(var) / len(var), _dhash(img)


def _page0_inventory(doc) -> tuple[list[dict], bool]:
    """Inventaire des images de la page 0 → `(inventaire, placeholder_vu)`.

    `get_image_info(xrefs=True)` fournit bbox (points) + dims natives ;
    `extract_image` fournit les octets bruts (nbytes) et sert aussi à mesurer la
    variance couleur (`colvar`, garde anti-aplat) et le dHash (garde anti-
    placeholder) via `_image_metrics` — tout mémoïsé par xref pour ne pas
    ré-extraire/re-décoder les images placées plusieurs fois. Placements sans
    xref (images inline) ignorés — non extractibles.

    Le placeholder générique « Photos à venir » (cf. `_PH_PLACEHOLDER_DHASHES`)
    est écarté de l'inventaire dès la lecture, sur dHash perceptuel des octets
    natifs (robuste aux ré-encodages). Le second élément du tuple signale si un
    tel placeholder a été rencontré : `extract_photos` s'en sert pour distinguer
    « fiche sans photo réelle » d'un échec.

    Chaque entrée porte `colvar` (variance couleur native) : `select_photos`,
    resté pur, s'en sert pour rejeter les aplats décoratifs (cf. `_PH_MIN_COLVAR`).
    """
    page = doc[0]
    nbytes_cache: dict[int, int] = {}
    colvar_cache: dict[int, float] = {}
    inv: list[dict] = []
    saw_placeholder = False
    for info in page.get_image_info(xrefs=True):
        xref = info.get("xref") or 0
        if not xref:
            continue
        if xref not in nbytes_cache:
            try:
                raw = doc.extract_image(xref).get("image", b"")
            except Exception:  # pragma: no cover - PDF pathologique
                raw = b""
            nbytes_cache[xref] = len(raw)
            colvar_cache[xref] = float("inf")
            # Placeholder « Photos à venir » : écarté sur dHash perceptuel des
            # octets natifs (robuste aux 8 ré-encodages du corpus, cf.
            # `_PH_PLACEHOLDER_DHASHES`). Sinon, on conserve la variance couleur
            # (garde anti-aplat) — le décodage Pillow est mutualisé entre les deux.
            if raw:
                colvar, dh = _image_metrics(raw)
                if _is_placeholder_dhash(dh):
                    nbytes_cache[xref] = -1  # marqueur : ne pas inventorier ce xref
                else:
                    colvar_cache[xref] = colvar
        if nbytes_cache[xref] < 0:
            saw_placeholder = True
            continue
        bbox = tuple(float(v) for v in info["bbox"])
        inv.append({
            "xref": xref,
            "bbox": bbox,
            "width": int(info.get("width") or 0),
            "height": int(info.get("height") or 0),
            "nbytes": nbytes_cache[xref],
            "colvar": colvar_cache[xref],
        })
    return inv, saw_placeholder


def _encode_photo(doc, group: dict, cap: int) -> tuple["SpeciesPhoto | None", str | None]:
    """Recolle les fragments d'un groupe et encode en WebP. `(photo|None, warn)`.

    Chaque fragment : `extract_image` → `Image.open(...).convert("RGB")` (couvre
    PNG, grayscale, smask). Recollage vertical par `paste` dans l'ordre des
    xrefs (déjà haut→bas). Resize LANCZOS si le long-edge dépasse `cap`. Toute
    erreur → `(None, warning)`, jamais de crash (« ne jamais inventer »).
    """
    role = group["role"]
    try:
        tiles = []
        for xref in group["xrefs"]:
            raw = doc.extract_image(xref).get("image")
            if not raw:
                return None, f"photos: {role} xref {xref} sans données"
            tiles.append(Image.open(BytesIO(raw)).convert("RGB"))
        if len(tiles) == 1:
            composed = tiles[0]
        else:
            width = max(t.width for t in tiles)
            total_h = sum(t.height for t in tiles)
            composed = Image.new("RGB", (width, total_h), (255, 255, 255))
            y = 0
            for t in tiles:
                composed.paste(t, (0, y))
                y += t.height
        long_edge = max(composed.width, composed.height)
        if long_edge > cap:
            scale = cap / long_edge
            composed = composed.resize(
                (max(1, round(composed.width * scale)),
                 max(1, round(composed.height * scale))),
                Image.LANCZOS)
        buf = BytesIO()
        composed.save(buf, format="WEBP", quality=_WEBP_QUALITY,
                      method=_WEBP_METHOD)
        data = buf.getvalue()
        if not data:
            return None, f"photos: {role} WebP vide"
        return SpeciesPhoto(
            role=role, webp=data, width=composed.width, height=composed.height,
            src_xrefs=tuple(group["xrefs"])), None
    except Exception as e:  # pragma: no cover - robustesse encodage
        return None, f"photos: encodage {role} échoué: {e}"


def extract_photos(pdf_path: Path) -> tuple[list[SpeciesPhoto], list[str]]:
    """Extrait la principale + les détails d'une fiche → WebP. Nécessite Pillow.

    Retour : `([principale, détails...], warnings)`, principale en tête (n=0),
    détails ordonnés haut→bas. Aucune photo détectée / PDF illisible → `([],
    [warning])`, jamais d'exception (façon `extract_all`). L'absence de Pillow
    est un défaut d'environnement (SystemExit clair via `_require_pillow`), pas
    une extraction ratée.
    """
    _require_fitz()
    _require_pillow()
    warnings: list[str] = []
    try:
        doc = fitz.open(pdf_path)
    except Exception as e:  # pragma: no cover - PDF illisible
        return [], [f"photos: ouverture échouée: {e}"]
    try:
        page_w = float(doc[0].rect.width)
        inventory, saw_placeholder = _page0_inventory(doc)
        principal_group, detail_groups = select_photos(inventory, page_w)
        if principal_group is None:
            # Aucune candidate : si un placeholder « Photos à venir » a été écarté,
            # c'est qu'il tenait lieu de seule photo (les logos d'en-tête/pied ne
            # sont jamais candidats). Warning explicite (« ne jamais inventer »).
            if saw_placeholder:
                return [], ["photos: seul le placeholder « Photos à venir » présent"]
            return [], ["photos: aucune principale détectée"]
        principal, w = _encode_photo(doc, principal_group, _CAP_PRINCIPAL)
        if w:
            warnings.append(w)
        if principal is None:
            return [], warnings
        photos = [principal]
        for g in detail_groups:
            sp, w = _encode_photo(doc, g, _CAP_DETAIL)
            if w:
                warnings.append(w)
            if sp is not None:
                photos.append(sp)
        return photos, warnings
    except Exception as e:  # pragma: no cover - page 0 corrompue / 0 page
        # Contrat « jamais d'exception » : un PDF à 0 page (doc[0] → IndexError)
        # ou une page pathologique ne doit pas casser tout le build (cf.
        # `extract_all`). Échec silencieux → warning, jamais de crash.
        return [], [f"photos: extraction échouée: {e}"]
    finally:
        doc.close()


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
# Overrides manuels des fiches à PDF rasterisé
# ---------------------------------------------------------------------------
# Les 6 fiches du template récent aplati en image (Tilia ×4, Fagus, Betula
# nigra) n'ont AUCUNE couche texte : ni le descriptif, ni « L'essence à Paris »,
# ni les services, ni les puces « À RETENIR » ne sont extractibles (pas d'OCR).
# Leurs 15 champs textuels ont été transcrits à la main puis double-vérifiés
# (cf. `tools/essence-overrides.json`, clé `_meta`). La fusion COMPLÈTE les
# champs manquants sans jamais écraser une valeur extraite : les calendriers
# flor/fruct de ces fiches (obtenus par le fallback couleur) restent intacts.

# Fichier d'override versionné, à côté de ce module.
ESSENCE_OVERRIDES_PATH = ROOT / "tools" / "essence-overrides.json"

# Note de provenance ajoutée en tête des warnings d'une fiche complétée, pour
# que la préviz reste honnête (champs présents mais issus d'une transcription
# manuelle, pas d'une extraction géométrique).
_OVERRIDE_PROVENANCE_WARNING = (
    "champs textuels complétés par tools/essence-overrides.json (PDF rasterisé)"
)


def merge_override(extras: EssenceExtras, override: dict) -> bool:
    """Complète EN PLACE les champs None/vides de `extras` depuis `override`.

    Fonction PURE et testable (aucune I/O). Contrat :
      - ne remplace JAMAIS une valeur déjà extraite (champ présent = intouché) ;
      - ignore les clés `_meta` et `nom_latin` de l'override ;
      - traite un dict/liste vide (`iddesc`, `svc`, `atouts`, `limites`) comme
        « vide » → éligible au remplissage (miroir de « champ vide = clé omise ») ;
      - si au moins un champ est complété, requalifie les warnings désormais
        obsolètes (« champs textuels non extraits », « ancres Atouts/Limites
        introuvables ») et ajoute une note de provenance en tête.

    Retourne True si au moins un champ a été complété.
    """
    filled_text = False       # champs descriptif / paris / svc
    filled_aretenir = False   # atouts / limites

    for key in ("fam", "haut", "env", "croiss", "long", "paris"):
        if override.get(key) is not None and getattr(extras, key) is None:
            setattr(extras, key, override[key])
            filled_text = True
    for key in ("iddesc", "svc"):
        if override.get(key) and not getattr(extras, key):
            setattr(extras, key, dict(override[key]))
            filled_text = True
    if override.get("atouts") and not extras.atouts:
        extras.atouts = list(override["atouts"])
        filled_aretenir = True
    if override.get("limites") and not extras.limites:
        extras.limites = list(override["limites"])
        filled_aretenir = True

    if not (filled_text or filled_aretenir):
        return False

    kept: list[str] = []
    for w in extras.warnings:
        if filled_text and "champs textuels non extraits" in w:
            continue
        if filled_aretenir and "ancres Atouts/Limites introuvables" in w:
            continue
        # Ceinture + bretelles : requalifie toute mention résiduelle.
        kept.append(w.replace("ancien template", "PDF rasterisé"))
    kept.insert(0, _OVERRIDE_PROVENANCE_WARNING)
    extras.warnings = kept
    return True


def apply_essence_overrides(
    extras_by_pdf_id: dict, overrides: dict | None = None
) -> None:
    """Applique les overrides sur les `EssenceExtras` du corpus (en place).

    `overrides` : dict `{pdf_id: {...}}` déjà chargé ; si None, lu depuis
    `ESSENCE_OVERRIDES_PATH` (no-op silencieux si le fichier est absent). La clé
    `_meta` est ignorée. Un pdf_id inconnu du corpus est logué (warning) sans
    faire échouer le build.
    """
    if overrides is None:
        if not ESSENCE_OVERRIDES_PATH.exists():
            return
        with ESSENCE_OVERRIDES_PATH.open(encoding="utf-8") as f:
            overrides = json.load(f)

    n_filled = 0
    unknown: list[str] = []
    for pdf_id, override in overrides.items():
        if pdf_id == "_meta":
            continue
        extras = extras_by_pdf_id.get(pdf_id)
        if extras is None:
            unknown.append(pdf_id)
            continue
        if merge_override(extras, override):
            n_filled += 1

    suffix = f", {len(unknown)} pdf_id inconnu(s) ignoré(s)" if unknown else ""
    print(f"[ovr ] {n_filled} fiche(s) complétée(s) via "
          f"{ESSENCE_OVERRIDES_PATH.name}{suffix}")
    for pid in unknown:
        print(f"[ovr ]   pdf_id inconnu : {pid}")


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


def _dump_photos(target: str) -> None:
    """Debug photos : liste les WebP sélectionnés (rôle, dims, octets, xrefs)."""
    _require_fitz()
    _require_pillow()
    p = Path(target)
    path = p if p.exists() else PDF_CACHE_DIR / f"{target}.pdf"
    if not path.exists():
        raise SystemExit(f"PDF introuvable : {path}")
    photos, warnings = extract_photos(path)
    print(f"=== {path.name} : {len(photos)} photo(s) ===")
    for i, ph in enumerate(photos):
        print(f"  [{i}] {ph.role:9} {ph.width}x{ph.height}  "
              f"{len(ph.webp)} o  xrefs={ph.src_xrefs}")
    if warnings:
        print("  warnings :")
        for w in warnings:
            print(f"    ! {w}")


def main(argv: list[str]) -> int:
    if len(argv) == 3 and argv[1] == "--dump":
        _dump(argv[2])
        return 0
    if len(argv) == 3 and argv[1] == "--photos":
        _dump_photos(argv[2])
        return 0
    print("usage: python3 tools/essence_pdf.py --dump|--photos <pdf_id|chemin>",
          file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
