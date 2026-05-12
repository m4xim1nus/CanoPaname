#!/usr/bin/env python3
"""Génère `docs/tips/index.html` : page de revue éditoriale des splash tips.

Lit l'artefact produit par `tools/build_dataset.py` :
- `app/src/main/assets/splash-tips.json` (banque fusionnée static + dataset —
  c'est exactement ce que le `ColdStartSplash` affiche au runtime)

Sortie unique : un HTML autonome (sans CDN ni dépendance JS), ouvrable dans un
navigateur. Tous les tips groupés par catégorie (`intro` d'abord, dans l'ordre
figé du 1er lancement, puis `dataset`, `history`, `popculture`, `player`), les
placeholders runtime rendus avec des valeurs d'exemple, et pour chaque tip un
verdict (`RAS` / `à tuer` / `formulation à revoir` / `chute à réécrire`) + un
commentaire libre. Verdict
et commentaire sont persistés en `localStorage` pour survivre à un refresh (la
revue se fait en async). Un bouton « Exporter mon avis » produit un bloc texte
copiable listant les tips à traiter — à recoller dans le sprint S6 (« intégration
des retours »).

Ce n'est **pas** un artefact de build consommé par l'app : c'est un outil de
relecture. À régénérer après chaque `python3 tools/build_dataset.py`.

Usage : `python3 tools/build_tips_preview.py`
"""

from __future__ import annotations

import datetime
import html
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPLASH_TIPS = ROOT / "app" / "src" / "main" / "assets" / "splash-tips.json"
OUT_DIR = ROOT / "docs" / "tips"
OUT_HTML = OUT_DIR / "index.html"

# Ordre d'affichage des catégories. `intro` en tête, ordonné par la liste
# `intro[]` (ordre figé du 1er lancement) ; les autres dans cet ordre, chaque
# bloc trié par id.
CATEGORY_ORDER = ["intro", "dataset", "history", "popculture", "app", "player"]
CATEGORY_LABELS = {
    "intro": "Intro (1er lancement)",
    "dataset": "Dataset (généré)",
    "history": "Histoire & faits",
    "popculture": "Pop culture",
    "app": "L'app elle-même",
    "player": "Stats joueur",
}
CATEGORY_NOTES = {
    "intro": "Les 10 tips de la séquence d'introduction, dans l'ordre exact où "
             "ils défilent au tout premier lancement. Les éditer = éditer "
             "tools/splash-tips-static.json.",
    "dataset": "Tips générés à partir des agrégats du CSV OpenData par "
               "write_splash_tips() dans tools/build_dataset.py. Le texte n'est "
               "pas éditable à la main : pour changer une chute, modifier le "
               "générateur. Les chiffres se rafraîchissent au prochain build.",
    "history": "Trivia historique/écologique écrit à la main "
               "(tools/splash-tips-static.json).",
    "popculture": "Références culturelles écrites à la main "
                  "(tools/splash-tips-static.json).",
    "app": "Tips sur les fonctionnalités de l'app (saisons, badges, mode chasse, "
           "sauvegarde, fiches…), écrits à la main (tools/splash-tips-static.json).",
    "player": "Gabarits avec placeholders runtime "
              "({speciesCount}, {remarquableCount}, {daysSinceFirst}). "
              "Affichés ici avec des valeurs d'exemple.",
}

# Valeurs d'exemple pour rendre les placeholders runtime. Choisies « petites mais
# pas triviales » pour que les chutes du genre « ce n'est plus une lubie » aient
# du sens.
SAMPLE_VALUES = {
    "speciesCount": "12",
    "remarquableCount": "3",
    "daysSinceFirst": "37",
}

# Verdicts proposés pour chaque tip. `RAS` est la valeur par défaut (cochée).
VERDICTS = ("RAS", "à tuer", "formulation à revoir", "chute à réécrire")


def git_short_hash() -> str:
    try:
        out = subprocess.check_output(
            ["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"],
            stderr=subprocess.DEVNULL, text=True,
        ).strip()
        return out or "n/a"
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "n/a"


def render_placeholders(text: str) -> str:
    out = text
    for key, val in SAMPLE_VALUES.items():
        out = out.replace("{" + key + "}", val)
    return out


