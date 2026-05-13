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

- [ ] `ui/common/SeasonAmbience.kt` : l'effet particules au changement de saison reste `Animatable`/`tween` → ne joue pas à échelle d'animation = 0 (hors liste du cycle Réveil S4 ; faible priorité — pas de signal manqué, juste l'effet décoratif absent) (claude:S4, 2026-05-12)

## Cycle Polissage (en cours)

Retours utilisateurs + nettoyage. Détail des sprints dans `ROADMAP.md` (*Cycle en cours*).

- [→Polissage] HuntPanel : libellé « Arbre remarquable **non capturé** le plus proche » plutôt que « Arbre remarquable le plus proche » (la cible filtre déjà aux non-capturés, le texte rattrape la sémantique) (user:moi, 2026-05-13) → S1
- [→Polissage] HuntPanel : triple-tap sur le `RadarGlyph` = easter egg persisté (DataStore) qui remplace l'espèce et la qualification du remarquable proche par « ??? ». Par défaut visible, pas de communication UI. (user:moi, 2026-05-13) → S2
- [→Polissage] RemarquablesScreen : « Historique » au lieu de « Liste », ordre `Catalogue → Historique` pour aligner sur `ArboretumScreen` (user:moi, 2026-05-13) → S1
- ~~[→Polissage] Majuscule systématique sur le nom vernaculaire des espèces (« érable plane » → « Érable plane », « franc frêne » → « Franc frêne », etc.) — appliquer à la source via `_capitalize_fr()` déjà défini dans `tools/build_dataset.py` mais jamais invoqué (user:moi, 2026-05-13) → S3~~ → **livré S3** : fonction dédiée `_capitalize_nv` (préfixe `x ` botanique préservé, ex. `x Chitalpa` intact), invoquée à `entry["nv"] = _capitalize_nv(nv)`. Post-build : 0 nv à initiale minuscule hors préfixe `x `, `Quercus canariensis = "Chêne zéen"`.
- ~~[→Polissage] Arboretum : 16 fiches `—` (espèces identifiées sans `pokedexNumber`, count = 0 dans l'OpenData courant — `Eriolobus trilobata`, `Malus communis`, `Styphnolobium japonica`…) à faire disparaître entièrement. Aucun user n'a de capture en zombie, le dénominateur backend `totalEspecesIdentifiees = 782` les exclut déjà → filtre côté UI (`pokedexNumber != null`) suffit. (user:moi + screenshots, 2026-05-13) → S3~~ → **livré S3** : champ dérivé `SpeciesEntry.isActive` (filtre canonique propagé à `SpeciesIndex.genreCount`/`capturedCountInGenre`, `ArboretumScreen`, `GenreDetailScreen`). `SpeciesDetailScreen.catalogueTotal` migré sur `DatasetStats.totalEspecesIdentifiees` (source unique). Compteur 782 certifié partout (max pokedex = 782, range 1..782 gap-free, dataset-stats aligné, README/tips déjà à 782).
- [→Polissage] Splash tip `app.season_tint` (« L'app change discrètement de couleur selon la saison… ») obsolète à supprimer + audit d'autres résidus de tint saisonnier éventuels (user:moi, 2026-05-13) → S1
- [→Polissage] Nettoyage des `.md` (CLAUDE.md, README.md) et des commentaires datés du code (références « S9 Lot B », « livré cycle Catalogue »…) — prépare la lisibilité pour une future transcription iOS (user:moi, 2026-05-13) → S5
- [→Polissage] tips : `dataset.rank_5` (« érable, 6 645 arbres ») fait doublon avec `dataset.iconic.acer_platanoides` (« l'érable plane a 6 645 représentants ») — même espèce, deux phrases. À dédoublonner côté `write_splash_tips`. (audit S6 Réveil, 2026-05-12) → S1

## Cycle Réveil (livré — `1.3.0`, 2026-05-12)

Résumé dans `ROADMAP.md` (*Cycles livrés post-1.0*), détail dans `CHANGELOG.md` `[1.3.0]`. Items du BACKLOG absorbés (laissés en trace barrée) :

- ~~[→Réveil] Animations Compose neutralisées si « échelle d'animation système = 0 » (cas device:moi) : `rememberInfiniteTransition` reste figé (pulse FAB GPS, sway du ColdStartSplash, couronne MiniArbreCrown, hero WelcomeScreen) ; `Animatable`/`animate*AsState`/`AnimatedVisibility`/`Crossfade` snap au lieu d'animer (fades splash/célébration 1re capture, décalage FAB GPS, chiffre de distance de la chasse). (device:moi, 2026-05-12)~~ → **livré S4** : helper `ui/common/FrameClock.kt` (`rememberFrameMillis` / `rememberFrameProgress(durationMs, easing)` / `rememberFramePingPong(periodMs, easing)`, modelés sur la boucle `withFrameNanos` de `RadarGlyph`). Converti : fade-in + sway du `ColdStartSplash`, couronne `MiniArbreCrown`/`MiniArbreItem` (sway + drift + cascade par platane → fonction pure `miniArbrePhase` lue dans le `graphicsLayer`), hero du `WelcomeScreen`, célébration 1re capture (`CelebrationHero` de `SpeciesDetailScreen`). Laissé tel quel et commenté : `AnimatedVisibility` de sortie du voile, micro-shift/pulse des FAB, chiffre de distance de la chasse, `AnimatedContent` de rotation des tips.
- ~~[→Réveil] Écran de chargement carte filtrée (`FilterSplash`) : remplacer « Filtrage de X… » + spinner par un texte au ton du splash principal ; **pas** d'animation platanes (user:moi, 2026-05-12)~~ → **livré S5** (décision révisée avec l'user : le `FilterSplash` *ressemble* au splash principal — `SplashScaffold` privé mutualisé dans `MapOverlays.kt`, hero platane + couronne). « Réveil des **{nv pluriel}** parisiens » (`pluralizeHead`) + `CircularProgressIndicator` discret, sans tips. `FILTER_SPLASH_MIN_MS = 1000`.
- ~~[→Réveil] Bug : la séquence intro de 10 tips ne s'affiche pas à la 1re ouverture post-onboarding, le splash part directement en mode aléatoire — à reproduire sur device avec logcat DEBUG, fix sans casser les invariants de `SplashTipsController` (user:moi, 2026-05-12)~~ → **livré S2** : cause = `ArbresNavHost.startDestination` dérivé de `onboardingDone` → reconstruction du graphe NavHost à chaque changement (`null→false→true` sur install frais) → MapScreen monté 3× → une instance transiente jouait + `markSplashIntroSeen()` avant l'instance stable. Fix : `startDestination` constante `Routes.map()` + redirection vers `WELCOME` via `LaunchedEffect(onboardingDone)`. Invariants `SplashTipsController` intacts.
- ~~[→Réveil] Bug repéré pendant S2 : `computeInitialCamera` bloquait jusqu'à ~30 s sur `getCurrentLocation()` (timeout système, GPS froid en intérieur) **sur le chemin critique** — `map.setStyle(...)` n'est appelé qu'après, donc carte invisible 30 s ; et la caméra ne se recadrait jamais sur le 1er fix (Paris dézoomé jusqu'au tap FAB) (device:moi + claude:logcat S2, 2026-05-12)~~ → **livré S2** : `computeInitialCamera` non-bloquant (lecture pure de `LocationProvider.currentLocation.value`, sinon Paris) + recadrage GPS auto au 1er fix (`LaunchedEffect(mapRef)` qui anime vers le fix, coupé si geste utilisateur / tap cluster / mode filtré / caméra mémorisée restaurée). Cold-start retombé de ~30-37 s à ~1 s en intérieur.
- ~~[→Réveil] Allonger le splash cold-start : il disparaît avant que les pins soient peints (instant « carte sans arbre ») — flip `arbresPrets` après `setArbresGeoJson` + plancher de durée min (~2,5 s) (user:moi, 2026-05-12)~~ → **livré S3** : cause = `GeoJsonSource.setGeoJson(String)` sur source attachée parse+cluster en background (retour immédiat, pins 1-3 s après). Fix = voile opaque jusqu'au rendu effectif (`awaitArbresRendered` qui poll `queryRenderedFeatures` sous `withTimeoutOrNull`) + `awaitSplashFloor` (plancher 2,5 s en cold-start fresh, 0 au remount avec cache enrichi) + `finally { arbresPrets = true }`. `MapScreen.kt` seul.
- ~~[→Réveil] Refresh des valeurs des tips (chiffres dataset périmés post-1.1.0/1.2.0) + outil HTML de revue (`docs/tips/index.html` : tous les tips, verdict RAS / à tuer / chute à réécrire / commentaire, export copiable) + nouveaux tips liés aux évolutions post-1.0 et créations inédites (user:moi, 2026-05-12)~~ → **livré S1** (outil `tools/build_tips_preview.py` → `docs/tips/index.html`) **+ S6** : retours de relecture appliqués — 18 popculture supprimés (« aucun rapport » / doublon `le_forestier` / `brassens` fusionné dans `brassens_marronnier`), ~30 réécritures (intro, history, popculture, dataset), `dataset.median_species` et `dataset.iconic.aesculus_hippocastanum` retirés ; placeholder `{captureCount}` supprimé partout (static, `SUPPORTED_PLACEHOLDERS`, `SplashTipsController`, preview) → 17 tips player `{captureCount}` retirés ; chiffres « X espèces » alignés sur les 782 identifiées (filtre `is_unknown_species` dans `write_splash_tips`) ; nom vernaculaire de genre pour `dataset.diverse_genus` (« le chêne ») ; fixup accents/casse des noms communs CSV (`erable` → `Érable`) ; +15 tips nouvelle catégorie `app` (saisons, badges/familles « Familier », backup ZIP, mode chasse radar, fiches espèce/genre, barres Profil, logos arrondissement, catalogue, map-pulse), +6 history/popculture, +5 player. Total ≈ 231 tips. Tests `SplashTipsTest` dans `tools/test_build_dataset.py`. `splash-tips.json` + `docs/tips/index.html` régénérés.

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_4_5` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)

## Cycle Progression (livré — `1.2.0`, 2026-05-12)

Résumé dans `ROADMAP.md` (*Cycles livrés post-1.0*), détail dans `CHANGELOG.md` `[1.2.0]`. Item du BACKLOG absorbé (laissé en trace barrée) :

- ~~[→Progression] Maîtrise par arrondissement : badge « Maître du Xe » (audit V2#5, 2026-05-06)~~ → livré sous forme de famille de badges binaires `familier_arr_*` (22 badges = 20 arr. + 2 bois ; renommé « Maître » → « Familier » ; carte chromatique restée en Refusé)

## À creuser

- [creuser] Quêtes hebdomadaires locales, opt-in, sans push (audit V2#3, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 quand le cycle a été dissous)
- [creuser] Texte de fallback minimal pour les fiches espèces sans page Wikipedia — pertinence à revérifier après que le cycle Catalogue 1.1.0 a élargi le périmètre couvert (le manque peut s'être réduit suffisamment pour rendre ce filet caduc) (audit-B, 2026-05-06 ; reformulé + déplacé depuis Endgame 2026-05-12)
- [creuser] Résidu post-cycle Catalogue : 11 entrées résiduelles avec `nv == binôme nu` et count ≤ 2 (post-fil-rouge S10), botaniquement douteuses — `Ehretia macrophylla`, `Sophora flavescens`, `Betula occidentalis`, `Crataegus japonicum`, `Crataegus baccata`, `Celtis cerasifera`, `Carpinus carpinifolia`, `Phellodendron japonicum`, `Zanthoxylum bungei`, `Alnus formosana`, `Brucea javanica`. Peut-être réelles mais rares à Paris, peut-être saisies erronées. Demandent une recherche botanique pour trancher keep / rebinder (claude:audit-S6, 2026-05-11 ; réduit de 29→11 au S10)
- [creuser] WelcomeScreen pas lu, intro depuis la carte (user:F&F + user:moi 2026-05-07 : pas prio mais à reconsidérer post-Photos)
- [creuser] Unifier espace lexical Arboretum / Catalogue / Pokédex (audit-E2 : recommande Arboretum pour l'UI)
- [creuser] Refonte modèle remarquables : espèce-boss vs vraie quête (audit V2#2 : décision structurelle)
- [creuser] Notifications push : digest mensuel opt-in vs rien (audit-tension#1)
- [creuser] Phénologie réelle (dates floraison/feuillage par espèce) — décision structurante v2 (audit V2#1)
- [creuser] Étendre screenshots README de 3 à 6 (audit#17 : à faire après Photos pour avoir les nouveaux écrans)
- [creuser] Script `tools/scout_other_cities.py` qui interroge OpenData de villes du Grand Paris et produit un md de faisabilité (user:moi, 2026-05-07)
- [creuser] Mini-quiz ou capacité d'identification entre espèces partageant le même `nc` (Quercus robur vs petraea, Tilia cordata vs platyphyllos) (claude:analyse, 2026-05-08 ; refusé du cycle Catalogue 2026-05-10 — scope dédié, UX du quiz + génération de paires + scoring trop coûteux à empiler)
- [creuser] Aide à l'indentification des genres/espèces
- [creuser] Leaderboard optionnel et minimaliste ?
- ~~[ ] CI : bumper les GitHub Actions sur Node.js 24~~ → livré 1.3.0 (clôture Réveil) : `actions/checkout` v6, `actions/setup-java` v5, `actions/setup-python` v6, `actions/cache` v5, `actions/upload-artifact` v7, `gradle/actions/setup-gradle` v6, `softprops/action-gh-release` v3.
- [ ] CI : `release.yml` — échec transitoire de restauration du cache Gradle (`Failed to restore gradle-home-… : Cache service responded with 400`), sans effet sur le build. À surveiller : si ça récidive, vérifier la conf de `gradle/actions/setup-gradle` (clé de cache / quota) (ci:release.yml run v1.2.0, 2026-05-12)
- ~~[ ] `tools/test_build_dataset.py` : 2 échecs pré-existants~~ → livré 1.3.0 (clôture Réveil) : attendus réalignés sur le pipeline courant (`UNKNOWN_ESPECE_FORMS` inclut `fleur/fruit n. sp.` ; numérotation Pokédex par count décroissant). Suite entièrement verte.

## Refusé

- [refusé] Bouton partage PNG sur fiche espèce (audit-C, tension single-player vs F&F à trancher - trop loin d'un intérêt)
- [refusé] Carte chromatique vert/jaune/gris par arrondissement (audit V2#5, 2026-05-06 ; user:moi 2026-05-12 lors du cadrage Progression : la barre « X / 22 arrondissements visités » dans le Profil + le badge « Maître du Xe » suffisent pour mettre sur la piste, l'overlay chromatique sur la carte serait redondant et chargerait inutilement la vue principale)
- [refusé] Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche espèce non capturée (audit-A, 2026-05-06 ; user:moi 2026-05-09 : philosophie « découverte en marchant », la 81e espèce se trouve en tapant un pin gris à proximité, le côté quête est porté par les Remarquables ★)
- [refusé] Table `photo` 1:N + backup `schemaVersion = 2` (BACKLOG cycle Photos d'origine ; user:moi 2026-05-09 : le modèle Room actuel 1 capture = 1 photo supporte déjà N photos par arbre/espèce via N captures, pas de migration nécessaire)
- [refusé] Cold-start « 7-10 s freeze » signalé par audit (user:moi 2026-05-07 : audit faux, pas de problème de temps long bloquant au 1er lancement)
- [refusé] 4 badges saisonniers v1.1.0 proposés par audit (user:moi 2026-05-07 : caduques, on supprime les saisons)
- [refusé] Mini-transition d'ambiance switch saison (audit-D2 ; user:moi 2026-05-07 : caduque, suppression saisons)
- [refusé] Anticlimax du déblocage des 38 147 platanes (audit-tension#4 ; audit lui-même recommande de laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax)
- [refusé] Pré-affichage fiche remarquable enrichie même non capturée, bandeau « Pas encore découvert » (audit-B, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 — c'est une chasse avec assez d'infos avec la distance)
