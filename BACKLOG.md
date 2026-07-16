# Backlog

File d'attente brute. Tout retour, idée ou bug atterrit ici en 1 ligne au format :

```
- [TAG] description courte (origine, date)
```

**Tags** : `[ ]` à trier, `[→Codename]` rangé dans un cycle, `[creuser]` mérite réflexion avant arbitrage, `[refusé]` tranché négatif.
**Origines** : `audit`, `user:moi`, `user:F&F`, `gh#N`, `device-test`.

Triage en lot au début de chaque cycle. Process complet dans `CLAUDE.md` (*Workflow & docs*).

---

## À trier

- [ ] [perf] Carte filtrée + filtre rapide lents : le 2-vagues re-pousse tout le GeoJSON et laisse MapLibre re-clusteriser 217 k points (défiltrage mesuré ~42 s wall-time, dominé par le re-clustering pas par l'enrich ~300 ms). Inhérent à l'archi « re-push + recluster », pas une régression. Pistes : push différentiel / garder la source clusterisée et ne changer que le filtre côté données (device-test, 2026-06-13)
- [ ] [perf] Jank du FilterSplash : `addArbresLayers` (obligatoirement main-thread, contexte GL) bloque le main thread 1-4 s pour une espèce commune (subset réel plusieurs Mo, pas le « < 1 Mo » supposé en commentaire `MapScreen.kt:803`), figeant les animations frame-clock du splash. Sortir le clustering du chemin critique = chantier perf (device-test, 2026-06-13)
- [ ] 4 WebP principales S9 (source Paris) dépassent le budget ~800px grand côté : `27-0` 941px, `102-0` 1015px, `237-0`/`475-0` 1100px (hauteur ; largeurs ≤ 750px). Décodables et légères, aucun impact fonctionnel — re-crop/downscale optionnel côté `essence_pdf.py` (claude:vérif-S11, 2026-07-15)
- [ ] [data] Résolution Wikidata multi-QID non déterministe dans `resolve_via_wikidata` : un binôme peut matcher plusieurs entités Wikidata (P225), le code garde le 1er binding renvoyé et l'ordre SPARQL n'est pas garanti → mis-résolution possible. Cas réel : `Vitex agnus-castus` (sk 934) résolu sur `Zanthoxylum armatum` (Q6170892) au 1er build du refresh (summary + nv pollués « Poivre de Timut »), correct au rebuild isolé ; contourné par override nv « Gattilier ». Garde-fou heuristique `_frtitle_genus_mismatch` tenté puis **retiré** (faux positifs prouvés sur synonymes inter-genres légitimes, ex. `Sorbus aria`→page `Aria edulis`, `Malus trilobata`→`Eriolobus trilobatus`). Vrai fix = quand plusieurs QID matchent, préférer l'entité `instance-of/subclass taxon` ou celle dont le genre attendu apparaît (claude:refresh-étape0, 2026-07-16)
- [ ] Refresh des 6 screenshots README (docs/screenshots/) — datent de Boussole, les apports Netteté et Herbier n'y figurent pas ; captures device requises (claude:tour-fin-Netteté, 2026-06-13 ; reporté à la clôture Herbier, 2026-07-16)

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_4_5` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)

## À creuser

### Onboarding & premier lancement

_Premier contact avec l'app : le WelcomeScreen n'est pas lu, la permission localisation n'est jamais demandée spontanément. Probablement à traiter ensemble._

- [creuser] WelcomeScreen pas lu, intro depuis la carte — fold pressenti (user:F&F + user:moi 2026-05-07 ; fold proposé 2026-05-21 ; sorti du cycle Netteté 2026-06-12, à re-discuter avant arbitrage)
- [creuser] Vrai install frais (permission localisation jamais accordée) : personne ne demande la permission spontanément, carte sur Paris jusqu'au tap FAB — demander au 1er affichage carte post-onboarding (one-shot, refus respecté) ? À intégrer à la réflexion fold WelcomeScreen (item ci-dessus) (device-test, 2026-06-12)

### Aide à l'identification

_Aider à reconnaître / distinguer les espèces. Même logique, à arbitrer ensemble._

- [creuser] **Écran Comparaison d'espèces** : long-press 600 ms sur une card Arboretum (cancel si déplacement > 8 px, iOS contextmenu fallback) ouvre un bottom-sheet picker des autres espèces capturées, puis route `#/compare/{skA}/{skB}` qui affiche les deux fiches en side-by-side — empilées verticalement sur téléphone, deux colonnes parallèles à scroll indépendant sur tablette. Utile pour distinguer espèces proches (Quercus robur vs petraea, Tilia cordata vs platyphyllos). Implémenté côté PWA, à reconsidérer pour Android si le besoin se confirme. (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu, garder pour cycle ultérieur ; sorti de la proposition Herbier 2026-05-21)
- [creuser] Mini-quiz ou capacité d'identification entre espèces partageant le même `nc` (Quercus robur vs petraea, Tilia cordata vs platyphyllos) (claude:analyse, 2026-05-08 ; refusé du cycle Catalogue 2026-05-10 — scope dédié, UX du quiz + génération de paires + scoring trop coûteux à empiler)
- [creuser] Aide à l'identification des genres/espèces — item parapluie de la sous-famille ; pourrait devenir creux si « Comparaison d'espèces » est livrée

### Engagement & rétention

_Mécaniques de ré-engagement, en tension avec le ton « compagnon de balade calme »._

- [creuser] CTA « Aller sur la carte » — Boutons dans les empty states (Arboretum historique, Remarquables historique, Profil vide).
- [creuser] Notifications push : digest mensuel opt-in vs rien (audit-tension#1)
- [creuser] Quêtes hebdomadaires locales, opt-in, sans push (audit V2#3, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 quand le cycle a été dissous)
- [creuser] Leaderboard optionnel et minimaliste ? (en tension avec l'invariant CLAUDE.md « pas de classement » ; gardé en creuser sur choix user:moi 2026-05-20)

### Elargissement de l'app

_Plus d'arbres, plus de fun_

- [creuser] Combler les vides du dataset sur les 4 jardins gérés hors Ville de Paris (Luxembourg/Sénat ~2 980 réels vs 243 en base, Plantes/MNHN ~2 000 vs 79, Tuileries/CMN, Villette/EPPGHV) — `les-arbres` ne couvre que la voirie municipale. MNHN et Sénat n'ont pas de dump open data exploitable ; OSM (`natural=tree` + `species=`) est la seule piste automatisable mais **verdict non rendu** (recherche incomplète : 4 requêtes Overpass à exécuter hors sandbox, critère = ratio `species=` ≥ ~70 %). Détails, arbitrages déjà pris (namespace `idbase`, qualité minimale lat/lon/genre/espèce) et requêtes dans `docs/audit-arbres-jardins-hors-ville.md`. (user:moi, 2026-05-20)
- [creuser] Script `tools/scout_other_cities.py` qui interroge OpenData de villes du Grand Paris et produit un md de faisabilité (user:moi, 2026-05-07)

### Technique / Perf

_Optimisations sous le capot, sans impact fonctionnel direct._

_(vide — « MapView persistante » promu item 1 du cycle Netteté le 2026-06-11)_

## Refusé

- [refusé] Cascade attributs Wikidata/POWO/Wikipedia/EOL pour les 584 espèces hors fiches-essences — sondes réseau réelles : rendement = la famille seule (~98 % via P171*, CC0), tout le reste indisponible ou non structuré ; ne justifie pas un sprint. Piste réactivable : famille-seule Wikidata genre-centrique, injection ess={"fam":…} dans write_species_info, zéro code Kotlin (S8 Herbier, abandonné 2026-07-15)
- [refusé] Fiche espèce consultable non capturée (cellules « ??? » tappables → fiche aperçu) — renversement de la décision du 2026-06-14 : la fiche n'est PAS consultable tant que l'espèce n'est pas capturée, cœur du game design, la découverte se mérite en marchant. Aucun code à retirer, les « ??? » étaient restés non cliquables (S12 Herbier, refusé 2026-07-16, user:moi)
- [refusé] Discriminant visuel pour remarquables jumeaux indistinguables en liste : deux « Marronnier / PARC DES BUTTES CHAUMONT / 7 RUE BOTZARIS » strictement identiques côte à côte (catalogue + fiche espèce « Arbres remarquables de cette espèce ») — réalité du dataset (deux individus physiques distincts à la même adresse), pas une erreur (claude:tour-fin-Netteté, 2026-06-13 ; user:moi 2026-06-13 : refusé)
- [refusé] Bouton partage PNG sur fiche espèce (audit-C, tension single-player vs F&F à trancher - trop loin d'un intérêt)
- [refusé] Carte chromatique vert/jaune/gris par arrondissement (audit V2#5, 2026-05-06 ; user:moi 2026-05-12 lors du cadrage Progression : la barre « X / 22 arrondissements visités » dans le Profil + le badge « Maître du Xe » suffisent pour mettre sur la piste, l'overlay chromatique sur la carte serait redondant et chargerait inutilement la vue principale)
- [refusé] Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche espèce non capturée (audit-A, 2026-05-06 ; user:moi 2026-05-09 : philosophie « découverte en marchant », la 81e espèce se trouve en tapant un pin gris à proximité, le côté quête est porté par les Remarquables ★)
- [refusé] Planificateur d'itinéraire / boucle de marche greedy nearest-neighbour passant par N arbres jamais capturés, presets 1/2/3/5 km, CTA depuis fiche espèce (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu ; user:moi 2026-05-21 : refusé — contredit « découverte en marchant », même motif que le refus « Espèces manquantes » en plus directif, et chevauche le mode chasse Étoile)
- [refusé] Carte flottante « prochaine espèce à découvrir » suggérant la closest never-captured species, refresh à chaque fix GPS, tap → fly-to + ouvre sheet (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu ; user:moi 2026-05-21 : refusé — « va ici ensuite » permanent, même motif que le refus « Espèces manquantes »)
- [refusé] Refonte modèle remarquables : espèce-boss vs vraie quête (audit V2#2 ; user:moi 2026-05-21 : outdated — la question structurelle a été de facto tranchée vers « vraie quête / individu » par les cycles Progression et Boussole, fiche-remarquable individuelle déverrouillée à la capture, comptage par arrondissement, mode chasse Étoile)
- [refusé] Table `photo` 1:N + backup `schemaVersion = 2` (BACKLOG cycle Photos d'origine ; user:moi 2026-05-09 : le modèle Room actuel 1 capture = 1 photo supporte déjà N photos par arbre/espèce via N captures, pas de migration nécessaire)
- [refusé] Cold-start « 7-10 s freeze » signalé par audit (user:moi 2026-05-07 : audit faux, pas de problème de temps long bloquant au 1er lancement)
- [refusé] 4 badges saisonniers v1.1.0 proposés par audit (user:moi 2026-05-07 : caduques, on supprime les saisons)
- [refusé] Mini-transition d'ambiance switch saison (audit-D2 ; user:moi 2026-05-07 : caduque, suppression saisons)
- [refusé] Anticlimax du déblocage des 38 147 platanes (audit-tension#4 ; audit lui-même recommande de laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax)
- [refusé] Pré-affichage fiche remarquable enrichie même non capturée, bandeau « Pas encore découvert » (audit-B, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 — c'est une chasse avec assez d'infos avec la distance)
