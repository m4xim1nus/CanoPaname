#!/usr/bin/env python3
"""Génère `docs/essences/index.html` : rapport HTML de validation des extractions
PDF fiches-essences (sprint S4 — cycle Herbier).

Lit le sidecar produit par `tools/build_dataset.py` :
- `tools/_trace/essence-extras.json` : `{pdf_id: {nom_latin, filename, url, flor,
  fruct, atouts, limites, warnings, matched}}` (`flor`/`fruct` = bitfield 12 bits
  int ou null, **bit 0 = janvier** ; `atouts`/`limites` listes ; `matched` bool).

Ce script NE re-parse PAS les PDF pour les attributs (pas d'appel à
`extract_all`/`extract_extras`) et ne fait AUCUN réseau. Il importe `essence_pdf`
uniquement pour `report_clips()` — il ouvre chaque PDF du cache local
(`tools/.essences-pdf-cache/{pdf_id}.pdf`) et en extrait deux crops image (zone
calendriers page 0, zone « À RETENIR ») rendus en JPEG base64 inline pour la
comparaison visuelle case à case avec le mini-calendrier reconstruit des bitfields.

Sortie unique : un HTML autonome (sans CDN ni dépendance JS), < 6 Mo à 200 fiches
(crops JPEG q70 dpi 80). PDF absent du cache ou zone introuvable → placeholder
texte, jamais d'exception.

Ce n'est **pas** un artefact de build consommé par l'app : c'est un outil de
relecture, à régénérer après `python3 tools/build_dataset.py`.

Usage :
    python3 tools/build_essences_preview.py
    python3 tools/build_essences_preview.py --trace <sidecar.json> --out <index.html>
"""
from __future__ import annotations

import argparse
import base64
import datetime
import html
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TRACE = ROOT / "tools" / "_trace" / "essence-extras.json"
DEFAULT_OUT = ROOT / "docs" / "essences" / "index.html"
PDF_CACHE_DIR = ROOT / "tools" / ".essences-pdf-cache"

# Import de essence_pdf pour report_clips() + fitz. Purement build-time.
sys.path.insert(0, str(ROOT / "tools"))
import essence_pdf  # noqa: E402

MONTHS = list("JFMAMJJASOND")
CROP_DPI = 80          # 100 déborde le budget 6 Mo à 200 fiches ; 80 reste lisible.
CROP_JPEG_QUALITY = 60  # 70 pousse le total à ~6,1 Mo ; 60 → ~5,4 Mo, texte net.


# ---------------------------------------------------------------------------
# Crops PDF → base64 (aucun réseau, cache local seulement)
# ---------------------------------------------------------------------------

def crops_for(pdf_id: str) -> dict[str, str | None]:
    """Rend les deux zones (calendars, aretenir) du PDF en JPEG base64.

    Valeur None si le PDF est absent du cache ou la zone introuvable. Toute
    exception (PDF corrompu, fitz…) → dict de None, jamais de crash.
    """
    out: dict[str, str | None] = {"calendars": None, "aretenir": None}
    path = PDF_CACHE_DIR / f"{pdf_id}.pdf"
    if not path.exists():
        return out
    try:
        with essence_pdf.fitz.open(path) as doc:
            clips = essence_pdf.report_clips(doc)
            for which, (pno, rect) in clips.items():
                page = doc[pno]
                # Léger padding pour ne pas rogner les glyphes en bordure.
                r = essence_pdf.fitz.Rect(rect) + (-4, -4, 4, 4)
                pix = page.get_pixmap(clip=r, dpi=CROP_DPI)
                data = pix.tobytes("jpeg", jpg_quality=CROP_JPEG_QUALITY)
                out[which] = base64.b64encode(data).decode("ascii")
    except Exception:  # PDF illisible, clip hors page… → placeholder.
        return {"calendars": None, "aretenir": None}
    return out


def _img_or_placeholder(b64: str | None, alt: str) -> str:
    if b64:
        return (
            f'<img class="crop" src="data:image/jpeg;base64,{b64}" '
            f'alt="{html.escape(alt)}" loading="lazy">'
        )
    return f'<div class="crop-missing">{html.escape(alt)} indisponible</div>'


