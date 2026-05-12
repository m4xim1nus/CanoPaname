# Changelog

Format basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions [SemVer](https://semver.org/lang/fr/).

## [1.3.0] — 2026-05-12

Cycle de polish *Réveil* : écrans de chargement et animations Compose. Six sprints — refresh + outil de revue des splash tips, fix du bug d'intro tips, fix d'un cold-start bloquant ~30 s, splash cold-start qui reste opaque jusqu'au rendu effectif des pins, animations clés rendues insensibles à l'échelle d'animation système, `FilterSplash` réécrit au look du splash principal. Aucune casse de schéma.

### Ajouté

- Outillage : `tools/build_tips_preview.py` → `docs/tips/index.html` — revue de tous les splash tips (placeholders rendus + gabarit brut affichés, verdict `RAS` / `à tuer` / `chute à réécrire` + commentaire libre persistés en `localStorage`, filtre texte, export copiable). Doc dans `tools/README.md`.
- Outillage : `SplashTipsTest` dans `tools/test_build_dataset.py` (ids uniques, `intro` présent, placeholders ⊆ set runtime).
- `ui/common/FrameClock.kt` : `rememberFrameMillis()`, `rememberFrameProgress(durationMs, easing)` (rampe one-shot 0→1 figée à 1f), `rememberFramePingPong(periodMs, easing)` (triangle 0→1→0) — modelés sur la boucle `withFrameNanos` du radar de chasse, insensibles à `MotionDurationScale`.

### Modifié

- `ColdStartSplash` : le voile reste **pleinement opaque jusqu'au rendu effectif des pins** (`awaitArbresRendered` poll `queryRenderedFeatures` sous `withTimeoutOrNull`) et non plus jusqu'au retour de `setArbresGeoJson` (qui parse / cluster en background, pins 1-3 s plus tard) — fin du moment « carte vide ». Plancher de durée minimale `COLD_SPLASH_MIN_MS = 2500` en cold-start fresh (0 au remount avec GeoJSON enrichi en cache). `finally { arbresPrets = true }` pour ne jamais rester coincé sous le voile.
- `FilterSplash` réécrit au look du `ColdStartSplash` : `SplashScaffold` privé extrait dans `MapOverlays.kt` (fond `colorScheme.primary`, hero platane `ic_launcher_foreground` `scale` + `rotationZ=sway`, fondu d'entrée, couronne de 7 mini-platanes flottants — tout le pilotage `withFrameNanos` mutualisé) ; à la place de « Filtrage de X… » + spinner, « Réveil des **{nom vernaculaire pluriel}** parisiens » (nv dans un span 20 sp SemiBold) + `CircularProgressIndicator` discret, sans zone de tips. Pluriel via `pluralizeHead` (`-eau`/`-eu` → `-eaux`/`-eux`, invariant en s/x/z) ; `splashSpeciesLabel` résolu depuis `filteredEntry` (filtre genre → `GenreInfo.nomFr`, filtre espèce → `displayNomCommun`). `FILTER_SPLASH_MIN_MS = 1000`.
- Animations clés repassées en pilotage `withFrameNanos` (insensibles à l'échelle d'animation système = 0) : fade-in + sway du `ColdStartSplash`, couronne `MiniArbreCrown`/`MiniArbreItem` (sway / drift / cascade par platane → fonction pure `miniArbrePhase` lue dans le `graphicsLayer`, la couronne ne recompose plus par frame), hero du `WelcomeScreen` (respiration gris↔vert), célébration 1re capture (`CelebrationHero`). Laissés en l'état (snap acceptable) : sortie du voile, micro-shift / pulse des FAB, chiffre de distance de la chasse, rotation des tips.
- Splash tips : banque rafraîchie (≈ 231 tips). Chiffres dataset réalignés post-1.1 / 1.2 (782 espèces identifiées, 930 entrées catalogue, 217 042 arbres). +15 tips d'une nouvelle catégorie `app` (saisons calendaires, badges binaires + familles « Familier des/du … », backup ZIP, mode chasse radar, fiches espèce & genre, barres de progression du Profil, logos d'arrondissement, catalogue, map-pulse) + 6 history/popculture + 5 player. 18 tips popculture supprimés (hors-sujet ou doublons), ≈ 30 réécritures. Placeholder `{captureCount}` retiré partout (`splash-tips-static.json`, `SUPPORTED_PLACEHOLDERS`, `SplashTipsController`, preview → 17 tips player concernés supprimés) ; le set runtime restant est `{speciesCount, remarquableCount, daysSinceFirst}`. Fixup accents / casse des noms communs CSV (`erable` → `Érable`).
- `ArbresNavHost.startDestination` passé en **constante** (`Routes.map()`) ; la redirection vers `WELCOME` est désormais portée par un `LaunchedEffect(onboardingDone)` — le graphe n'est plus reconstruit quand `onboardingDone` change.
- CI : actions GitHub bumpées sur le runtime Node 24 (`actions/checkout` v6, `actions/setup-java` v5, `actions/setup-python` v6, `actions/cache` v5, `actions/upload-artifact` v7, `gradle/actions/setup-gradle` v6, `softprops/action-gh-release` v3).

### Corrigé

- La séquence intro de 10 tips ne jouait pas au tout 1er lancement post-onboarding (le splash partait directement en mode aléatoire). Cause : `startDestination` dérivé de `onboardingDone` → reconstruction du graphe `NavHost` à chaque changement (`null → false → true` sur install frais) → `MapScreen` monté 3×, une instance transiente jouait l'intro **et** appelait `markSplashIntroSeen()` avant que l'instance stable ne lise le flag. Fix via la `startDestination` constante (cf. *Modifié*) ; invariants de `SplashTipsController` intacts (`.first()` figé, pas de `collectAsState` sur `splashIntroSeen`, keys minimales).
- Cold-start de ~30-37 s en intérieur (GPS froid) : `computeInitialCamera` faisait un `getCurrentLocation()` **bloquant** (timeout système ~30 s) sur le chemin critique avant `map.setStyle(...)`, et la caméra ne se recadrait jamais sur le 1er fix. Rendu non-bloquant (`LocationProvider.currentLocation.value ?: parisCamera()`, plus `suspend`) + recadrage caméra automatique au 1er fix GPS (`LaunchedEffect(mapRef)`, coupé si geste utilisateur / tap cluster / mode filtré / `pulseArbreId` / caméra mémorisée restaurée). Cold-start retombé à ~1 s en intérieur.
- `tools/test_build_dataset.py` : 2 attendus de test réalignés sur le pipeline courant (`UNKNOWN_ESPECE_FORMS` inclut `fleur n. sp.` / `fruit n. sp.` depuis le cycle Catalogue ; numérotation Pokédex par count décroissant). La suite repasse entièrement au vert.

### Privacy

- Inchangé : 100 % local, aucune télémétrie, aucun service tiers au runtime.

## [1.2.0] — 2026-05-12

Refonte de l'expression de la progression sous le codename *Progression*. Six sprints : le FAB ★ devient un mode chasse persistant ; Profil et Badges sont séparés conceptuellement (progression chiffrée en barres sur le Profil, badges désormais tous binaires) ; deux familles de badges dynamiques « Familier » émergent du dataset (un genre avec ≥ 7 espèces identifiées, les 20 arrondissements + 2 bois). En corollaire, le cycle « Endgame » disparaît comme cycle nommé — sa pièce maîtresse (maîtrise par arrondissement) est absorbée ici.

### Ajouté

- Map : **mode chasse Étoile** (`HuntPanel.kt`). Le tap ★ ouvre un panneau bas pleine largeur — radar animé, nom + qualification glosée du remarquable non découvert le plus proche, distance live rafraîchie toutes les 5 s en phase avec le balayage du radar (pulse au refresh), ✕ au même emplacement que le FAB ★. Cible **dynamique** recalculée à chaque tick ; fermeture auto à la sortie de l'écran ; FAB GPS / snackbars décalés au-dessus du panneau ; cas « tous découverts » dédié. Radar + pulse pilotés par `withFrameNanos` (insensibles à l'échelle d'animation système). Plus de snackbar éphémère « remarquable proche ».
- Badges : famille **« Familier des … »** — capturer toutes les espèces identifiées d'un genre. Un genre a un badge ssi il a ≥ `GENRE_FAMILIER_MIN_SPECIES` (= 7) espèces identifiées → **26 badges** (libellés au nom vernaculaire pluriel via `GenreInfo.nomFr`, jamais le binôme latin).
- Badges : famille **« Familier du … »** — capturer toutes les espèces recensées dans l'arrondissement (volontairement aspirationnel) → **22 badges** (20 arr. + 2 bois). Dénominateurs précalculés au build dans `assets/arr-species.json` (slug `ArrKey` → liste de `speciesIndex`, miroir Python `arr_key_slug` ↔ Kotlin `parseArrKey`) ; loader `ArrSpeciesIndex` ; couverture propagée par `effectivelyCapturedSpecies` (capturer un chêne couvre `Quercus sp.`).
- Profil : ligne **« X jours depuis ta première capture »** en tête (Fraunces, masquée à 0 capture) + carte **Progression** = jusqu'à **7 barres** Material 3 (`LinearProgressIndicator` + `X / Y · Z %`) : arbres déverrouillés, remarquables capturés, espèces capturées (sous-texte « + N indéterminées »), genres découverts, genres complétés, arrondissements visités (`/22`), arrondissements complétés. Une barre à `0 / N` est entièrement masquée — la liste se densifie avec la progression.
- Badges binaires neufs : `PREMIERE_CAPTURE` (lance la section Badges du Profil dès la 1re capture) ; symétriques démesure `BONSAI` (< 2 m) et `JEUNE_POUSSE` (< 10 cm de circonférence), seuils calés sur la distribution dataset ; `ESPECE_RARE` éclaté en 5 badges de rareté exacte — `Unique` (1 ind.), `Couple` (2), `Trinité` (3), `Quatuor` (4), `Quintette` (5).
- Badges : logos d'arrondissement = **chiffre romain** (I…XX) rendu en texte Fraunces dans le cercle (« Boulogne » / « Vincennes » pour les 2 bois) ; badges de genre = icône partagée `Icons.Outlined.Forest`. `BadgeDef.visual()` (`BadgeVisual.Vector` / `.Label`), cercle `BadgeIconCircle` extrait en composable partagé (`BadgesScreen` ↔ rangée « Derniers badges » du Profil).

### Modifié

- Badges : `BadgeState` aplati en `data class(def, unlockedAt)` — démantèlement de `BadgeState.Progressive`, `TierDef`, `BadgeTier`, `unlockProgressive`, `ProgressiveBadgeCard`. `BadgeEvaluator.evaluate(...)` ne fait plus que des `unlockBinaryOnce` et renvoie `Map<String, Long>` (id → ts de déblocage) ; `BadgeRepository` assemble le catalogue complet via `BadgeCatalog.full(speciesIndex, genreInfo, arrSpecies)` puis zippe. `unlockedAt` figé sur la capture déclenchante (balayage chronologique unique). Aucune migration Room (badges dérivés à la volée).
- `BadgeCatalog.ALL` = 10 binaires statiques ; le compteur du `BadgesScreen` se base sur `badgeRepo.catalog.size` (10 + 26 + 22 selon le dataset).
- Profil : `StatsCard` (tableau plat) remplacée par la ligne « X jours » + la carte Progression. Ligne « Captures totales » supprimée. Titre de section « Infos » (style `titleLarge`) inséré entre « Voir tous les badges » et « Comment jouer ».
- Map : icône / comportement du FAB ★ — popup éphémère → mode chasse persistant (`huntActive` en `remember` côté `MapScreen`).
- Build : `detekt {}` de `app/build.gradle.kts` câble enfin `baseline = file("$projectDir/detekt-baseline.xml")` (le fichier était committé mais ignoré) ; baseline régénérée post-S5. `./gradlew detekt` repasse au vert.

### Retiré

- Badges progressifs `Marcheur` / `Botaniste` / `Chasseur` + `MOSAIQUE_QUERCUS` (l'identité Quercus revient sous forme de badge binaire « Familier des chênes ») ; `TOURNEUR_DE_PARIS` / `TOUR_COMPLET` (redondants avec la barre arrondissements du Profil + les badges « Familier du Xe »).
- `BadgeEvaluator` : param `speciesIndex` sans consommateur, retiré (idem côté `BadgeRepository` avant réintroduction pour `BadgeCatalog.full`).
- `SpeciesDetailScreen` : param `onSpeciesClick` + val `capturedSpecies` morts depuis le déménagement des fiches `(G, sp.)` vers `GenreDetailScreen` (cycle Catalogue), supprimés + call site `ArbresNavHost`.

### Corrigé

- Detekt : les 3 findings réellement neufs corrigés plutôt que figés dans la baseline — vals/params morts de `SpeciesDetailScreen`, `frères` → `freres` dans `SpeciesIndex` (`VariableNaming`).

## [1.1.0] — 2026-05-11

Refonte du catalogue d'espèces sous le codename *Catalogue*. Dix sprints : nettoyage data amont (drops `Non spécifié`, normalisation `sp.`, fixups latins), cascade de noms vernaculaires français (Wikidata P1843 → Wikipedia frTitle filtré → redirects API → overrides éditoriaux → construction), fiches genre dédiées, Arboretum à 2 niveaux *Catalogue* / *Historique*, badge progressif *Mosaïque de chênes*. Refresh OpenData absorbé en passant (217 042 arbres, 183 remarquables). Fil rouge en clôture : 11 fixups de typos / synonymes désuets supplémentaires côté `SPECIES_FIXUPS` + marqueurs Prunus cultivars basculés en `sp.`.

### Ajouté

- Catalogue : 930 entrées dont 782 espèces identifiées et 132 entrées genre `(G, sp.)`, noms français uniques garantis par assert d'unicité au build, numérotation Pokédex `#N` stable sur les identifiées.
- Fiches genre dédiées (`GenreDetailScreen`, route `Routes.GENRE`), 203 genres couverts (200 avec espèces identifiées + 3 only-unknown : Genista, Vitex, Ziziphus). Asset `genre-info.json` avec section *« À Paris »* (count cumulé, hauteur / circonférence médianes, proportion du dataset, top arrondissements sur-représentés). Pipeline `compute_genre_info` côté `tools/build_dataset.py` + cache `tools/.wikipedia-cache/` ~200 articles Wikipedia FR.
- Arboretum : 2 niveaux de `SegmentedButton`. Niveau 1 *Catalogue* / *Historique* (= ex-Découverte). Niveau 2 sous *Catalogue* : *Par fréquence* (count Paris décroissant) / *Par genre* (groupé alphabétique). Headers de chapitre cliquables → fiche genre. HeaderCard affiche `N / 203 genres découverts`. Compteur principal `X / 782` (espèces identifiées seules).
- Auto-débloquage des fiches `(G, sp.)` : capture d'un `Tilia X` quelconque débloque la fiche `Tilia (espèce indéterminée)`. La galerie photos reste alimentée par les seules captures explicites de `sp.`.
- Carte filtrée par genre (fiche `(G, sp.)` → bouton « Voir sur la carte ») : extension de `MAP_FILTERED` à un set de `sk`. Affiche les pins gris des `sp.` à résoudre + les pins verts des espèces du genre déjà capturées.
- Badge progressif *Mosaïque de chênes* (3 / 5 / 10, paliers Bosquet / Chênaie / Forêt, exclut `Quercus sp.`). Catalogue passe à 9 badges (5 binaires + 4 progressifs, 25 paliers).
- Overrides éditoriaux `VERNACULAR_OVERRIDES` (Marronnier commun, Sophora du Japon, Merisier, Thuya géant, etc.) : ~30 noms français curés à la main pour les espèces les plus capturables.
- Rapport HTML self-contained `docs/dataset/index.html` pour validation visuelle du catalogue (sources `nv`, contrôles cas limites, catalogue des 202 genres triables).
- Sanity checks au build (raises) : espèce > 100 perdant sa page WP entre deux builds, `sk` existant disparaissant, entrée `Non spécifié` recevant des arbres (count > 0), `nv` non-unique ou redondant `{g} {e} ({g} {e})`. Warn pour fallback construit sur espèce > 1000 captures.

### Modifié

- Dataset OpenData refreshé : **217 042** arbres (vs 213 042), **183** remarquables (vs 169).
- `species-index.json` : nouveaux champs `nv` (nom vernaculaire unique), `n` (numéro Pokédex stable), `u: true` (flag `unknownSpecies`). Rétrocompat asset legacy maintenue côté `SpeciesIndex` (lecture tolérante).
- `dataset-stats.json` : nouveau champ `totalEspecesIdentifiees`.
- Arboretum : titre des cards = `nv`, sous-titre = binôme latin italique (si différent). Cards `unknownSpecies` visuellement distinctes, toujours en fin de catalogue, sans `#`.
- `BadgeEvaluator` : signature évoluée pour recevoir `SpeciesIndex`. Botaniste exclut désormais les `sp.` (intentionnel — les espèces indéterminées ne comptent pas comme nouvelle espèce identifiée).
- `SPECIES_FIXUPS` étendu : `Olea europea` → `europaea`, Styphnolobium japonicum, Eriobotrya japonica, Ligustrum vulgare, Ulmus parvifolia, Ulmus minor, Platanus x hispanica (S8) ; puis 9 ajouts en clôture (S10) — Sorbus padus → Prunus padus, Rhus verniciflua → Toxicodendron vernicifluum, Eriolobus trilobata → Malus trilobata, Populus canadensis → x canadensis, Sequoiadendron sempervirens → Sequoia sempervirens, Robinia ornus → Fraxinus ornus, Robinia pseudocamellia → Stewartia pseudocamellia (nouvelle entrée sk=929), Fagus purpurea → Fagus sylvatica, Malus communis → Malus domestica. 16 typos / synonymes désuets corrigés au build (~12 arbres OpenData rebindés).
- `UNKNOWN_ESPECE_FORMS` : ajout des marqueurs OpenData `Fleur n. sp.` et `Fruit n. sp.` (cultivars Prunus génériques décoratifs / fruitiers) — 48 arbres basculés vers l'entrée `Prunus sp.` au lieu de polluer le catalogue identifié.
- Cascade `nv` : filtre `frTitle != binôme` (récupère ~50 % des articles WP titrés scientifiquement) + étape Wikipedia redirects API (cache permanent `tools/.wikipedia-aliases-cache/`, ~80 % des cas restants type « Marronnier d'Inde », « Pin noir »).

### Retiré

- 811 arbres `Non spécifié` (4 formes : `sp.`, `n. sp.`, espece vide, `americana` aberrant) — drop dur côté pipeline. Plus de bruit sans valeur de jeu dans le catalogue.

### Corrigé

- Hotfix tri *Par fréquence* du catalogue : count décroissant (et non croissant).

## [1.0.2] — 2026-05-10

Profondeur et lisibilité après v1.0.1, sans casse de schéma. Six sprints atomiques sous le codename *Photos et progressivité* : re-capture + suppression de captures, refonte `PhotoLightbox` (bornes zoom/pan, swipe entre photos), refonte badges en multi-paliers visibles (catalogue 13 → 8, 22 paliers cumulés), saut vers l'arbre exact sur la carte (fly-to + pulse), galerie photos cliquable dans le sheet de détail arbre.

### Ajouté

- Sheet détail : bouton « Recapturer » dès qu'une espèce ou un remarquable est débloqué (modèle Room déjà N captures par arbre, pas de nouveau schéma).
- Galerie photos : long-press sur une vignette ou icône poubelle dans la lightbox plein-écran ouvrent un dialog de confirmation. Si c'est la dernière capture de l'espèce / du remarquable, le dialog prévient du re-verrouillage et la suppression renvoie sur la Map.
- Badges : 3 badges progressifs multi-paliers `Marcheur` (1/10/25/50/100/250 captures), `Botaniste` (1/10/25/50/100/200 espèces), `Chasseur` (1/5/10/25/50 remarquables). Card pleine largeur avec barre + jalons et score absolu (« 37 / 50 »). Catalogue passe de 13 à 8 badges (5 binaires + 3 progressifs, 22 paliers au total).
- Saut vers un arbre exact sur la carte depuis ses points de contact : bouton « Voir sur la carte » plein écran sur la fiche-remarquable, icône Map en haut à gauche de la `PhotoLightbox` (universelle, fiche-espèce comme fiche-remarquable). La nav passe par `Routes.map(arbreId)` (query param `pulseArbreId`), qui déclenche un fly-to ~600 ms à zoom 20 (très fort zoom — un seul pin à l'écran, aucun doute sur l'individu ciblé) et un pulse blanc 2 s sur la position. Pas d'ouverture du sheet : on tape l'arbre soi-même si on veut la fiche.
- Sheet détail arbre : galerie photos cliquable (`PhotoGallery` avec vignettes 120 dp et titre « Tes photos (N) ») en lieu et place du texte « N photo(s) de capture ». Click → `PhotoLightbox` plein écran (zoom/pan/swipe). Long-press → dialog suppression avec wording adaptatif (re-verrouillage si dernière capture de l'espèce ou du remarquable, recompose en `UnknownContent` via les Flows réactifs).

### Modifié

- `PhotoLightbox` : pinch-zoom toujours 1×→5× avec pan désormais clampé aux bords (calcul `boxSize × ratio bitmap × scale`), plus de photo qui s'évade en vignette dans un coin. Galerie ≥ 2 photos : navigation entre photos via `HorizontalPager` (swipe horizontal + chevrons `Outlined.ChevronLeft/Right` désactivés aux bornes), pager gelé dès `scale > 1f`. Détecteur custom `awaitEachGesture` qui ne consomme rien à 1 doigt + scale=1 (laisse passer le pager) et reset auto du zoom à la transition de page.
- Profil : « Derniers badges » liste maintenant les 3 derniers événements de déblocage (chaque palier de progressif compte indépendamment).
- Compteur global de l'écran Badges passe de « X / 13 » à « X / 22 » paliers — progression plus continue qu'un saut binaire.

### Retiré

- 8 badges binaires absorbés par les nouveaux progressifs : `FIRST_CAPTURE`, `PROMENADE`, `MARCHEUR`, `CENTURION` (→ `Marcheur`) ; `BOTANISTE_AMATEUR`, `BOTANISTE_CONFIRME` (→ `Botaniste`) ; `CHASSEUR_REMARQUABLES`, `LEGENDE` (→ `Chasseur`). Pas de migration Room (les badges sont calculés depuis les captures).

## [1.0.1] — 2026-05-09

Patch dette + UX. Cinq sprints atomiques sous le codename *Vérité & Friction*. Aucune casse de schéma, aucune nouvelle feature visible.

### Ajouté

- Map : pulse FAB GPS + snackbar « Localisation en cours… » pendant le gap permission → 1er fix (timeout 30 s avec warning « GPS indisponible — sors à découvert »).
- Map : reprise GPS automatique quand l'utilisateur réactive la localisation système (BroadcastReceiver `PROVIDERS_CHANGED_ACTION`).
- Map : ring orange autour des clusters contenant ≥ 1 remarquable capturé (mémorisation visuelle, indépendant du fill `discovered_count`).
- Détail arbre : haptique `LongPress` à l'ouverture du sheet.
- Capture : snackbar + tic discret à l'annulation de l'app caméra.
- Profil : compteurs « Espèces du Catalogue » `X / 907 (Y %)` et « Arbres déverrouillés » `X / 213 042 (Y %)`.
- Profil : section « Derniers badges » (row des 3 plus récents `unlockedAt`, masquée si vide).
- Profil : label texte + timeout 60 s sur la barre de progression export/import.

### Modifié

- Documentation : mention explicite des 528 fiches espèces enrichies (sur 907) dans le pitch ; précision « tuiles OpenStreetMap via OpenFreeMap, sans envoi de données personnelles » dans la section vie privée du README.
- Fiches remarquables enrichies (qualification, résumé, description, cultivar) accessibles après capture.
- Map : icône FAB ★ passe de la loupe à l'étoile (`Outlined.Star`, tint orange remarquable).
- Map : snackbar distance remarquable allongée à 5 s (helper `showSnackbarFor` partagé).
- Détail arbre : copy `UnknownContent` rappelle la mécanique de déverrouillage par espèce ; `CaptureAvailability.TooFar` affiche la distance courante (« Trop loin (X m / max 30 m). Rapproche-toi. »).
- Capture : haptique déplacée du post-INSERT vers le tap « Capturer » (perçu immédiat avant l'ouverture caméra).
- Empty state des écrans de découverte : `bodyMedium` (14 sp) → `bodyLarge` (16 sp).
- Profil : remplacement de la card unique « Première capture » par la section « Derniers badges ».

### Retiré

- UI saisonnalité (sélecteur de saison sur Arboretum / Remarquables / Profil, bandeau d'archive, ambiance saisonnière sur la carte). Le schéma DB et l'enum `Season` sont conservés pour le futur cycle Variantes.
- 2 badges saisonniers `RONDE_DES_SAISONS` et `ANNEE_COMPLETE` (catalogue passe de 15 à 13 badges).
- Bullets `welcome_bullet_*` du WelcomeScreen (déclarés dans `strings.xml` mais jamais rendus depuis longtemps).

### Corrigé

- Profil : la 1re capture posée la veille au soir s'affiche désormais « il y a 1 jour » au lieu d'« aujourd'hui » (bascule `LocalDate` + `ChronoUnit.DAYS.between` zone Europe/Paris, plus de fenêtre 24 h glissante).

## [1.0.0] — 2026-05-05

### Ajouté

- Carte plein écran de 213 042 arbres parisiens (clusters MapLibre, OpenFreeMap).
- Capture par proximité GPS (< 30 m) + photo locale.
- Arboretum à 907 espèces avec fiche enrichie (Wikipedia FR, stats Paris, mini-carte filtrée).
- Pokédex remarquables dédié (169 fiches, lien fiche PDF Ville de Paris).
- 15 badges en 6 catégories, évalués depuis les captures.
- Saisonnalité 4 saisons, mode archive read-only.
- Profil avec stats Global / Saison vive.
- Export / import ZIP local (Storage Access Framework, dédup idempotent).
- Onboarding + écran « Comment jouer » rejouable.
- Splash cold start avec tips informatifs rotatifs.
- Coloration progressive des clusters carte selon découvertes.

### Privacy

- 100 % local. Aucune télémétrie, aucun compte, aucun service tiers au runtime.
