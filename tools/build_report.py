#!/usr/bin/env python3
"""Génère `docs/dataset/index.html` : rapport HTML self-contained de validation
de l'état du dataset après un build.

Lit les artefacts produits par `tools/build_dataset.py` :
- `app/src/main/assets/species-index.json` (entrées catalogue + nv finaux)
- `app/src/main/assets/species-info.json` (counts Paris par espèce)
- `app/src/main/assets/dataset-stats.json` (totaux globaux)
- `app/src/main/assets/genre-info.json` (fiches genre S8, optionnel — fallback
  sur agrégats dérivés de species-index si absent)
- `tools/_trace/vernacular-source.json` (sidecar : source nv par sk)

Sortie unique : un HTML autonome (~600-800 KB), sans CDN ni dépendance JS,
ouvrable dans un navigateur. Trois grandes sections : (1) vue d'ensemble,
(2) catalogue d'espèces (cas limites + liste complète), (3) catalogue de
genres ajouté par S8 (cas limites + liste complète des 200+ genres).

Usage : `python3 tools/build_report.py`
"""

from __future__ import annotations

import datetime
import html
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
SPECIES_INDEX = ASSETS_DIR / "species-index.json"
SPECIES_INFO = ASSETS_DIR / "species-info.json"
DATASET_STATS = ASSETS_DIR / "dataset-stats.json"
GENRE_INFO = ASSETS_DIR / "genre-info.json"
TRACE = ROOT / "tools" / "_trace" / "vernacular-source.json"
OUT_DIR = ROOT / "docs" / "dataset"
OUT_HTML = OUT_DIR / "index.html"

# Importer GENRE_FR depuis le pipeline pour dériver la couverture nomFr même
# sans `genre-info.json`. Garde le rapport informatif sur un repo fraîchement
# cloné (build asset pas encore lancé).
sys.path.insert(0, str(ROOT / "tools"))
try:
    from build_dataset import GENRE_FR
except ImportError:
    GENRE_FR = {}

# Doit rester aligné avec `compute_vernacular_and_pokedex` (build_dataset.py).
SOURCES_ORDERED = [
    "override",
    "p1843",
    "frtitle",
    "redirect",
    "summary_extract",
    "genre_fr",
    "construct_nc_unique",
    "construct_nc_disamb",
    "construct_binom",
]

SOURCE_LABELS = {
    "override": "Override manuel",
    "p1843": "Wikidata P1843",
    "frtitle": "Wikipédia frTitle",
    "redirect": "Wikipédia redirect",
    "summary_extract": "Wikipédia summary",
    "genre_fr": "GENRE_FR (zombie)",
    "construct_nc_unique": "Construit (nc unique)",
    "construct_nc_disamb": "Construit (nc + binôme)",
    "construct_binom": "Construit (binôme nu)",
}

# Pour les badges dans le tableau — couleurs sobres, hue par famille de qualité :
# vert/cyan = sources externes solides, orange = construit lisible, gris = binôme.
SOURCE_COLORS = {
    "override": "#7c3aed",
    "p1843": "#16a34a",
    "frtitle": "#2563eb",
    "redirect": "#0891b2",
    "summary_extract": "#0d9488",
    "genre_fr": "#ea580c",
    "construct_nc_unique": "#65a30d",
    "construct_nc_disamb": "#a16207",
    "construct_binom": "#6b7280",
}

# Cibles S6 (raise déjà câblé pour redondance ; les autres sont indicatives).
TARGET_NV_BINOMIAL_MAX = 50
TARGET_REDUNDANT_MAX = 0

NV_REDUNDANT_RE = re.compile(r"^(\S+)\s+(\S+)\s+\(\1\s+\2\)$")


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def git_short_hash() -> str:
    try:
        out = subprocess.check_output(
            ["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"],
            stderr=subprocess.DEVNULL, text=True,
        ).strip()
        return out or "n/a"
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "n/a"


def is_nv_equal_binomial(entry: dict) -> bool:
    nv = (entry.get("nv") or "").strip().casefold()
    binomial = f"{entry.get('g', '')} {entry.get('e', '')}".strip().casefold()
    return nv == binomial