def text_to_html(text: str) -> str:
    # Le texte de splash-tips.json a déjà ses sauts de ligne (`\n`) — le splash
    # affiche une phrase par ligne. On les rend en <br>.
    return "<br>".join(html.escape(line) for line in text.split("\n"))


def render_card(tip: dict, intro_order: dict[str, int]) -> str:
    tid = tip["id"]
    cat = tip.get("category", "?")
    raw = tip.get("text", "")
    requires = tip.get("requires") or []

    chips: list[str] = [f'<span class="chip chip-cat chip-{html.escape(cat)}">{html.escape(cat)}</span>']
    if cat == "intro" and tid in intro_order:
        chips.append(f'<span class="chip">intro #{intro_order[tid] + 1}/10</span>')
    if requires:
        chips.append(
            '<span class="chip chip-req">requires: '
            + html.escape(", ".join(requires)) + "</span>"
        )
    if cat == "dataset":
        chips.append(
            '<span class="chip chip-gen" title="Texte généré par '
            'write_splash_tips() — modifier le générateur, pas le texte">généré</span>'
        )

    rendered = text_to_html(render_placeholders(raw))
    raw_block = ""
    if requires:
        raw_block = (
            '<div class="raw">gabarit : <code>'
            + html.escape(raw.replace("\n", " "))
            + "</code></div>"
        )

    esc_tid = html.escape(tid)
    rname = "v-" + esc_tid
    radios = "".join(
        f'<label class="verdict-opt"><input type="radio" name="{rname}" '
        f'value="{html.escape(val)}"{" checked" if val == "RAS" else ""}> {html.escape(val)}</label>'
        for val in VERDICTS
    )
    return (
        f'<div class="tip-card" data-id="{esc_tid}" data-cat="{html.escape(cat)}">'
        f'<div class="tip-head"><span class="tip-id">{esc_tid}</span>'
        f'<span class="chips">{"".join(chips)}</span></div>'
        f'<div class="tip-text">{rendered}</div>'
        f"{raw_block}"
        f'<div class="review">{radios}'
        '<input type="text" class="comment" placeholder="commentaire (optionnel)">'
        "</div>"
        "</div>"
    )