# ---------------------------------------------------------------------------
# Mini-calendrier reconstruit depuis les bitfields
# ---------------------------------------------------------------------------

def _calendar_row(label: str, bits: int | None, kind: str) -> str:
    """Une rangée : étiquette + 12 cases. bits None → « non extrait »."""
    if bits is None:
        return (
            f'<div class="cal-row"><span class="cal-label">{html.escape(label)}</span>'
            f'<span class="cal-none">— non extrait</span></div>'
        )
    cells = []
    for i in range(12):
        active = (bits >> i) & 1
        cls = f"cell {kind}" if active else "cell off"
        cells.append(f'<span class="{cls}"></span>')
    return (
        f'<div class="cal-row"><span class="cal-label">{html.escape(label)}</span>'
        f'{"".join(cells)}</div>'
    )


def _mini_calendar(flor: int | None, fruct: int | None) -> str:
    head_cells = "".join(f'<span class="cell head">{m}</span>' for m in MONTHS)
    return (
        '<div class="cal">'
        f'<div class="cal-row"><span class="cal-label"></span>{head_cells}</div>'
        f'{_calendar_row("Floraison", flor, "flor")}'
        f'{_calendar_row("Fructification", fruct, "fruct")}'
        '</div>'
    )


def _bullets_list(title: str, items: list[str]) -> str:
    if items:
        lis = "".join(f"<li>{html.escape(b)}</li>" for b in items)
        body = f"<ul>{lis}</ul>"
    else:
        body = '<div class="empty">— aucune puce extraite</div>'
    return f'<div class="bullets"><div class="bullets-title">{html.escape(title)}</div>{body}</div>'


# ---------------------------------------------------------------------------
# Détection des manques (triage)
# ---------------------------------------------------------------------------

def _missing_fields(entry: dict) -> list[str]:
    miss = []
    if entry.get("flor") is None:
        miss.append("floraison")
    if entry.get("fruct") is None:
        miss.append("fructification")
    if not entry.get("atouts"):
        miss.append("atouts")
    if not entry.get("limites"):
        miss.append("limites")
    return miss


# ---------------------------------------------------------------------------
# Rendu
# ---------------------------------------------------------------------------

def git_short_hash() -> str:
    try:
        out = subprocess.check_output(
            ["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"],
            stderr=subprocess.DEVNULL, text=True,
        ).strip()
        return out or "n/a"
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "n/a"