def render_html(
    entries: list[dict],
    counts_by_sk: dict[int, int],
    trace_by_sk: dict[int, dict],
    stats: dict,
    genre_info: list[dict] | None,
) -> str:
    # --- Pré-calculs : espèces ---------------------------------------------
    by_source: dict[str, int] = {s: 0 for s in SOURCES_ORDERED}
    for sk in (e["i"] for e in entries):
        src = trace_by_sk.get(sk, {}).get("source", "construct")
        by_source[src] = by_source.get(src, 0) + 1

    nv_eq_binomial = [e for e in entries if is_nv_equal_binomial(e)]
    redundant = [e for e in entries if NV_REDUNDANT_RE.match(e.get("nv") or "")]
    zombies_no_genre_fr = [
        e for e in entries
        if e.get("u")
        and trace_by_sk.get(e["i"], {}).get("source") == "construct_binom"
    ]
    construct_binom = sorted(
        [
            e for e in entries
            if not e.get("u")
            and trace_by_sk.get(e["i"], {}).get("source") == "construct_binom"
        ],
        key=lambda e: -counts_by_sk.get(e["i"], 0),
    )
    construct_nc_disamb = sorted(
        [
            e for e in entries
            if not e.get("u")
            and trace_by_sk.get(e["i"], {}).get("source") == "construct_nc_disamb"
            and counts_by_sk.get(e["i"], 0) > 200
        ],
        key=lambda e: -counts_by_sk.get(e["i"], 0),
    )

    rows_data = sorted(
        entries, key=lambda e: -counts_by_sk.get(e["i"], 0),
    )

    # --- Pré-calculs : genres (S8) -----------------------------------------
    # On dérive un agrégat par genre depuis species-index. C'est la source
    # autoritative pour le routage de fiches genre côté app (`SpeciesIndex.
    # allGenres()`). `genre-info.json` apporte en plus le summary Wikipedia
    # et les stats agrégées au build (top espèces / top arr) — quand absent,
    # on rend la section avec ce qu'on a sous la main.
    by_genre: dict[str, dict] = {}
    for e in entries:
        g = e.get("g", "")
        if g == "Non spécifié":
            continue  # Cas dégénéré, exclu d'`allGenres()`.
        bucket = by_genre.setdefault(g, {
            "g": g,
            "count": 0,
            "speciesIdentified": 0,
            "spEntry": None,
            "topSk": None,
            "topCount": 0,
        })
        sk_count = counts_by_sk.get(e["i"], 0)
        bucket["count"] += sk_count
        if e.get("u"):
            bucket["spEntry"] = e
        else:
            if sk_count > 0:
                bucket["speciesIdentified"] += 1
            if sk_count > bucket["topCount"]:
                bucket["topCount"] = sk_count
                bucket["topSk"] = e

    # Fusionner avec genre-info.json si présent.
    info_by_genre: dict[str, dict] = {}
    if genre_info is not None:
        for gi in genre_info:
            info_by_genre[gi.get("g", "")] = gi

    genres_sorted = sorted(by_genre.values(), key=lambda b: -b["count"])

    n_genres = len(genres_sorted)
    n_only_unknown = sum(1 for b in genres_sorted if b["speciesIdentified"] == 0)
    n_with_fr = sum(1 for b in genres_sorted if GENRE_FR.get(b["g"]))
    n_with_summary = sum(
        1 for b in genres_sorted if (info_by_genre.get(b["g"]) or {}).get("summary")
    )

    genres_no_fr = sorted(
        [b for b in genres_sorted if not GENRE_FR.get(b["g"])],
        key=lambda b: -b["count"],
    )
    genres_only_unknown = [b for b in genres_sorted if b["speciesIdentified"] == 0]
    genres_no_summary_high_count = sorted(
        [
            b for b in genres_sorted
            if not (info_by_genre.get(b["g"]) or {}).get("summary")
            and b["count"] > 1000
        ],
        key=lambda b: -b["count"],
    ) if genre_info is not None else []

    # --- HTML ---------------------------------------------------------------
    generated = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    git_ref = git_short_hash()

    css = """
    :root {
      --bg: #fafaf8;
      --fg: #1f2937;
      --muted: #6b7280;
      --border: #e5e7eb;
      --card-bg: #ffffff;
      --warn: #b45309;
      --ok: #15803d;
      --bad: #b91c1c;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0; padding: 24px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Helvetica, Arial, sans-serif;
      background: var(--bg); color: var(--fg);
      line-height: 1.45;
    }
    h1 { margin: 0 0 4px; font-size: 26px; }
    h2 { margin: 32px 0 12px; font-size: 18px; border-bottom: 1px solid var(--border); padding-bottom: 6px; }
    .meta { color: var(--muted); font-size: 13px; margin-bottom: 16px; }
    .intro {
      background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 8px;
      padding: 12px 16px; margin-bottom: 18px; font-size: 14px;
    }
    .intro strong { color: #065f46; }
    .intro code { background: #d1fae5; padding: 1px 6px; border-radius: 3px;
      font-family: ui-monospace, monospace; font-size: 12.5px; }
    .grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px; margin-bottom: 18px;
    }
    .card {
      background: var(--card-bg); border: 1px solid var(--border);
      border-radius: 8px; padding: 14px 16px;
    }
    .card .label { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
    .card .value { font-size: 26px; font-weight: 600; margin-top: 4px; }
    .card .sub { font-size: 12px; color: var(--muted); margin-top: 4px; }
    .stack-bar {
      display: flex; height: 18px; border-radius: 4px; overflow: hidden;
      border: 1px solid var(--border); margin: 8px 0 4px;
    }
    .stack-bar > div { display: flex; align-items: center; justify-content: center; color: white; font-size: 11px; font-weight: 500; }
    .legend { display: flex; flex-wrap: wrap; gap: 12px; font-size: 12px; }
    .legend > span { display: inline-flex; align-items: center; gap: 6px; }
    .swatch { width: 12px; height: 12px; border-radius: 2px; display: inline-block; }
    .check {
      background: var(--card-bg); border: 1px solid var(--border);
      border-radius: 8px; padding: 12px 16px; margin-bottom: 10px;
    }
    .check .head { display: flex; justify-content: space-between; align-items: baseline; }
    .check .name { font-weight: 600; }
    .check .count { font-size: 22px; font-weight: 700; }
    .check.ok .count { color: var(--ok); }
    .check.warn .count { color: var(--warn); }
    .check.bad .count { color: var(--bad); }
    .check .desc { color: var(--muted); font-size: 13px; margin-top: 4px; }
    .check details { margin-top: 8px; font-size: 13px; }
    .check details summary { cursor: pointer; color: var(--muted); }
    .check ul.skchips { list-style: none; padding: 8px 0 0; margin: 0; display: flex; flex-wrap: wrap; gap: 6px; }
    .check ul.skchips li a {
      display: inline-block; padding: 2px 8px; background: #f3f4f6;
      border-radius: 3px; font-size: 12px; color: var(--fg); text-decoration: none;
    }
    .check ul.skchips li a:hover { background: #e5e7eb; }
    .controls { display: flex; gap: 12px; margin-bottom: 8px; align-items: center; flex-wrap: wrap; }
    .controls input {
      padding: 6px 10px; border: 1px solid var(--border); border-radius: 6px;
      font-size: 14px; min-width: 240px;
    }
    .controls .total { color: var(--muted); font-size: 13px; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    thead th {
      position: sticky; top: 0; background: var(--bg); border-bottom: 2px solid var(--border);
      text-align: left; padding: 8px 6px; font-weight: 600; cursor: pointer; user-select: none;
    }
    thead th:hover { background: #f3f4f6; }
    thead th.sorted::after { content: ' ↓'; color: var(--muted); }
    thead th.sorted-asc::after { content: ' ↑'; color: var(--muted); }
    tbody tr:nth-child(even) { background: #f9fafb; }
    tbody tr:hover { background: #fff7ed; }
    tbody tr.match-redundant { background: #fee2e2; }
    tbody tr.match-binomial { background: #fef3c7; }
    tbody tr.zombie { color: #57534e; font-style: italic; }
    td { padding: 6px; border-bottom: 1px solid var(--border); }
    td.num { text-align: right; font-variant-numeric: tabular-nums; }
    td.latin { font-family: ui-monospace, monospace; font-size: 12.5px; color: #525b6b; }
    td.nv { font-weight: 500; }
    td.source { white-space: nowrap; }
    .badge {
      display: inline-block; padding: 1px 7px; border-radius: 3px;
      color: white; font-size: 11px; font-weight: 500;
    }
    .u-tag {
      display: inline-block; padding: 0 6px; border-radius: 3px;
      background: #f3f4f6; color: var(--muted); font-size: 10.5px; margin-left: 6px;
      font-style: normal;
    }
    """

    # --- Header + overview --------------------------------------------------
    out: list[str] = []
    out.append("<!doctype html>")
    out.append('<html lang="fr"><head><meta charset="utf-8">')
    out.append("<title>Catalogue d'espèces — état du dataset</title>")
    out.append(f"<style>{css}</style>")
    out.append("</head><body>")
    out.append("<h1>Catalogue d'espèces et de genres — état du dataset</h1>")
    out.append(
        f'<div class="meta">Généré le {generated} · '
        f'commit <code>{git_ref}</code> · '
        f'pipeline <code>tools/build_dataset.py</code> + <code>tools/build_report.py</code></div>'
    )

    # Note d'intro S8 : explique la coexistence catalogue espèces / catalogue
    # genres et la sémantique des entrées `u: true`.
    out.append(
        '<div class="intro">'
        '<strong>Sprint 8 — fiches genre dédiées.</strong> '
        "Le catalogue catalogue affiché en UI ne montre plus que les "
        "<strong>espèces identifiées</strong>. Les entrées <code>(G, sp.)</code> "
        "(flag <code>u: true</code>) ne sont plus rendues comme cards d'espèce ; "
        "elles alimentent désormais une <strong>fiche genre dédiée</strong> "
        "(galerie photos sp. + carte filtrée + résumé Wikipedia du genre). Une "
        "capture <code>Quercus sp.</code> n'incrémente plus aucun compteur "
        "« espèce capturée » — voir les sections espèces puis genres ci-dessous."
        '</div>'
    )

    # Overview cards.
    out.append('<h2>Vue d\'ensemble</h2>')
    out.append('<div class="grid">')
    out.append(_card("Total arbres", _fmt(stats.get("totalArbres", 0))))
    out.append(_card(
        "Espèces identifiées",
        _fmt(stats.get("totalEspecesIdentifiees", 0)),
        sub="affichées dans le Catalogue (hors `u: true`)",
    ))
    out.append(_card(
        "Genres",
        _fmt(n_genres),
        sub=f"{n_only_unknown} only-unknown · couverture FR {n_with_fr}/{n_genres}",
    ))
    out.append(_card("Remarquables", _fmt(stats.get("totalRemarquables", 0))))
    out.append("</div>")

    # Source répartition.
    total_sources = sum(by_source.values()) or 1
    out.append('<h2>Répartition des nv par source de cascade</h2>')
    out.append('<div class="stack-bar">')
    for src in SOURCES_ORDERED:
        n = by_source.get(src, 0)
        if n == 0:
            continue
        pct = 100.0 * n / total_sources
        color = SOURCE_COLORS[src]
        label = f"{n}" if pct >= 5 else ""
        out.append(
            f'<div title="{html.escape(SOURCE_LABELS[src])}: {n} ({pct:.1f}%)" '
            f'style="width: {pct:.2f}%; background: {color};">{label}</div>'
        )
    out.append("</div>")
    out.append('<div class="legend">')
    for src in SOURCES_ORDERED:
        n = by_source.get(src, 0)
        color = SOURCE_COLORS[src]
        out.append(
            f'<span><span class="swatch" style="background: {color};"></span>'
            f'{html.escape(SOURCE_LABELS[src])} <strong>{n}</strong></span>'
        )
    out.append("</div>")

    # === BLOC ESPÈCES =======================================================
    out.append('<h2>Espèces — contrôles de cas limites</h2>')

    out.append(_check_panel(
        title=f"nv == binôme latin nu (cible ≤ {TARGET_NV_BINOMIAL_MAX})",
        items=nv_eq_binomial,
        threshold=TARGET_NV_BINOMIAL_MAX,
        desc=("Espèces identifiées dont le nv affiché en UI est juste « Genre espece ». "
              "Candidates à un override dans VERNACULAR_OVERRIDES ou à étendre "
              "le mapping GENRE_FR / les redirects. Note S8 : ces espèces "
              "restent accessibles via la fiche genre correspondante quand "
              "elles ne sont pas capturées."),
    ))

    out.append(_check_panel(
        title=f"Redondances `{{g}} {{e}} ({{g}} {{e}})` (cible = {TARGET_REDUNDANT_MAX})",
        items=redundant,
        threshold=TARGET_REDUNDANT_MAX,
        desc=("Cas pathologiques type « Aria edulis (Aria edulis) ». "
              "Le verify lève sur ces cas — si la liste est non vide ici c'est "
              "que ce rapport a été généré sur un build pré-S6."),
    ))

    out.append(_check_panel(
        title="Entrées sp. sans GENRE_FR mappé (titre fiche genre en latin)",
        items=zombies_no_genre_fr,
        threshold=0,
        desc=("Entrées `u: true` dont le titre de fiche genre tombe sur le "
              "nom latin du genre (ex. « Wollemia » au lieu de « Pin de "
              "Wollemi »). Compléter `GENRE_FR` dans `tools/build_dataset.py` "
              "pour les afficher avec un nom français court. Note S8 : ces "
              "entrées ne sont plus rendues comme cards d'espèce — la "
              "section « Catalogue des genres » ci-dessous est l'angle de "
              "lecture pertinent."),
    ))

    out.append(_check_panel(
        title="Construits binôme nu (toutes espèces identifiées)",
        items=construct_binom,
        threshold=50,
        desc=("Espèces identifiées sans `nc` CSV ni nv issu de Wikidata/"
              "Wikipédia/redirects/summary. Le pire cas qualité — bons "
              "candidats `VERNACULAR_OVERRIDES`."),
        with_count=True,
        counts_by_sk=counts_by_sk,
    ))

    out.append(_check_panel(
        title="Construits `nc + (I. e)` sur > 200 captures (candidats summary/override)",
        items=construct_nc_disamb,
        threshold=20,
        desc=("Cas où le `nc` du CSV est partagé entre plusieurs sks → on "
              "doit suffixer `(I. epithète)` faute de mieux. Si Wikipédia "
              "propose un nv plus précis, c'est l'occasion d'overrider."),
        with_count=True,
        counts_by_sk=counts_by_sk,
    ))

    # --- Liste complète des espèces ----------------------------------------
    out.append('<h2>Espèces — liste complète</h2>')
    out.append(
        '<div class="controls">'
        '<input type="search" id="filter" placeholder="Rechercher (genre, espèce, nv, nc)…">'
        f'<span class="total"><span id="visible-count">{len(rows_data)}</span> / {len(rows_data)} entrées</span>'
        '</div>'
    )
    out.append('<table id="catalogue"><thead><tr>')
    for col, key in [
        ("count", "count"),
        ("n°", "n"),
        ("sk", "i"),
        ("genre", "g"),
        ("espèce", "e"),
        ("nc (CSV)", "nc"),
        ("nv (catalogue)", "nv"),
        ("source", "source"),
        ("u", "u"),
    ]:
        sorted_cls = ' class="sorted"' if key == "count" else ""
        out.append(f'<th data-key="{key}"{sorted_cls}>{col}</th>')
    out.append("</tr></thead><tbody>")

    for e in rows_data:
        sk = e["i"]
        count = counts_by_sk.get(sk, 0)
        n_pokedex = e.get("n", "")
        nc = e.get("nc") or ""
        nv = e.get("nv") or ""
        source = trace_by_sk.get(sk, {}).get("source", "construct")
        is_zombie = bool(e.get("u"))
        cls_parts = []
        if is_nv_equal_binomial(e):
            cls_parts.append("match-binomial")
        if NV_REDUNDANT_RE.match(nv):
            cls_parts.append("match-redundant")
        if is_zombie:
            cls_parts.append("zombie")
        cls = f' class="{" ".join(cls_parts)}"' if cls_parts else ""
        badge_color = SOURCE_COLORS.get(source, "#6b7280")
        u_tag = '<span class="u-tag">u</span>' if is_zombie else ""
        out.append(
            f'<tr{cls} id="sk-{sk}">'
            f'<td class="num">{count}</td>'
            f'<td class="num">{n_pokedex}</td>'
            f'<td class="num">{sk}</td>'
            f'<td class="latin">{html.escape(e.get("g", ""))}</td>'
            f'<td class="latin">{html.escape(e.get("e", ""))}</td>'
            f'<td>{html.escape(nc)}</td>'
            f'<td class="nv">{html.escape(nv)}{u_tag}</td>'
            f'<td class="source"><span class="badge" style="background:{badge_color};">{html.escape(SOURCE_LABELS.get(source, source))}</span></td>'
            f'<td>{"✓" if is_zombie else ""}</td>'
            f'</tr>'
        )
    out.append("</tbody></table>")

    # === BLOC GENRES (S8) ===================================================
    out.append('<h2>Genres — contrôles de cas limites</h2>')
    if genre_info is None:
        out.append(
            '<div class="check warn">'
            '<div class="head"><div class="name">'
            '<code>genre-info.json</code> absent — section dégradée'
            '</div><div class="count">⚠</div></div>'
            '<div class="desc">Le pipeline S8 n\'a pas encore été lancé '
            '(<code>python3 tools/build_dataset.py</code>). Les agrégats '
            'genre sont dérivés de <code>species-index.json</code> ; les '
            'colonnes Wikipedia (résumé / titre WP) ne sont pas '
            'disponibles. Les contrôles ci-dessous sur la couverture WP '
            'sont skippés.</div></div>'
        )

    out.append(_check_panel_genre(
        title="Genres sans nom français mappé (titre fiche en latin)",
        items=genres_no_fr,
        threshold=40,
        desc=("Genres dont le `nomFr` tombe sur le nom latin (ex. « Wollemia »). "
              "Compléter `GENRE_FR` dans `tools/build_dataset.py` pour donner "
              "un titre français court à la fiche genre. Beaucoup d'exotiques "
              "rares acceptent légitimement le fallback latin (sans nom FR usuel "
              "attesté) — la cible est la couverture des genres > 100 individus."),
    ))

    out.append(_check_panel_genre(
        title="Genres only-unknown (uniquement des entrées sp.)",
        items=genres_only_unknown,
        threshold=5,
        desc=("Genres qui n'ont aucune espèce identifiée dans le CSV OpenData "
              "— uniquement des entrées sp. Trois cas attendus : Genista, "
              "Vitex, Ziziphus. Si la liste explose, vérifier qu'on n'a pas "
              "perdu un parsing d'espèce identifiée côté pipeline."),
    ))

    if genre_info is not None:
        out.append(_check_panel_genre(
            title="Genres > 1000 individus sans summary Wikipedia FR",
            items=genres_no_summary_high_count,
            threshold=10,
            desc=("Genres très représentés à Paris pour lesquels le fetch "
                  "Wikipedia FR du genre n'a pas trouvé d'article publiable "
                  "(miss définitif cached). Vérifier le titre tenté côté "
                  "pipeline ; un override manuel `GENRE_FR` mal calibré peut "
                  "faire viser un titre inexistant."),
        ))

    out.append('<h2>Genres — liste complète (catalogue de fiches genre)</h2>')
    out.append(
        '<div class="controls">'
        '<input type="search" id="genre-filter" placeholder="Rechercher (genre latin, nom FR)…">'
        f'<span class="total"><span id="genre-visible-count">{n_genres}</span> / {n_genres} genres</span>'
        '</div>'
    )
    out.append('<table id="genre-catalogue"><thead><tr>')
    for col, key in [
        ("count", "count"),
        ("genre (latin)", "g"),
        ("nom FR", "fr"),
        ("# espèces id.", "speciesIdentified"),
        ("entrée sp. ?", "hasSp"),
        ("WP genre", "wp"),
        ("top espèce", "topNv"),
    ]:
        sorted_cls = ' class="sorted"' if key == "count" else ""
        out.append(f'<th data-key="{key}"{sorted_cls}>{col}</th>')
    out.append("</tr></thead><tbody>")

    for b in genres_sorted:
        g = b["g"]
        fr = GENRE_FR.get(g) or ""
        info = info_by_genre.get(g) or {}
        wp_title = info.get("wp") or ""
        has_summary = bool(info.get("summary"))
        has_sp = b["spEntry"] is not None
        top_sk_entry = b["topSk"]
        top_label = ""
        if top_sk_entry is not None:
            top_label = f"{top_sk_entry.get('nv') or top_sk_entry.get('e')} ({_fmt(b['topCount'])})"
        cls_parts = []
        if not fr:
            cls_parts.append("match-binomial")
        if b["speciesIdentified"] == 0:
            cls_parts.append("zombie")
        cls = f' class="{" ".join(cls_parts)}"' if cls_parts else ""
        wp_cell = (
            f'<a href="https://fr.wikipedia.org/wiki/{html.escape(wp_title.replace(" ", "_"))}" target="_blank">{html.escape(wp_title)}</a>'
            if wp_title else "—"
        )
        if genre_info is not None and has_summary:
            wp_cell = f'<span title="summary disponible">✓ </span>' + wp_cell
        out.append(
            f'<tr{cls}>'
            f'<td class="num">{_fmt(b["count"])}</td>'
            f'<td class="latin">{html.escape(g)}</td>'
            f'<td class="nv">{html.escape(fr) if fr else "—"}</td>'
            f'<td class="num">{b["speciesIdentified"]}</td>'
            f'<td>{"sp." if has_sp else ""}</td>'
            f'<td>{wp_cell}</td>'
            f'<td>{html.escape(top_label)}</td>'
            f'</tr>'
        )
    out.append("</tbody></table>")

    # --- JS : tri + filtre --------------------------------------------------
    # Factorisé pour les 2 tables (catalogue espèces + catalogue genres).
    # Les clés numériques sont déclarées dans `numericKeys` ; le reste est
    # comparé en lexicographique normalisé fr.
    js = """
    (function() {
      const numericKeys = new Set(['count', 'n', 'i', 'speciesIdentified']);

      function attachTable(tableId, filterId, counterId) {
        const table = document.getElementById(tableId);
        if (!table) return;
        const tbody = table.tBodies[0];
        const rows = Array.from(tbody.rows);
        const headers = Array.from(table.tHead.rows[0].cells);
        const visibleCounter = document.getElementById(counterId);
        const filterInput = document.getElementById(filterId);

        let currentKey = 'count';
        let currentDesc = true;

        function valueFor(row, key) {
          const idx = headers.findIndex(h => h.dataset.key === key);
          if (idx < 0) return '';
          const cell = row.cells[idx];
          const text = cell.textContent.trim();
          if (numericKeys.has(key)) {
            const n = parseInt(text.replace(/\\s/g, ''), 10);
            return isNaN(n) ? -1 : n;
          }
          return text.toLocaleLowerCase('fr');
        }

        function sortBy(key) {
          if (key === currentKey) {
            currentDesc = !currentDesc;
          } else {
            currentKey = key;
            currentDesc = numericKeys.has(key);
          }
          const sorted = rows.slice().sort((a, b) => {
            const va = valueFor(a, key), vb = valueFor(b, key);
            if (va < vb) return currentDesc ? 1 : -1;
            if (va > vb) return currentDesc ? -1 : 1;
            return 0;
          });
          tbody.replaceChildren(...sorted);
          headers.forEach(h => {
            h.classList.remove('sorted', 'sorted-asc');
            if (h.dataset.key === key) {
              h.classList.add(currentDesc ? 'sorted' : 'sorted-asc');
            }
          });
        }

        headers.forEach(h => {
          h.addEventListener('click', () => sortBy(h.dataset.key));
        });

        if (filterInput) {
          filterInput.addEventListener('input', () => {
            const q = filterInput.value.trim().toLocaleLowerCase('fr');
            let visible = 0;
            rows.forEach(r => {
              if (!q) { r.style.display = ''; visible++; return; }
              const txt = r.textContent.toLocaleLowerCase('fr');
              if (txt.includes(q)) { r.style.display = ''; visible++; }
              else { r.style.display = 'none'; }
            });
            if (visibleCounter) visibleCounter.textContent = visible;
          });
        }
      }

      attachTable('catalogue', 'filter', 'visible-count');
      attachTable('genre-catalogue', 'genre-filter', 'genre-visible-count');

      // Scroll-to-row depuis les chips des panneaux cas limites espèces.
      document.querySelectorAll('a[href^="#sk-"]').forEach(a => {
        a.addEventListener('click', () => {
          const target = document.querySelector(a.getAttribute('href'));
          if (target) {
            target.style.outline = '2px solid #f97316';
            setTimeout(() => target.style.outline = '', 1800);
          }
        });
      });
    })();
    """
    out.append(f"<script>{js}</script>")
    out.append("</body></html>")
    return "".join(out)