def render_html(payload: dict) -> str:
    version = payload.get("version", "?")
    intro_ids: list[str] = list(payload.get("intro", []))
    intro_order = {tid: i for i, tid in enumerate(intro_ids)}
    tips: list[dict] = list(payload.get("tips", []))

    by_cat: dict[str, list[dict]] = {}
    for t in tips:
        by_cat.setdefault(t.get("category", "?"), []).append(t)

    # `intro` dans l'ordre figé ; les autres triés par id.
    ordered_sections: list[tuple[str, list[dict]]] = []
    for cat in CATEGORY_ORDER:
        items = by_cat.get(cat, [])
        if not items:
            continue
        if cat == "intro":
            pos = {tid: i for i, tid in enumerate(intro_ids)}
            items = sorted(items, key=lambda t: pos.get(t["id"], 1_000_000))
        else:
            items = sorted(items, key=lambda t: t["id"])
        ordered_sections.append((cat, items))
    # Catégories inattendues éventuelles, en queue.
    for cat in sorted(by_cat):
        if cat not in CATEGORY_ORDER:
            ordered_sections.append((cat, sorted(by_cat[cat], key=lambda t: t["id"])))

    n_total = len(tips)
    n_with_ph = sum(1 for t in tips if t.get("requires"))
    size_kb = SPLASH_TIPS.stat().st_size // 1024
    generated = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    git_ref = git_short_hash()

    css = """
    :root {
      --bg: #fafaf8; --fg: #1f2937; --muted: #6b7280; --border: #e5e7eb;
      --card-bg: #ffffff; --accent: #15803d; --warn: #b45309;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0; padding: 24px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg); color: var(--fg); line-height: 1.45;
    }
    h1 { margin: 0 0 4px; font-size: 26px; }
    h2 {
      margin: 34px 0 4px; font-size: 18px;
      border-bottom: 1px solid var(--border); padding-bottom: 6px;
    }
    h2 .count { color: var(--muted); font-weight: 400; font-size: 14px; }
    .meta { color: var(--muted); font-size: 13px; margin-bottom: 16px; }
    .cat-note { color: var(--muted); font-size: 13px; margin: 0 0 14px; }
    .intro {
      background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 8px;
      padding: 12px 16px; margin-bottom: 18px; font-size: 14px;
    }
    .intro strong { color: #065f46; }
    .intro code {
      background: #d1fae5; padding: 1px 6px; border-radius: 3px;
      font-family: ui-monospace, monospace; font-size: 12.5px;
    }
    .grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 12px; margin-bottom: 18px;
    }
    .card {
      background: var(--card-bg); border: 1px solid var(--border);
      border-radius: 8px; padding: 14px 16px;
    }
    .card .label {
      font-size: 12px; color: var(--muted); text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .card .value { font-size: 24px; font-weight: 600; margin-top: 4px; }
    .card .sub { font-size: 12px; color: var(--muted); margin-top: 4px; }
    .controls {
      display: flex; gap: 12px; margin: 6px 0 18px; align-items: center;
      flex-wrap: wrap; position: sticky; top: 0; background: var(--bg);
      padding: 8px 0; z-index: 5; border-bottom: 1px solid var(--border);
    }
    .controls input[type=text] {
      padding: 7px 11px; border: 1px solid var(--border); border-radius: 6px;
      font-size: 14px; min-width: 260px;
    }
    .controls button {
      padding: 7px 14px; border: 1px solid var(--border); border-radius: 6px;
      background: var(--card-bg); font-size: 14px; cursor: pointer;
    }
    .controls button:hover { background: #f3f4f6; }
    .controls button.primary { background: var(--accent); color: white; border-color: var(--accent); }
    .controls button.primary:hover { background: #166534; }
    .controls .total { color: var(--muted); font-size: 13px; }
    .tip-card {
      background: var(--card-bg); border: 1px solid var(--border);
      border-radius: 8px; padding: 12px 16px; margin-bottom: 10px;
    }
    .tip-card.touched { border-color: var(--warn); }
    .tip-head {
      display: flex; justify-content: space-between; align-items: baseline;
      gap: 12px; flex-wrap: wrap;
    }
    .tip-id {
      font-family: ui-monospace, monospace; font-size: 12.5px; color: #525b6b;
    }
    .chips { display: inline-flex; gap: 6px; flex-wrap: wrap; }
    .chip {
      display: inline-block; padding: 1px 8px; border-radius: 3px;
      background: #f3f4f6; color: var(--muted); font-size: 11px;
    }
    .chip-req { background: #fef3c7; color: #92400e; }
    .chip-gen { background: #ede9fe; color: #5b21b6; }
    .chip-cat { font-weight: 600; }
    .tip-text { margin: 8px 0; font-size: 15px; }
    .raw { font-size: 12px; color: var(--muted); margin-bottom: 4px; }
    .raw code {
      background: #f3f4f6; padding: 1px 5px; border-radius: 3px;
      font-family: ui-monospace, monospace; font-size: 11.5px;
    }
    .review {
      display: flex; gap: 14px; align-items: center; flex-wrap: wrap;
      margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--border);
    }
    .verdict-opt { font-size: 13px; cursor: pointer; user-select: none; }
    .comment {
      flex: 1; min-width: 200px; padding: 5px 9px;
      border: 1px solid var(--border); border-radius: 5px; font-size: 13px;
    }
    #export-out {
      white-space: pre-wrap; background: #1f2937; color: #e5e7eb;
      border-radius: 8px; padding: 14px 16px; font-family: ui-monospace, monospace;
      font-size: 12.5px; margin: 12px 0 24px; display: none;
    }
    """

    out: list[str] = []
    out.append("<!doctype html>")
    out.append('<html lang="fr"><head><meta charset="utf-8">')
    out.append("<title>Splash tips — revue éditoriale</title>")
    out.append(f"<style>{css}</style>")
    out.append("</head><body>")
    out.append("<h1>Splash tips — revue éditoriale</h1>")
    out.append(
        f'<div class="meta">Généré le {generated} · '
        f'commit <code>{git_ref}</code> · '
        f'source <code>app/src/main/assets/splash-tips.json</code> (version {html.escape(str(version))}) · '
        f'outil <code>tools/build_tips_preview.py</code></div>'
    )
    out.append(
        '<div class="intro">Page de <strong>relecture</strong>, pas un artefact de '
        'build. Pour chaque tip : un verdict (<code>RAS</code> par défaut, '
        '<code>à tuer</code>, <code>formulation à revoir</code>, '
        '<code>chute à réécrire</code>) et un commentaire libre. '
        'Tes choix sont sauvegardés dans le navigateur (<code>localStorage</code>) — '
        'tu peux fermer et revenir. Quand tu as fini, « Exporter mon avis » produit un '
        'bloc texte à recoller dans le sprint S6. Les tips <code>dataset</code> sont '
        'générés : on relit la <em>chute</em>, pas les chiffres (qui se rafraîchissent '
        'au build).</div>'
    )

    # --- Synthèse ---
    out.append('<div class="grid">')
    out.append(_card("Tips au total", str(n_total)))
    for cat in CATEGORY_ORDER:
        n = len(by_cat.get(cat, []))
        if n:
            out.append(_card(CATEGORY_LABELS[cat], str(n)))
    out.append(_card("Avec placeholders", str(n_with_ph), "tips player à valeur dynamique"))
    out.append(_card("Taille du fichier", f"{size_kb} Ko"))
    out.append("</div>")

    # --- Contrôles ---
    out.append('<div class="controls">')
    out.append('<input type="text" id="filter" placeholder="Filtrer (id ou texte)…">')
    out.append(f'<span class="total"><b id="visible-count">{n_total}</b> / {n_total} affichés</span>')
    out.append('<button class="primary" id="export-btn">Exporter mon avis</button>')
    out.append('<button id="copy-btn" style="display:none">Copier</button>')
    out.append('<button id="reset-btn">Tout réinitialiser</button>')
    out.append("</div>")
    out.append('<pre id="export-out"></pre>')

    # --- Sections ---
    for cat, items in ordered_sections:
        label = CATEGORY_LABELS.get(cat, cat)
        out.append(f'<h2 id="sec-{html.escape(cat)}">{html.escape(label)} <span class="count">({len(items)})</span></h2>')
        note = CATEGORY_NOTES.get(cat)
        if note:
            out.append(f'<p class="cat-note">{html.escape(note)}</p>')
        for t in items:
            out.append(render_card(t, intro_order))

    out.append(f"<script>{_js()}</script>")
    out.append("</body></html>")
    return "".join(out)