CSS = """
:root {
  --bg: #fafaf8; --fg: #1f2937; --muted: #6b7280; --border: #e5e7eb;
  --card-bg: #ffffff; --accent: #15803d; --warn: #b45309; --bad: #b91c1c;
  --flor: #d946ef; --fruct: #f59e0b; --off: #e5e7eb;
}
* { box-sizing: border-box; }
body {
  margin: 0; padding: 24px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  background: var(--bg); color: var(--fg); line-height: 1.45;
}
h1 { margin: 0 0 4px; font-size: 26px; }
h2 { margin: 34px 0 12px; font-size: 18px; border-bottom: 1px solid var(--border); padding-bottom: 6px; }
.meta { color: var(--muted); font-size: 13px; margin-bottom: 16px; }
.intro {
  background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 8px;
  padding: 12px 16px; margin-bottom: 18px; font-size: 14px;
}
.intro code { background: #d1fae5; padding: 1px 6px; border-radius: 3px;
  font-family: ui-monospace, monospace; font-size: 12.5px; }
.grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px; margin-bottom: 18px;
}
.card {
  background: var(--card-bg); border: 1px solid var(--border);
  border-radius: 8px; padding: 14px 16px;
}
.card .label { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.card .value { font-size: 24px; font-weight: 600; margin-top: 4px; }
.card .sub { font-size: 12px; color: var(--muted); margin-top: 4px; }
.triage {
  background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px;
  padding: 8px 14px; margin-bottom: 10px;
}
.triage .t-head { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
.triage .t-name { font-weight: 600; font-family: ui-monospace, monospace; font-size: 13px; }
.triage .t-file { color: var(--muted); font-size: 12px; }
.triage ul { margin: 6px 0 2px; padding-left: 20px; font-size: 13px; }
.triage .miss { color: var(--bad); }
.triage .warn { color: var(--warn); }
.ok-note { color: var(--accent); font-weight: 600; }
.fiche {
  background: var(--card-bg); border: 1px solid var(--border);
  border-radius: 10px; padding: 16px 18px; margin-bottom: 16px;
}
.fiche-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 4px; }
.fiche-latin { font-size: 17px; font-weight: 600; font-style: italic; }
.fiche-file { color: var(--muted); font-size: 12.5px; font-family: ui-monospace, monospace; }
.fiche-head a { font-size: 12.5px; color: var(--accent); text-decoration: none; }
.fiche-head a:hover { text-decoration: underline; }
.badge-nomatch {
  display: inline-block; padding: 1px 8px; border-radius: 3px;
  background: #fee2e2; color: var(--bad); font-size: 11px; font-weight: 600;
}
.fiche-warn { color: var(--warn); font-size: 12.5px; margin: 4px 0 8px; }
.compare { display: flex; gap: 20px; flex-wrap: wrap; align-items: flex-start; margin: 8px 0 12px; }
.compare > div { flex: 1 1 300px; min-width: 260px; }
.compare .col-title { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 6px; }
.crop { max-width: 480px; width: 100%; border: 1px solid var(--border); border-radius: 6px; background: white; }
.crop-missing {
  color: var(--muted); font-size: 13px; font-style: italic;
  border: 1px dashed var(--border); border-radius: 6px; padding: 14px; background: #f9fafb;
}
.cal { display: inline-block; }
.cal-row { display: flex; align-items: center; gap: 3px; margin-bottom: 4px; }
.cal-label { width: 92px; font-size: 12px; color: var(--muted); text-align: right; padding-right: 6px; }
.cell {
  width: 22px; height: 20px; border-radius: 3px; display: inline-flex;
  align-items: center; justify-content: center; font-size: 11px;
}
.cell.head { background: transparent; color: var(--muted); font-weight: 600; }
.cell.off { background: var(--off); }
.cell.flor { background: var(--flor); }
.cell.fruct { background: var(--fruct); }
.cal-none { font-size: 12.5px; color: var(--muted); font-style: italic; }
.cal-legend { font-size: 12px; color: var(--muted); margin-top: 4px; display: flex; gap: 14px; flex-wrap: wrap; }
.cal-legend .sw { display: inline-block; width: 12px; height: 12px; border-radius: 2px; vertical-align: -1px; margin-right: 4px; }
.retenir { display: flex; gap: 20px; flex-wrap: wrap; align-items: flex-start; }
.retenir > .bullets-col { flex: 1 1 300px; min-width: 260px; }
.bullets { margin-bottom: 8px; }
.bullets-title { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 4px; }
.bullets ul { margin: 0; padding-left: 20px; font-size: 13.5px; }
.bullets li { margin-bottom: 3px; }
.bullets .empty { color: var(--muted); font-size: 13px; font-style: italic; }
.controls { display: flex; gap: 12px; margin-bottom: 12px; align-items: center; flex-wrap: wrap;
  position: sticky; top: 0; background: var(--bg); padding: 8px 0; z-index: 5; border-bottom: 1px solid var(--border); }
.controls input { padding: 6px 10px; border: 1px solid var(--border); border-radius: 6px; font-size: 14px; min-width: 260px; }
.controls .total { color: var(--muted); font-size: 13px; }
"""