def _card(label: str, value: str, sub: str = "") -> str:
    sub_html = f'<div class="sub">{html.escape(sub)}</div>' if sub else ""
    return (
        f'<div class="card"><div class="label">{html.escape(label)}</div>'
        f'<div class="value">{html.escape(value)}</div>{sub_html}</div>'
    )


def _check_panel(
    title: str,
    items: list[dict],
    threshold: int,
    desc: str,
    with_count: bool = False,
    counts_by_sk: dict | None = None,
) -> str:
    n = len(items)
    cls = "ok" if n <= threshold else ("warn" if n <= threshold * 3 + 5 else "bad")
    out = [
        f'<div class="check {cls}">',
        f'<div class="head"><div class="name">{html.escape(title)}</div>',
        f'<div class="count">{n}</div></div>',
        f'<div class="desc">{html.escape(desc)}</div>',
    ]
    if items:
        out.append("<details><summary>Voir les entrées concernées</summary>")
        out.append('<ul class="skchips">')
        # Limiter à 80 chips pour rester lisible. La table complète reste accessible.
        for e in items[:80]:
            sk = e["i"]
            label = f"{e.get('g', '?')} {e.get('e', '?')}"
            if with_count and counts_by_sk:
                label = f"{label} · {counts_by_sk.get(sk, 0)}"
            out.append(
                f'<li><a href="#sk-{sk}">{html.escape(label)}</a></li>'
            )
        if len(items) > 80:
            out.append(f'<li>… +{len(items) - 80}</li>')
        out.append("</ul></details>")
    out.append("</div>")
    return "".join(out)


