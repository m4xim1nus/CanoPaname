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
- [ ] Textes extraits des PDF essences : espaces parasites dans les ordinaux en exposant (« 12 e », « 19 e siècle » dans quelques proses `paris`) — l'exposant est un span séparé que `_clean` ne recolle pas ; recoller `\d+ e(r|re)?\b` côté `essence_pdf.py` (claude:vérif-S6, 2026-07-15)
- [ ] Prose `paris` du Ginkgo : run-on « …dans les parcs Essence rare à Paris… » — deux paragraphes du PDF fusionnés sans ponctuation (la source n'a pas de point final). Cas unique, éventuel point inséré au join si ligne suivante démarre par une majuscule (claude:vérif-S6, 2026-07-15)

## Cycle Netteté

Polish de la boucle carte + capture. **Cycle en cours depuis le 2026-06-11** — détail item par item dans `ROADMAP.md`.

- ~~[→Netteté] **MapView persistante across navigation** — instance hissée hors de `MapScreen`, retour carte instantané sans splash~~ **Livré le 2026-06-11** : `MapHost` Activity-scopé (lifecycle GL relayé de l'Activity, rendu gelé hors-écran, pipeline d'init one-shot + observers de découverte dans son scope), validé device (cold start, onboarding, capture, allers-retours, disparition des microfreezes). Sous-étapes A/B/C, commits `cf436c4` / `595710f` / cleanup C. (pwa:livraison-dev-externe, 2026-06-09 ; design arbitré 2026-06-11)
- ~~[→Netteté] Cône de vision sur le pin Location : secteur orienté selon la boussole du téléphone, façon Google Maps~~ **Livré le 2026-06-11, validé device le 2026-06-12** : `RenderMode.COMPASS` + `bearingDrawable` `ic_location_cone`, puck au-dessus des arbres via `layerAbove(CLUSTER_COUNT_LAYER_ID)` — détail dans `CHANGELOG.md` `[1.5.0]` (user:moi, 2026-05-20)
- ~~[→Netteté] Filtres rapides depuis la sheet d'un arbre non remarquable : boutons « toute l'espèce » / « tout le genre »~~ **Livré le 2026-06-12, validé device** : `MapHost.quickFilter`, re-push `setGeoJson` du subset filtré sur la source persistante, défiltrage banner ✕ — détail dans `CHANGELOG.md` `[1.5.0]` (user:moi, 2026-05-20)
- ~~[→Netteté] Transition de capture sans flash carte : couvrir la bascule validation photo → fiche espèce par un overlay~~ **Livré le 2026-06-12, validé device** : voile plein écran `CaptureTransitionSplash`, décision « 1re espèce » figée avant launch caméra, rendu au-dessus du NavHost — détail dans `CHANGELOG.md` `[1.5.0]` (user:moi, 2026-05-20)
- ~~[→Netteté] Indication de la direction dans le mode Chasse : un point sur le radar indique "l'angle" / la direction de l'arbre remarquable. C'est à ce moment là que le radar "pulse" et que la distance se met à jour.~~ **Livré le 2026-06-12, validé device** : blip north-up (zéro capteur), rayon = distance (mapping log), pulse central sans direction sous 25 m (hystérésis 35 m), refresh théâtralisé au passage de la barre sur le blip — détail dans `ROADMAP.md` item 6 (user:moi, 2026-05-20 ; design arbitré 2026-06-12)

## Cycle Herbier

Enrichissement des fiches espèces (saisonnalité, attributs, picto, photos de référence). Pressenti après Netteté et avant Variantes — dépendance assumée : le calendrier de floraison alimentera la suggestion d'état « en fleur » de Variantes.

- ~~[→Herbier] Upgrade Arboretum (saisonnalité, picto, photos ref) — ligne parapluie~~ **éclatée en 13 sprints S1-S13 le 2026-06-14** (décisions actées : tout d'un coup + cascade photos complète + pas de silhouettes + cellule non capturée garde « ??? »). Plan : `~/.claude/plans/on-attaque-le-cycle-temporal-abelson.md` ; tableau dans `ROADMAP.md`. (user:moi, 2026-05-17)
  - [→Herbier] **S1** Champs API fiches-essences (200) : étendre `fetch_essences()`/`_build_essences_index()` (port, feuillage, taille, expo, indigénat, origine…) → `species-info.json`. Aucune dép. (data)
  - [→Herbier] **S2** Étendre `SpeciesInfo.kt` : forme cible complète des champs nullable (attributs + bitfields calendriers 12 + atouts/limites + textuels) + parsing tolérant + tests JVM. (binding, dép. S1)
  - [→Herbier] **S3** `AttributesBlock` (pills Material 3) sur `SpeciesDetailScreen` — 1er livrable device, valide le pipeline bout-en-bout. (UI, dép. S2)
  - ~~[→Herbier] **S4** Parsing PDF (+`pymupdf`) : calendriers floraison/fructification (bitfields 12) + bloc « À RETENIR » (atouts/limites) + rapport HTML de validation sur les 200. Le gros gain. (data, dép. S1)~~ **livré 2026-07-15** (`7c7edc8`) — couverture flor 199/200, fruct 193/200, atouts/limites 194/200 ; fallback couleur pour 6 fiches ancien template ; rapport `docs/essences/index.html`.
  - ~~[→Herbier] **S5** `SeasonalityCalendar` (strip 12 mois ×2) + « en floraison ce mois-ci » + bloc « À retenir ». Alimente l'état « en fleur » de Variantes. (UI, dép. S2,S4)~~ **livré 2026-07-15** (`ab5c5e7`) — bindings `floraison`/`fructification`/`atouts`/`limites`, contrat bit 0 = janvier miroir Python↔Kotlin, à valider device.
  - ~~[→Herbier] **S6** Parsing PDF : champs textuels restants (famille, hauteur chiffrée, envergure, croissance, longévité, descriptions courtes, encarts éditoriaux, services éco). (data, dép. S4)~~ **livré 2026-07-15** (`335a5a3`) — clés `ess` fam/haut/env/croiss/long + iddesc/paris/svc, couverture 194/200 (écorce 193), scores pictos filtrés au niveau span après un NO-GO de vérif corrigé ; « Paysage et cadre de vie » et Statut écartés (décision Max).
  - ~~[→Herbier] **S7** Affichage champs textuels (enrichir `AttributesBlock` + encarts Ville de Paris, réservés aux 200). (UI, dép. S2,S6)~~ **livré 2026-07-15** (`488ff96`) — pills famille/hauteur/envergure/croissance + ligne Longévité + descriptions d'identification dans `AttributesBlock`, `EssenceParisBlock` (prose + lien PDF + attribution, absorbe l'ancien `EssencePdfBlock`), `ServicesEcoBlock` ; attribution ODbL fiches-essences dans About/NOTICE ; à valider device.
  - ~~[→Herbier] **S8** Cascade attributs 584 : Wikidata SPARQL→POWO→Wikipedia FR infobox→EOL + cache `.taxa-attributes-cache/`. Saisonnalité NLP best-effort haute confiance, jamais inventée. (data, dép. S2)~~ **`[refusé]` 2026-07-15** — sondes réseau réelles : la cascade se réduit à **un seul champ, la famille** (~98 %, 621/631 espèces connues sans `ess`, via traversée Wikidata P171*→rang famille Q35409 + P225, **CC0**, approche genre-centrique). Tout le reste indisponible ou non structuré sur Wikidata (hauteur P2043 = **0 %** ; port/feuillage/taille/croissance absents). POWO/EOL/Wikipedia-infobox écartés (scrapers HTML fragiles + 3 licences pour un gain marginal), saisonnalité NLP écartée (risque d'invention). Rendu concret = grille « Carte d'identité » réduite à 1 cellule Famille sur ~620 fiches → ne justifie pas un sprint (décision Max). **Piste réactivable** : famille-seule Wikidata (genre-centrique, injection `ess={"fam":…}` dans `write_species_info`, **zéro code Kotlin** car `parseSpeciesAttributes`/`SpeciesIdGrid` consomment déjà `fam`). Aucune dép. cassée : S9-S13 ne dépendent pas de S8.
  - [→Herbier] **S9** Photos officielles 200 (+`Pillow`) : `pymupdf.get_images()` → filtre taille → WebP → `assets/species-photos/` + `species-photos.json` (crédit ODbL Ville de Paris). (data, dép. S4)
  - [→Herbier] **S10** Cascade photos 584 : Wikidata P18→iNat, filtre `cc0`/`cc-by` strict → WebP + crédit/licence par image. Surveiller taille APK. (data, dép. S9)
  - [→Herbier] **S11** `ReferencePhotoBlock` + `SpeciesPhotoRepository` + `CREDITS.md` + écran crédits ; réviser phrase `CLAUDE.md` « images Wikipedia absentes ». (UI, dép. S2,S9)
  - [→Herbier] **S12** Fiche détail consultable non capturée : cellules « ??? » tappables (`ArboretumScreen.kt:310,378,394`) → fiche aperçu (photo réf + attributs + calendrier, captures masquées, remarquables verrouillés) ; grille garde « ??? ». (UI, dép. S3,S11)
  - [→Herbier] **S13** Hygiène & clôture item 1 : detekt ≤ baseline 9, tests, `CHANGELOG`, `CLAUDE.md` (deps Python + photos), screenshots, vérif taille APK, commit assets régénérés. (clôture, dép. tous)
- [→Herbier] Texte fallback pour fiches espèces sans Wikipedia. Audit S7 (2026-05-16) : **254/784 espèces identifiées (32 %) sans `summary`**, mais ne couvrent que **7 778/204 364 arbres (3.8 %)** — quasi exclusivement des hybrides/cultivars/sous-espèces (Tilia x europaea, Pinus nigra subsp. nigra, Gleditsia triacanthos f. Inermis, x Cupressocyparis leylandii…). Fallback gratuit dispo via `genre-info.json.summary` (texte Wikipedia du genre, déjà cuit). Lecture pondérée par arbres = sous le seuil ≤ 5 %, lecture par espèce = bien au-dessus — à trancher dans un cycle futur si la friction utilisateur devient sensible. (audit-B 2026-05-06 ; ré-audité 2026-05-16 hors-cycle Boussole ; fold Herbier 2026-05-21)
- [→Herbier] Résidu post-cycle Catalogue : 11 entrées résiduelles avec `nv == binôme nu` et count ≤ 2 (post-fil-rouge S10), botaniquement douteuses — `Ehretia macrophylla`, `Sophora flavescens`, `Betula occidentalis`, `Crataegus japonicum`, `Crataegus baccata`, `Celtis cerasifera`, `Carpinus carpinifolia`, `Phellodendron japonicum`, `Zanthoxylum bungei`, `Alnus formosana`, `Brucea javanica`. Peut-être réelles mais rares à Paris, peut-être saisies erronées. Demandent une recherche botanique pour trancher keep / rebinder (claude:audit-S6, 2026-05-11 ; réduit de 29→11 au S10 ; fold Herbier 2026-05-21)
- [→Herbier] **S13** Refresh des 6 screenshots README (`docs/screenshots/`) — datent de Boussole, les apports Netteté (cône de vision, blip radar, filtres rapides) n'y figurent pas ; nécessite des captures device fraîches. Rangé dans la clôture item 1 (S13 prévoit déjà des screenshots) au triage démarrage Herbier (claude:tour-fin-Netteté, 2026-06-13 ; trié 2026-06-14)
- [ ] Haptique manquant sur le ✕ de fermeture du HuntPanel (`HuntPanel.kt` ~353) — les autres gestes carte (capture, annulation) en ont (claude:tour-fin-Netteté, 2026-06-13)

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