def render_html(trace: dict, trace_path: Path) -> str:
    entries = [dict(v, pdf_id=k) for k, v in trace.items()]
    entries.sort(key=lambda e: (e.get("nom_latin") or "").lower())

    total = len(entries)
    n_flor = sum(1 for e in entries if e.get("flor") is not None)
    n_fruct = sum(1 for e in entries if e.get("fruct") is not None)
    n_atouts = sum(1 for e in entries if e.get("atouts"))
    n_limites = sum(1 for e in entries if e.get("limites"))
    n_matched = sum(1 for e in entries if e.get("matched"))
    n_warn = sum(1 for e in entries if e.get("warnings"))

    generated = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    git_ref = git_short_hash()

    out: list[str] = []
    out.append("<!doctype html>")
    out.append('<html lang="fr"><head><meta charset="utf-8">')
    out.append('<meta name="viewport" content="width=device-width, initial-scale=1">')
    out.append("<title>Fiches-essences — validation des extractions PDF</title>")
    out.append(f"<style>{CSS}</style>")
    out.append("</head><body>")
    out.append("<h1>Fiches-essences — validation des extractions PDF</h1>")
    out.append(
        f'<div class="meta">Généré le {generated} · commit <code>{git_ref}</code> · '
        f'source <code>{html.escape(str(trace_path))}</code> · '
        f'crops <code>tools/.essences-pdf-cache/</code> (JPEG q{CROP_JPEG_QUALITY} '
        f'dpi&nbsp;{CROP_DPI}) · outil <code>tools/build_essences_preview.py</code></div>'
    )
    out.append(
        '<div class="intro">Page de <strong>relecture</strong>, pas un artefact de '
        'build. Pour chaque fiche : le mini-calendrier reconstruit depuis les '
        'bitfields (<code>bit 0 = janvier</code>) est posé <em>à côté</em> du crop '
        'de la zone calendrier du vrai PDF, pour vérifier case à case. Idem pour les '
        'puces « À retenir » vs. le crop de la zone. La section « Échecs &amp; '
        'warnings » en tête liste les fiches à revoir en priorité.</div>'
    )

    # --- Bandeau stats ---
    out.append('<div class="grid">')
    out.append(_card("Fiches", str(total)))
    out.append(_card("Floraison", f"{n_flor}/{total}", "bitfield extrait"))
    out.append(_card("Fructification", f"{n_fruct}/{total}", "bitfield extrait"))
    out.append(_card("Atouts", f"{n_atouts}/{total}", "≥ 1 puce"))
    out.append(_card("Limites", f"{n_limites}/{total}", "≥ 1 puce"))
    out.append(_card("Matchées", f"{n_matched}/{total}", "retenue + species-index"))
    out.append(_card("Avec warnings", str(n_warn)))
    out.append("</div>")

    # --- Section triage : échecs & warnings ---
    out.append('<h2>Échecs &amp; warnings</h2>')
    triage = [e for e in entries if e.get("warnings") or _missing_fields(e)]
    if not triage:
        out.append('<p class="ok-note">Aucun échec : toutes les fiches ont flor, '
                   'fruct, atouts, limites extraits et aucun warning.</p>')
    else:
        out.append(f'<p class="meta">{len(triage)} fiche(s) avec ≥ 1 warning ou '
                   'champ manquant.</p>')
        for e in triage:
            miss = _missing_fields(e)
            warns = e.get("warnings") or []
            out.append('<div class="triage">')
            out.append(
                '<div class="t-head">'
                f'<span class="t-name">{html.escape(e.get("nom_latin") or e["pdf_id"])}</span>'
                f'<span class="t-file">{html.escape(e.get("filename") or e["pdf_id"])}</span>'
                '</div>'
            )
            items = []
            if miss:
                items.append(f'<li class="miss">champs manquants : {html.escape(", ".join(miss))}</li>')
            for w in warns:
                items.append(f'<li class="warn">{html.escape(w)}</li>')
            out.append(f'<ul>{"".join(items)}</ul>')
            out.append('</div>')

    # --- Liste complète ---
    out.append('<h2>Toutes les fiches</h2>')
    out.append(
        '<div class="controls">'
        '<input type="search" id="filter" placeholder="Filtrer (nom latin, fichier)…">'
        f'<span class="total"><span id="visible-count">{total}</span> / {total} fiches</span>'
        '</div>'
    )
    for e in entries:
        out.append(_fiche_card(e))

    out.append(f"<script>{_JS}</script>")
    out.append("</body></html>")
    return "".join(out)