def _check_panel_genre(
    title: str,
    items: list[dict],
    threshold: int,
    desc: str,
) -> str:
    """Variante de `_check_panel` pour les genres : items sont des buckets
    `{g, count, speciesIdentified, ...}` au lieu d'entrées species. Pas de
    chip cliquable (pas de table à scroller — tout le tableau genres tient
    déjà dans la même page)."""
    n = len(items)
    cls = "ok" if n <= threshold else ("warn" if n <= threshold * 3 + 5 else "bad")
    out = [
        f'<div class="check {cls}">',
        f'<div class="head"><div class="name">{html.escape(title)}</div>',
        f'<div class="count">{n}</div></div>',
        f'<div class="desc">{html.escape(desc)}</div>',
    ]
    if items:
        out.append("<details><summary>Voir les genres concernés</summary>")
        out.append('<ul class="skchips">')
        for b in items[:80]:
            label = f"{b['g']} · {_fmt(b['count'])}"
            out.append(f'<li><a href="#">{html.escape(label)}</a></li>')
        if len(items) > 80:
            out.append(f'<li>… +{len(items) - 80}</li>')
        out.append("</ul></details>")
    out.append("</div>")
    return "".join(out)


def _fmt(n: int) -> str:
    return f"{n:,}".replace(",", " ")