def _card(label: str, value: str, sub: str = "") -> str:
    sub_html = f'<div class="sub">{html.escape(sub)}</div>' if sub else ""
    return (
        f'<div class="card"><div class="label">{html.escape(label)}</div>'
        f'<div class="value">{html.escape(value)}</div>{sub_html}</div>'
    )


def _js() -> str:
    return r"""
    (function () {
      const KEY = id => "tips-review:" + id;
      const cards = Array.from(document.querySelectorAll(".tip-card"));

      function stateOf(card) {
        const checked = card.querySelector('input[type=radio]:checked');
        return {
          verdict: checked ? checked.value : "RAS",
          comment: card.querySelector(".comment").value.trim(),
        };
      }
      function markTouched(card) {
        const s = stateOf(card);
        card.classList.toggle("touched", s.verdict !== "RAS" || s.comment.length > 0);
      }
      function persist(card) {
        const s = stateOf(card);
        const k = KEY(card.dataset.id);
        if (s.verdict === "RAS" && !s.comment) localStorage.removeItem(k);
        else localStorage.setItem(k, JSON.stringify(s));
        markTouched(card);
      }
      function restore(card) {
        let s = null;
        try { s = JSON.parse(localStorage.getItem(KEY(card.dataset.id)) || "null"); } catch (e) {}
        if (!s) { markTouched(card); return; }
        if (s.verdict) {
          const r = card.querySelector('input[type=radio][value="' + s.verdict.replace(/"/g, '\\"') + '"]');
          if (r) r.checked = true;
        }
        if (s.comment) card.querySelector(".comment").value = s.comment;
        markTouched(card);
      }

      cards.forEach(card => {
        restore(card);
        card.querySelectorAll('input[type=radio]').forEach(r => r.addEventListener("change", () => persist(card)));
        const c = card.querySelector(".comment");
        c.addEventListener("input", () => persist(card));
      });

      // Filtre.
      const filter = document.getElementById("filter");
      const counter = document.getElementById("visible-count");
      filter.addEventListener("input", () => {
        const q = filter.value.trim().toLocaleLowerCase("fr");
        let visible = 0;
        cards.forEach(card => {
          if (!q) { card.style.display = ""; visible++; return; }
          const hay = (card.dataset.id + " " + card.textContent).toLocaleLowerCase("fr");
          if (hay.includes(q)) { card.style.display = ""; visible++; }
          else card.style.display = "none";
        });
        counter.textContent = visible;
      });

      // Export.
      const out = document.getElementById("export-out");
      const copyBtn = document.getElementById("copy-btn");
      document.getElementById("export-btn").addEventListener("click", () => {
        const picked = [];
        cards.forEach(card => {
          const s = stateOf(card);
          if (s.verdict === "RAS" && !s.comment) return;
          picked.push({ id: card.dataset.id, cat: card.dataset.cat, ...s });
        });
        if (!picked.length) {
          out.textContent = "Aucun avis saisi (tous les tips sont RAS, sans commentaire).";
          out.style.display = ""; copyBtn.style.display = "none";
          return;
        }
        const lines = [];
        lines.push("# Revue splash tips — " + picked.length + " tip(s) à traiter");
        lines.push("# (généré depuis docs/tips/index.html)");
        let curCat = null;
        picked.forEach(p => {
          if (p.cat !== curCat) { curCat = p.cat; lines.push(""); lines.push("## " + curCat); }
          let l = "- [" + p.verdict + "] " + p.id;
          if (p.comment) l += " — " + p.comment;
          lines.push(l);
        });
        out.textContent = lines.join("\n");
        out.style.display = ""; copyBtn.style.display = "";
      });
      copyBtn.addEventListener("click", () => {
        const txt = out.textContent;
        const done = () => { const o = copyBtn.textContent; copyBtn.textContent = "Copié ✓"; setTimeout(() => copyBtn.textContent = o, 1500); };
        if (navigator.clipboard) navigator.clipboard.writeText(txt).then(done, () => fallbackCopy(txt, done));
        else fallbackCopy(txt, done);
      });
      function fallbackCopy(txt, done) {
        const ta = document.createElement("textarea");
        ta.value = txt; document.body.appendChild(ta); ta.select();
        try { document.execCommand("copy"); done(); } catch (e) {}
        document.body.removeChild(ta);
      }

      // Reset.
      document.getElementById("reset-btn").addEventListener("click", () => {
        if (!confirm("Effacer tous les verdicts et commentaires saisis ?")) return;
        cards.forEach(card => {
          localStorage.removeItem(KEY(card.dataset.id));
          const ras = card.querySelector('input[type=radio][value="RAS"]');
          if (ras) ras.checked = true;
          card.querySelector(".comment").value = "";
          markTouched(card);
        });
        out.style.display = "none"; copyBtn.style.display = "none";
      });
    })();
    """


def main() -> int:
    if not SPLASH_TIPS.exists():
        print(
            f"[err] {SPLASH_TIPS.relative_to(ROOT)} absent — lancer "
            f"`python3 tools/build_dataset.py` d'abord."
        )
        return 1
    print(f"[tipv] lecture {SPLASH_TIPS.relative_to(ROOT)}")
    with SPLASH_TIPS.open("r", encoding="utf-8") as f:
        payload = json.load(f)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    htmlout = render_html(payload)
    OUT_HTML.write_text(htmlout, encoding="utf-8")
    size_kb = OUT_HTML.stat().st_size // 1024
    print(
        f"[ok ] {len(payload.get('tips', []))} tips → "
        f"{OUT_HTML.relative_to(ROOT)} ({size_kb} Ko)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