def _fiche_card(e: dict) -> str:
    pdf_id = e["pdf_id"]
    nom_latin = e.get("nom_latin") or pdf_id
    filename = e.get("filename") or pdf_id
    url = e.get("url")
    crops = crops_for(pdf_id)

    head = [
        '<div class="fiche-head">',
        f'<span class="fiche-latin">{html.escape(nom_latin)}</span>',
        f'<span class="fiche-file">{html.escape(filename)}</span>',
    ]
    if url:
        head.append(f'<a href="{html.escape(url)}" target="_blank" rel="noopener">PDF source ↗</a>')
    if not e.get("matched"):
        head.append('<span class="badge-nomatch">non retenue</span>')
    head.append('</div>')

    warns = e.get("warnings") or []
    warn_html = ""
    if warns:
        warn_html = ('<div class="fiche-warn">⚠ '
                     + html.escape(" · ".join(warns)) + '</div>')

    # Bloc calendriers : mini-calendrier reconstruit ↔ crop PDF.
    cal_block = (
        '<div class="compare">'
        '<div><div class="col-title">Reconstruit (bitfields)</div>'
        + _mini_calendar(e.get("flor"), e.get("fruct"))
        + '<div class="cal-legend">'
          '<span><span class="sw" style="background:var(--flor)"></span>Floraison</span>'
          '<span><span class="sw" style="background:var(--fruct)"></span>Fructification</span>'
          '</div></div>'
        '<div><div class="col-title">Crop PDF (calendrier)</div>'
        + _img_or_placeholder(crops["calendars"], "Crop calendrier")
        + '</div>'
        '</div>'
    )

    # Bloc À retenir : puces ↔ crop PDF.
    retenir_block = (
        '<div class="compare">'
        '<div class="retenir-cols"><div class="col-title">Puces extraites</div>'
        '<div class="retenir">'
        '<div class="bullets-col">' + _bullets_list("Atouts", e.get("atouts") or []) + '</div>'
        '<div class="bullets-col">' + _bullets_list("Limites", e.get("limites") or []) + '</div>'
        '</div></div>'
        '<div><div class="col-title">Crop PDF (À retenir)</div>'
        + _img_or_placeholder(crops["aretenir"], "Crop À retenir")
        + '</div>'
        '</div>'
    )

    hay = f"{nom_latin} {filename}"
    return (
        f'<div class="fiche" data-hay="{html.escape(hay.lower())}">'
        + "".join(head)
        + warn_html
        + cal_block
        + retenir_block
        + '</div>'
    )


def _card(label: str, value: str, sub: str = "") -> str:
    sub_html = f'<div class="sub">{html.escape(sub)}</div>' if sub else ""
    return (
        f'<div class="card"><div class="label">{html.escape(label)}</div>'
        f'<div class="value">{html.escape(value)}</div>{sub_html}</div>'
    )


_JS = r"""
(function () {
  const filter = document.getElementById("filter");
  const counter = document.getElementById("visible-count");
  const cards = Array.from(document.querySelectorAll(".fiche"));
  filter.addEventListener("input", () => {
    const q = filter.value.trim().toLocaleLowerCase("fr");
    let visible = 0;
    cards.forEach(card => {
      if (!q || card.dataset.hay.includes(q)) { card.style.display = ""; visible++; }
      else { card.style.display = "none"; }
    });
    counter.textContent = visible;
  });
})();
"""


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--trace", type=Path, default=DEFAULT_TRACE,
                    help=f"sidecar essence-extras.json (défaut {DEFAULT_TRACE})")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT,
                    help=f"HTML de sortie (défaut {DEFAULT_OUT})")
    args = ap.parse_args()

    if not args.trace.exists():
        print(f"[err] sidecar absent : {args.trace}\n"
              f"      Lancer `python3 tools/build_dataset.py` d'abord pour le générer.")
        return 1
    print(f"[essv] lecture {args.trace}")
    with args.trace.open("r", encoding="utf-8") as f:
        trace = json.load(f)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    print(f"[essv] {len(trace)} fiches, génération des crops (dpi {CROP_DPI})…")
    htmlout = render_html(trace, args.trace)
    args.out.write_text(htmlout, encoding="utf-8")
    size_mb = args.out.stat().st_size / (1024 * 1024)
    print(f"[ok ] → {args.out} ({size_mb:.2f} Mo)")
    if size_mb >= 6:
        print(f"[warn] taille {size_mb:.2f} Mo ≥ budget 6 Mo — baisser CROP_DPI.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