def main() -> int:
    print(f"[rep ] lecture {SPECIES_INDEX.relative_to(ROOT)}")
    entries = load_json(SPECIES_INDEX)
    print(f"[rep ] lecture {SPECIES_INFO.relative_to(ROOT)}")
    info = load_json(SPECIES_INFO)
    print(f"[rep ] lecture {DATASET_STATS.relative_to(ROOT)}")
    stats = load_json(DATASET_STATS)
    if not TRACE.exists():
        print(
            f"[err] sidecar trace manquant : {TRACE}\n"
            f"      Lancer `python3 tools/build_dataset.py` d'abord pour le générer.",
        )
        return 1
    print(f"[rep ] lecture {TRACE.relative_to(ROOT)}")
    trace = load_json(TRACE)

    genre_info: list[dict] | None = None
    if GENRE_INFO.exists():
        print(f"[rep ] lecture {GENRE_INFO.relative_to(ROOT)}")
        genre_info = load_json(GENRE_INFO)
    else:
        print(f"[warn] {GENRE_INFO.relative_to(ROOT)} absent — section genres dégradée")

    counts_by_sk = {e["i"]: e.get("stats", {}).get("count", 0) for e in info}
    trace_by_sk = {t["sk"]: t for t in trace}

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"[rep ] {len(entries)} entrées, sources tracées pour {len(trace_by_sk)} sk")
    htmlout = render_html(entries, counts_by_sk, trace_by_sk, stats, genre_info)
    OUT_HTML.write_text(htmlout, encoding="utf-8")
    size_kb = OUT_HTML.stat().st_size // 1024
    print(f"[ok ] → {OUT_HTML.relative_to(ROOT)} ({size_kb} Ko)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
