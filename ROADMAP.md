# Roadmap

App perso, pas de calendrier engageant. Phases ordonnées du plus pragmatique au plus ambitieux. Tout est négociable. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé.

## Phase 0 — Scaffold ✅

Squelette Gradle/Kotlin/Compose, NavHost, MapLibre, Room, GeoJSON, icône, build/install GrapheneOS via ADB et Android Studio.

## Phase 1 — MVP « voir les arbres autour de moi » ✅

213 042 arbres réels de Paris affichés et tappables (217 855 lignes du CSV OpenData filtrées des ~4 800 sans `genre`/`espece` connus). Style OpenFreeMap, MapLibre 11.11.0, géoloc native (`LocationManager`, sans GMS), Room avec index `(latitude, longitude)`, source GeoJSON clusterisée, hit-test à deux niveaux.

## Phase 1.5 — Polish carte ✅

Sprint A (zoom auto + ModalBottomSheet) et Sprint B (`docs/vision-jeu.md` posant la philosophie « Pokémon GO épuré » : capture par proximité GPS + Pokédex/Arboretum, sans combats, raids, social, anti-cheat).

## Phase 2 — Capture et collection ✅

Sprint C (capture photo + GPS + Room, MIGRATION_1_2, Arboretum, découverte par espèce) et Sprint D (correctifs `Expression.match()`, sealed `CaptureAvailability`, insets FABs, pin orange remarquable capturé).

## Phase 2.5 — Profondeur Arboretum ✅

Cold start masqué (splash natif + overlay Compose, 40-50 s → maîtrisé), fiche-espèce dans l'Arboretum (Wikipedia FR 528/907, stats Paris, lien externe), Sprint E (drift GPS fixé, mini-carte → carte filtrée, fiche-arbre→espèce, célébration 1re capture, vue Pokédex), Sprint F (fiche enrichie remarquables : qualification + résumé + description + cultivar), Sprint G (Pokédex remarquables dédié, FAB ★ Liste + 🔍 Loupe, lien fiche PDF Ville de Paris 169/907).

## Sprint H — Profil et premier badge ✅

Branche `claude/go-sprint-h-foH26` (en attente de merge dans `main`).

`ProfileScreen.kt` accessible en TopStart de la carte. Stats : jours depuis 1re capture, # espèces capturées, # remarquables capturés. Section badges minimale avec « 1re capture » (silhouette grise tant que non débloqué).

## Sprint I — Saisonnalité ✅

Branche `claude/go-sprint-h-foH26` (en attente de merge dans `main`).

4 saisons calendaires fixes (`WINTER` / `SPRING` / `SUMMER` / `AUTUMN`), `SeasonStore` singleton, `SeasonSelector` pill compacte (TopAppBar Arboretum/Remarquables, Row TopStart carte), `ArchiveBanner` plein-écran en mode archive read-only, flows scopés saison sur Arboretum/Remarquables/carte, recoloration carte au switch, profil saisonnier (segmented Global / Saison vive). Captures s'accumulent dans le bucket de leur saison sur toutes les années — l'année prochaine, retour des progrès.

## Phase 3 — Revue graphique ✅

Branche `claude/phase-3-design-update-hKgD6` (en attente de merge dans `main`).

Icône launcher platane parisien + variante monochrome Themed Icons. Tokens couleur centralisés dans `theme/Color.kt` + `ArbresColors` (or, écorce, feuille…) via `staticCompositionLocalOf`. Fraunces SemiBold (~70 Ko OFL) sur display/headline/titleLarge. Splash cold-start animé pure Compose (sway sinusoïdal + cascade fade+scale + wordmark). `FilterSplash` dédié `MAP_FILTERED` (« Filtrage de {nom}… »). Iconographie homogène Outlined. Tinting saisonnier discret du surface (light + dark).

## Phase 4 — Page Badges & succès dédiée ✅

Entrée nav distincte du Profil (le Profil garde son badge unique « 1re capture » de Sprint H et expose un lien « Voir tous les badges → »). Pas d'XP, pas de classement.

- [x] **Catalogue de 15 badges** ✅ — déclarés en `BadgeDef` (id, label, description, `BadgeCategory`) dans `data/Badge.kt`, regroupés en 6 catégories (Découverte, Botanique, Géographie, Remarquables, Saisons, Démesure). Pas de table Room : tout est dérivé des captures à la volée par `BadgeEvaluator`.
- [x] **`BadgeEvaluator` pure** ✅ — fonction `evaluate(captures, arbresById, speciesInfo): List<BadgeState>`. Balayage chronologique unique (O(n×b), n captures = quelques centaines en pratique, b = 15) qui maintient des accumulateurs (espèces vues, arrondissements, saisons, YearMonths, count, remarquables). Le timestamp de la capture qui fait basculer le critère est figé comme `unlockedAt`. « Année complète » = recherche d'une fenêtre 12 mois consécutifs dans les YearMonths capturées (zone Europe/Paris). Arrondissement parsé depuis `Arbre.adresse` via regex `, (\d+)(er|e)$` cohérente avec `tools/build_dataset.py:normalize_arr`.
- [x] **Batch fetch arbres** ✅ — `ArbreDao.arbresParIds(ids)` + `ArbreRepository.arbresParIds(ids): Map<Long, Arbre>`. Évite N requêtes pour les badges qui dépendent des caractéristiques (Géant > 30 m, Vieux sage > 400 cm, arrondissements, espèce rare via `SpeciesInfo.stats.count < 100`).
- [x] **Écran `BadgesScreen`** ✅ — route `Routes.BADGES`, accédée depuis le Profil via card `AllBadgesEntry`. Layout : un seul `LazyVerticalGrid` 3 colonnes avec items spans full-width pour l'en-tête (« X / 15 débloqués ») et les titres de section (« Débloqués » / « À débloquer »). Cards homogènes : icône Outlined par badge (mapping `BadgeDef.icon()` dans `ui/badges/BadgeIcons.kt`), libellé + critère toujours visibles, date d'obtention en bas si débloqué, silhouette `Lock` grise sinon. Couleurs cohérentes avec ProfileScreen : `tertiaryContainer` débloqué / `surfaceVariant` verrouillé.

## Phase 5 — Export / import (backup local) ✅

Le seul moyen de ne pas tout perdre lors d'un changement de téléphone ou d'une désinstallation. Single-player, partage manuel : fichier zip qu'on déplace soi-même. Implémentation dans `app.arbre.backup` (BackupExporter, BackupImporter, BackupModels, BackupFilename) — sérialisation `org.json` (cohérent avec les autres datasets, pas de nouvelle dep). UI dans `ProfileScreen` section « Sauvegarde ».

- [x] **Export** ✅ — bouton dans le Profil. Génère `arbres-export-yyyyMMdd.zip` contenant :
  - `captures.json` : sérialisation des captures (sans `id` Room autoincrement, qui n'a pas de sens cross-device).
  - `photos/` : copie des fichiers JPEG sous leur nom UUID.
  - `meta.json` : versionCode/Name de l'app, `schemaVersion = 1`, count captures, `exportedAt`.
  Écriture via Storage Access Framework (`ACTION_CREATE_DOCUMENT` MIME `application/zip`) — l'utilisateur choisit où enregistrer.
- [x] **Import** ✅ — bouton dans le Profil. `ACTION_OPEN_DOCUMENT` filtré sur `application/zip` + `application/octet-stream` (certains file managers exposent .zip différemment). Valide `meta.json`, refus dur si `schemaVersion > CURRENT_SCHEMA_VERSION`. Copie les photos dans `getExternalFilesDir(null)/captures/`, INSERT les captures non-dupliquées via `CaptureDao.captureExists(arbreId, timestamp)`. Photo absente du zip → capture insérée quand même + compteur `photosMissing` remonté.
- [x] **Politique de fusion** ✅ — additif uniquement. Idempotence via dédup `(arbreId, timestamp)` : ré-importer le même zip ne crée pas de doublon. Pas de transaction Room enveloppante volontairement (un import partiel laisse les captures déjà ingérées, dédup garantit la reprise).

## Phase 5.5 — Onboarding ✅

Constat : un nouvel utilisateur arrivait sur une carte 100 % grise sans explication, et la permission GPS n'était demandée qu'au tap du FAB GPS. Corrigé via un `WelcomeScreen` minimal, marker DataStore et nouveau rationale GPS.

- [x] **`OnboardingStore`** ✅ — DataStore Preferences (un seul booléen `done`). Première ouverture (flag absente) → `false`. `markDone()` appelé une fois par le NavHost après que l'utilisateur clique « Commencer ». Singleton dans `ArbresApp`, helper `rememberOnboardingStore()` dans `RepositoryProvider`.
- [x] **`WelcomeScreen`** ✅ — un seul écran scrollable. Hero (logo platane Outlined dans cercle `feuilleSombre` + or), titre Fraunces SemiBold, accroche, 4 BulletCards (commencent gris, capture < 30 m, capture déverrouille genre, remarquables = chasse spéciale), note privacy (« tout reste sur ton téléphone »), bouton « Commencer ». Au clic : check `ACCESS_FINE_LOCATION`, `RequestPermission()` si besoin, `onContinue()` quel que soit le résultat (granted ou denied — l'utilisateur retentera via le FAB GPS de la carte).
- [x] **NavHost startDestination conditionnelle** ✅ — `rememberOnboardingStore().onboardingDone.collectAsState(initial = null)` ; `WELCOME` si false, `MAP` sinon. `null` (transitoire DataStore) → fallback `MAP`, le splash overlay du MapScreen couvre déjà l'écran. Routes ajoutées : `WELCOME`, `WELCOME_REPLAY`.
- [x] **« Comment jouer » dans le Profil** ✅ — `HowToPlayEntry` Card cliquable (icône `HelpOutline`) sous le bloc Badges. Navigue vers `WELCOME_REPLAY` (mode `readOnly = true` avec bouton « Fermer » au lieu de « Commencer »). Utile pour family & friends qui prennent le téléphone.
- [x] **Rationale GPS réécrit** ✅ — `permission_location_rationale` passe de « L'app a besoin de votre position pour afficher les arbres autour de vous. » à « Active la position pour capturer les arbres autour de toi (moins de 30 m). Tout reste sur ton téléphone. » Cohérent avec le ton tutoiement du WelcomeScreen.

## Idées en vrac (non engageantes)

- **Lien Wikidata pour les espèces avec QID mais sans page FR** — pour les 175 résolues mais sans `frTitle`, afficher dans la fiche un lien `https://www.wikidata.org/wiki/{qid}` plutôt que le placeholder. 1 ligne UI, gain réel.
- **Search / filtres dans l'Arboretum** — barre de recherche (nom commun, binomial) + filtres (capturé / non, remarquable, par famille). Confort à 907 espèces.
- **Timeline des captures** — écran liste par date décroissante (photo + espèce + lieu).
- **Stats avancées Profil** — heatmap calendaire, top arrondissements parcourus, graphes par saison.
- **Recadrage du pitch « Pokémon GO »** — la métaphore promet du social/compétitif que le scope single-player ne tient pas. Reformuler en « carnet de bord naturaliste gamifié » dans README + accroche WelcomeScreen, aligner la copy de l'onboarding et le ton des célébrations. Décision d'identité produit, pas de code.
- **Calendrier 12 cases pour le badge « Année complète »** — rendre la progression visible passivement dans le Profil (case par mois, ✓ si une capture existe dans le bucket YearMonth). Sans notif système, juste un état affiché. Capitalise sur le seul critère qui force la rétention long-terme.
- **Refactor du hub navigation carte** — aujourd'hui 3 FABs (GPS, ★, Arboretum) + Profil TopStart + SeasonSelector + ArchiveBanner. Capacité presque saturée. Avant la prochaine grosse feature, prévoir une bottom bar à 3 entrées (Carte / Arboretum / Profil) ou un drawer, plutôt que d'empiler un 4e bouton. À faire à froid, pas en panique.
- **Sound design opt-in** — l'app est aujourd'hui muette partout. 3-4 field recordings courts (~500 ms) pour capture confirmée (froissement de feuilles), badge unlock (craquement de bois), switch de saison (soupir de vent). Banque CC0 freesound.org, ~50 Ko OGG embarqués. Toggle dans le Profil, opt-in par défaut pour rester compatible avec le ton sobre. Élève de « app » vers « expérience sensorielle » sans rompre la charte privacy.
- **Traitement des photos utilisateur** — les JPEG bruts capturés en conditions réelles (pluie, contre-jour, flou de marche) contaminent l'esthétique soignée. Trois pistes : cadre polaroid subtil (bordure crème + ombre douce) qui assume l'imperfection, vignette légère ~10 % qui adoucit les bords brûlés, filtre saisonnier discret (hiver bleuté, été verdâtre). Probablement la piste polaroid seule. Affecte `PhotoThumbnail` + galeries de la fiche-espèce.
- **Accessibilité daltonien sur la couche découverte** — la distinction découvert/non-découvert repose sur gris vs vert, quasi-identiques pour ~8 % des hommes (deutéranopie/protanopie). Ajouter un 2nd signal : halo autour des points découverts, pattern, ou différentiel de saturation/luminosité plus marqué. À tester avec simulateur daltonisme (Android dev options). Affecte `buildDiscoveryExpression` dans `MapScreen`.
- **Investigation usage data Android** — observé 2026-05-04 : User data 6 → 36 Mo en 4 h sur Pixel 9a (cf. `manual_tests/20260504/UserData{1,2}.png`), Cache stable ~31 Mo. Croissance attendue (asset DB Room 30 Mo copiée par `Room.createFromAsset` au 1er lancement + photos JPEG ~2-5 Mo + WAL Room non checkpointé). Pistes si dérive : (a) checkpoint WAL périodique (`PRAGMA wal_checkpoint(TRUNCATE)`), (b) ouverture asset DB en read-only sans copie complète si Room le permet, (c) compression JPEG plus aggressive sur les captures. Pas urgent — investiguer post-v1.0 si remontée user.

## Phase 6 — Hygiène projet ✅

Branche `claude/phase-6-merge-ZEOpJ`. Aligne la doc et prépare l'infra de signing release sans rien committer de sensible (le repo reste privé).

- [x] Branche `claude/phase-4-roadmap-update-qSpGh` consolidée ✅ — merges successifs `claude/review-roadmap-planning-rrUHS`, `claude/phase-3-design-update-hKgD6`, `claude/go-sprint-h-foH26`. Conflit `Season.kt` résolu en faveur de Sprint I (sur-ensemble) ; conflits ROADMAP en faveur de la version review-roadmap (post-arbitrage) ; conflits MapScreen / ProfileScreen alignés sur la convention Outlined de Phase 3.
- [x] **CLAUDE.md à jour** ✅ — Stack : ajout DataStore Preferences + Fraunces. Architecture : ajout des sections `data/` étendue (Season + SeasonStore en mémoire, OnboardingStore DataStore, Badge + BadgeEvaluator pure, RepositoryProvider, RemarquableInfo), bloc `backup/` (BackupExporter / Importer / Models / Filename, dédup additive, schemaVersion=1), bloc `ui/` (WelcomeScreen + WELCOME_REPLAY, MapScreen + FilterSplash + SeasonSelector + ArchiveBanner, RemarquablesScreen, ProfileScreen segmented + Sauvegarde, BadgesScreen). Conventions : nav conditionnelle, saisonnalité non persistée, backup additif, ajout de badges, routes, tokens couleur via `MaterialTheme.arbresColors`. Section Setup : note JDK 21 préservée. Section Commandes : ajout sous-section *Build release signé* (keytool + local.properties + apksigner verify). Section *Quand tu travailles ici* : règles autour de Season ordinal, BadgeCatalog.ALL.size, BackupModels.CURRENT_SCHEMA_VERSION, déclaration de routes.
- [x] **README.md refondu** ✅ — statut « v0.6 (phases 0 → 5.5 livrées) », nouvelle section *Ce que ça fait aujourd'hui* (8 features livrées), pile technique précisée (MapLibre 11.11.0, DataStore, ~907 espèces), section *Build release signé* en pointer vers CLAUDE.md, mention LICENSE MIT. Pas de captures d'écran (à ajouter quand validé device).
- [x] **Signing release configurée** ✅ — `app/build.gradle.kts` lit `local.properties` (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) et bascule `signingConfigs.create("release")` quand le keystore est trouvé. Fallback `signingConfigs.getByName("debug")` sinon — `assembleRelease` reste fonctionnel pour CI / clones frais sans manipuler de clés. Le `.gitignore` excluait déjà `*.jks`, `*.keystore` et `local.properties` — aucune modif nécessaire.

## Phase 7 — Texture sensorielle (motion, célébration, identité) ✅

Capitaliser sur l'identité visuelle posée en Phase 3 (Fraunces, `ArbresColors`, Outlined homogène, splash custom, tinting saisonnier) en étendant le vocabulaire vers le motion, le haptique et les états vides. Pas de nouvelles features fonctionnelles — uniquement de la profondeur sensorielle sur les gestes existants.

- [x] **Motion language codifié `ArbresMotion`** ✅ — `data class ArbresMotion` exposée via `staticCompositionLocalOf` (`theme/ArbresMotion.kt`), accessible par `MaterialTheme.arbresMotion`. Tokens : `micro=150`, `short=300`, `medium=600`, `long=1200`, `sway=2400`, `celebration=1800`, easings `sway`/`snap`. `MapScreen.kt` (splash sway, intro, FilterSplash, AnimatedVisibility exit) refactoré sur le token plutôt que `tween(2400)` etc.
- [x] **Transitions de saison animées** ✅ — `Theme.kt` collecte désormais `seasonStore.selected` et anime `surface`/`background` via `animateColorAsState(tween(arbresMotion.medium, swayEasing))`. Couche ambiante `SeasonAmbience` (Canvas + 16 particules saisonnières par saison, descente verticale + wobble + rotation, fade in/out 1.8 s) montée derrière les FABs sur `MapScreen`. `SeasonSelector` icône + label en `Crossfade(arbresMotion.short)` plutôt que swap brutal.
- [x] **Climax du moment de capture** ✅ — `CaptureRepository` expose `SharedFlow<CaptureEvent>` (avec `isFirstOfSpecies` calculé via `CaptureDao.speciesAlreadyCaptured()` one-shot avant l'INSERT). Sur la carte : `CaptureCelebrationOverlay` projette le `LatLng` capturé via `MapLibreMap.projection.toScreenLocation`, dessine un halo qui s'étend (8→48 px, alpha 0.6→0) + cœur scale 1×→1.5×→1× sur 300 ms ; si 1re espèce, le binomial Fraunces flotte 800 ms au-dessus du point puis fade. Sur la fiche-espèce : `CelebrationHero` (cascade fade+scale en 4 paliers sur `arbresMotion.celebration` = 1800 ms, silhouette `Park` grand format, fond tinté `feuilleSombre`, binomial italique, label de confirmation).
- [x] **Haptiques** ✅ — permission `VIBRATE` ajoutée au manifest, wrapper `rememberCaptureHaptic()` dans `util/Haptics.kt`. Appel `captureHaptic()` après l'INSERT dans `rememberCaptureController` (`CaptureLauncher.kt`) — synchronisé avec le climax visuel sur la carte.
- [x] **Empty states designés** ✅ — `EmptyState` partagé dans `ui/common/`, illustration custom + Fraunces SemiBold + corps Ecorce 70 % opacity. 4 vector drawables `illus_empty_{arboretum,badges,remarquables,profile}.xml` (silhouettes douces palette `feuilleSombre`/`or`/`ecorce`). Sites migrés : Arboretum (`ArboretumEmptyState`), Remarquables (`RemarquablesEmptyState`), Badges (case 0 débloqué via span full-width dans la grille), Profil (visible avant 1re capture, au-dessus du `StatsCard`).
- [x] **Iconographie remarquable inspirée des plaques officielles Paris** ✅ — `ic_remarquable_plaque.xml` : rectangle chanfreiné vert sombre `#2E7D32` + lettre R crème, viewport 24×24, non-tintable (`tint = Color.Unspecified` côté Compose). Sites migrés : FAB ★ de la carte, fiche détail (`ArbreDetailContent`), card discovered de `RemarquablesScreen`. Carte (points) gardée en cercles colorés (lecture rapide à 8 px, pas de switch symbol-layer). `BadgeIcons.kt` garde `Icons.Outlined.Star` (icône tintée dans la grille, le bichromy de la plaque ne survit pas au tint).
- [x] **Onboarding animé** ✅ — `lottie-compose` 6.4.0 ajoutée à `gradle/libs.versions.toml`. Asset `app/src/main/assets/animations/welcome_loop.json` (4 s, 30 fps, canopée d'arbre qui scale + bascule de couleur gris → vert → gris). `WelcomeScreen.kt` remplace les 4 BulletCard par `LottieAnimation(IterateForever, 220 dp)` + une caption Fraunces unique (`welcome_caption`).

## Phase 8 — Hygiène pré-release

Sprint court (~3 jours) entre Phase 7 (texture sensorielle, qui ajoute du code) et Phase 9 (release publique). Met des garde-fous *autour* du code complet plutôt qu'autour de code qui va bouger. Pas de feature, juste de la dette technique avant que le repo passe public.

**Bloquants v1.0 :**

- [x] **Bump `versionCode` / `versionName`** ✅ — `versionCode = 7` / `versionName = "0.7.0"` dans `app/build.gradle.kts`, aligné sur les phases 0 → 7 livrées. Bumper avant chaque APK signé à partir de maintenant.
- [x] **Nettoyage des dépendances mortes** ✅ — `moshi-kotlin` (1.15.1) et `okhttp` (4.12.0) retirés de `libs.versions.toml` (versions + libraries) et de `app/build.gradle.kts` (implementations). `git grep` post-suppression : zéro import `com.squareup.moshi` / `okhttp3` dans `app/src/`. Pas de keep rules ProGuard à ajouter (deps zéro-utilisation).
- [x] **Tests unitaires `BadgeEvaluator`** ✅ — `app/src/test/java/app/arbre/data/BadgeEvaluatorTest.kt`, junit4 vanilla, 23 cas. Couvre : `parseArrondissement` (1er, 11e, 20e, hors-Paris, range), `yearMonthOf` (zone Europe/Paris + rollover UTC↔Paris), `hasTwelveConsecutiveMonths` (12 pile, year boundary, gap), 0/1/10 captures, geant `> 30 m`, vieux sage `> 400 cm`, espèce rare `count < 100`, remarquables ne comptent pas comme espèces, tourneur de Paris (10 arr) + tour complet (20), captures hors-Paris ignorées, ronde des saisons (4), année complète (12 mois consécutifs), dédup remarquables sur arbreId, `unlockedAt` figé sur la capture déclenchante.
- [x] **Tests unitaires `BackupImporter`** ✅ — `app/src/test/java/app/arbre/backup/BackupImporterTest.kt`. Refactor minimal : `importStream(input, photosDir, dao)` extrait en top-level `internal suspend fun` pour rester JVM-pur (pas de `Context` requis). 7 cas : roundtrip 2 captures, idempotence (2nd import → `imported=0, skipped=N`), photo manquante (`photosMissing` incrémenté, capture insérée), `schemaVersion = 99 → SCHEMA_TOO_NEW`, zip corrompu → `CORRUPT_ZIP`, meta absent → `META_MISSING`, captures absentes → `CAPTURES_MISSING`. `FakeCaptureDao` qui implémente l'interface Room.
- [x] **`testOptions.unitTests.isReturnDefaultValues = true`** ✅ — ajouté dans `app/build.gradle.kts` pour neutraliser `android.util.Log` en JVM pur (utilisé par `BackupImporter`) sans tirer Robolectric.

**Très utiles :**

- [x] **detekt minimal + intégration Gradle** ✅ — plugin `io.gitlab.arturbosch.detekt` 1.23.7 ajouté à `gradle/libs.versions.toml`, appliqué sur `:app` (mono-module). Config souple `detekt.yml` à la racine : focus complexité (`LongMethod` 100, `LargeClass` 600, `CyclomaticComplexMethod` 25, `NestedBlockDepth` 5), null-safety (`UnsafeCallOnNullableType`), wildcard imports + `ReturnCount` ≤ 6. Désactivé : `MagicNumber`, `MaxLineLength`, `TooGenericExceptionCaught`, `SwallowedException` (BackupImporter catch volontairement Throwable + log). Rapports HTML + XML dans `app/build/reports/detekt/`. Run : `./gradlew :app:detekt`. Baseline à figer côté dev (`./gradlew :app:detektBaseline`) avant de bloquer la CI dessus.
- [x] **Extraction `MapScreen.kt`** ✅ — passé de 913 LOC à 540 LOC (orchestrateur principal). Deux nouveaux fichiers package-privés dans `ui/map/` :
  - `MapLayers.kt` (~190 LOC) : `addArbresLayers`, `applyDiscoveryColor`, `buildDiscoveryExpression`, `filterGeoJsonBySpecies` + constantes IDs sources/layers + couleurs pins. Logique MapLibre pure, testable JVM si besoin.
  - `MapOverlays.kt` (~210 LOC) : `FilterBanner`, `ColdStartSplash`, `FilterSplash` — composables d'overlay UI sans état partagé avec l'écran.

  Le refactor du `@Composable MapScreen` lui-même (extraction `MapHostView` / `MapFabs` / `MapSheets` / `MapPermissions`) est laissé à plus tard — risque de casser le lifecycle `MapView` sans environnement de build pour valider, et les FABs sont fortement couplés à l'état du Composable parent (snackbar, viewModel, scope, captureRepo, captureController). À reprendre quand on veut splitter le hub de navigation (idée-vrac CPO #10).

## Phase 9 — Build et tests manuels device ✅

Première session devant le téléphone GrapheneOS depuis longtemps : on a accumulé 8 phases (Sprints H/I, Phase 3 → 8) sans validation device, donc une grosse séance de smoke + régression avant de penser à la release publique.

Objectifs : (a) confirmer que la branche `claude/phase-8-dev-YGxnL` build proprement sur la machine du dev (debug + release), (b) que les tests JVM de Phase 8 passent (`./gradlew test` → `BadgeEvaluatorTest` + `BackupImporterTest`, 30 cas), (c) que `./gradlew detekt` sort un baseline raisonnable, (d) que toutes les features livrées entre `main` et la branche tournent réellement sur device.

Le détail du protocole de test est dans **`TESTS.md`** à la racine du repo : 100+ items à cocher, organisés par flow utilisateur (onboarding → carte → capture → Arboretum → fiche-espèce → remarquables → profil → badges → backup → saisonnalité → motion / haptiques / empty states), avec côté à côté les commandes build et les attendus précis.

Pendant cette session, Claude Code peut être réquisitionné en parallèle pour exécuter des `./gradlew …` ou `adb …`, lire des logs, fabriquer des données de test (ex. `BackupImporter` avec un zip généré), comparer des sorties — voir `TESTS.md` § « Ce que Claude peut faire à tes côtés ».

- [x] **Build debug + install device** ✅ — `./gradlew installDebug` sur Pixel 9a (GrapheneOS), APK 77 Mo. Splash device à valider en smoke (section 2 TESTS.md, à venir).
- [x] **Tests JVM verts** ✅ — `./gradlew :app:testDebugUnitTest` → 34 cas verts (`BadgeEvaluator` + `BackupImporter`, le ROADMAP annonçait 30 mais le décompte réel est 34).
- [x] **detekt baseline** ✅ — `app/detekt-baseline.xml` (15 lignes legacy : `MapScreen`, `ArbresNavHost`, `ProfileScreen`, `ArbreDetailScreen`). `./gradlew :app:detekt` → 0 issue.
- [x] **Build release** ✅ — `./gradlew assembleRelease` (fallback debug-signing) → APK 59 Mo, R8/ProGuard OK, signature v2 vérifiée par `apksigner verify`.
- [x] **Smoke device — première passe** ✅ (2026-05-03) — flows principaux validés (onboarding, carte interactive, capture, Arboretum, remarquables, profil, badges, saisonnalité, motion). 4 bugs critiques attrapés et fixés en cours de session, listés ci-dessous. Reste des sous-sections de `TESTS.md` à cocher demain en usage prolongé sur le device — bascule en Phase 10 plutôt que de bloquer la fermeture de Phase 9.
- [ ] **Bump versionCode → 8 / versionName → "0.8.0"** à la fin de Phase 10 (avec les fixes polish).

### Bugs Phase 9 — corrigés pendant le smoke device

4 bugs trouvés au 1er run device (2026-05-03), tous fixés dans la foulée :

1. **Asset DB v0 → fallback destructif → table `capture` jamais créée** — `tools/build_dataset.py` ne settait pas `PRAGMA user_version` sur l'asset DB. Room voyait `user_version = 0` à la 1re ouverture, ne trouvait pas de migration `0→2`, déclenchait `fallbackToDestructiveMigration()` → re-copie de l'asset (toujours v0) → la migration `1→2` ne tournait jamais → crash `SQLiteException: no such table: capture`. Fix : `PRAGMA user_version = 1` ajouté côté script + asset existant patché en place (sans regénérer 217 k rows).
2. **Carte non-interactive (zoom/pan/tap bloqués)** — `SeasonAmbience` (Canvas overlay des particules saisonnières) attachait un `pointerInput(Unit) {}` censé « laisser passer les events » d'après le commentaire. En réalité un `pointerInput {}` vide INTERCEPTE les events au lieu de les forwarder. Même bug dans `CaptureCelebrationOverlay`. Fix : `pointerInput` retiré des deux composables, un Canvas sans pointerInput laisse passer naturellement les events vers les composables sous-jacents.
3. **Sélecteur de saison hors de sa place** — la pill `SeasonSelector` traînait sur la carte alors qu'elle vit naturellement dans Profil/Arboretum/Remarquables (3 écrans qui exposent des stats). La carte affiche désormais toujours `Season.current()`. `ArchiveBanner` + branche `isArchive` retirés de `MapScreen`.
4. **Splash bloqué (régression introduite par tentative d'optim)** — `GeoJsonSource(json)` appelé sur `Dispatchers.Default` jetait `CalledFromWorkerThreadException` (MapLibre exige le UI thread sur les sources). L'exception était attrapée par le `catch (Throwable)`, `arbresPrets` ne devenait jamais `true`, splash affiché indéfiniment. Fix : revert sur Main (le freeze ~700 ms du parsing 32 Mo est toléré ; régler le « splash sans animation » est traité en Phase 10).

### Bugs Phase 9 — corrigés en build & tests headless

5 bugs trouvés en faisant tourner build + tests + lint pour la première fois depuis la fin de Phase 7. Tous fixés dans le commit du 2026-05-03 :

1. **`WelcomeScreen.kt`** — `import androidx.compose.runtime.getValue` manquant ; `by rememberLottieComposition(...)` ne compilait pas. Régression Phase 7 jamais buildée localement.
2. **`build.gradle.kts` + `libs.versions.toml`** — `org.json` indisponible en JVM unit-test (Android-only, pas tiré par `isReturnDefaultValues`). Ajout `testImplementation("org.json:json:20240303")` (version `orgJson` dans la version catalog). Sans ça, les 7 tests `BackupImporterTest` tombaient tous en `Failure(CORRUPT_ZIP)` car `JSONObject(...)` levait à l'instanciation, attrapé par le `catch (Throwable)` de l'importer.
3. **`BackupImporter.kt`** — bug réel : `ZipInputStream` accepte les bytes garbage sans `ZipException`, sortait de la boucle avec 0 entry, retournait `META_MISSING` au lieu de `CORRUPT_ZIP` sur fichier non-zip. Compteur `entryCount` ajouté ; si 0 entry, fail dur en `CORRUPT_ZIP` avant le check `meta == null`. Couvre aussi le cas dégénéré du zip vide légitime.
4. **`BackupExporter.kt`** — `pkg.longVersionCode` requiert API 28, `minSdk = 26` → erreur Lint `NewApi`. Remplacé par `PackageInfoCompat.getLongVersionCode(pkg).toInt()` (`androidx.core.content.pm`, déjà tiré par `core-ktx`).
5. **`BadgesScreen.kt`** — faux-positif Lint `ProduceStateDoesNotAssignValue` sur un `produceState` dont l'assignation `value = ...` est suspend. Suppression locale `@Suppress("ProduceStateDoesNotAssignValue")` ; le pattern est idiomatique.

## Phase 10 — Polish v1.0 (rebranding + fixes UX) ✅

Phase tampon entre la passe device de Phase 9 et la release publique de Phase 11. Pas de feature nouvelle : on transforme « Arbres » (nom de travail) en **CanoPaname** (nom produit), on règle les bugs de polish remontés en usage prolongé, et on referme les détails textuels. Les 4 tâches étaient strictement indépendantes (zéro fichier partagé) — elles ont été parallélisées sur 2 agents pendant que les fixes locaux tournaient en main thread.

- [x] **Rebranding `Arbres` → `CanoPaname`** ✅ — strict surface utilisateur. Touché : `strings.xml` (`app_name`, `welcome_title`), `themes.xml` (`Theme.Arbres*` → `Theme.CanoPaname*`), `AndroidManifest.xml` (refs themes seulement, `android:name=".ArbresApp"` intact), `MapOverlays.kt:160` (string hardcodée du `ColdStartSplash`), `README.md` (titre L1). Conservés intentionnellement : package `app.arbre`, classes Kotlin `Arbres*` (`ArbresApp`/`ArbresTheme`/`ArbresColors`/`ArbresMotion`/`ArbresNavHost`/`ArbresTypography`), IDs MapLibre (`ARBRES_SOURCE_ID`, `arbres-paris.geojson`), strings au sens trees (« capturer les arbres », « Arbres remarquables » dans `ProfileScreen:407`). Mismatch interne/externe assumé.
- [x] **Splash cold-start figé** ✅ — stratégie « lazy 2-passes flip-avant-load » :
  1. `addArbresLayers(style, EMPTY_GEOJSON)` → carte interactive immédiate, layers vides instantanées.
  2. `arbresPrets = true` → splash joue son anim (sway, intro fade, progress) et sort proprement.
  3. `setArbresGeoJson(style, json)` (nouvelle fonction `internal` dans `MapLayers.kt`) injecte les 217k features après — le freeze 700 ms du parsing natif MapLibre est masqué par « carte vide » au lieu d'un splash figé. Mode `MAP_FILTERED` reste en single-pass (corpus filtré < 1 Mo, freeze imperceptible). `EMPTY_GEOJSON` passe `internal` pour usage cross-fichier.
- [x] **Cohérence iconographique des arbres** ✅ — silhouette platane unique partout :
  - Nouveau `ic_arbre_canonical.xml` (viewport 24×24, fillColor `#FFFFFF` tintable, dérivé linéairement du path L16 de `ic_launcher_foreground.xml`).
  - `illus_empty_arboretum.xml` et `illus_empty_remarquables.xml` réécrits sur le platane canonique 160×160 (`#7A8F6E` fillAlpha 0.40). La plaque chanfreinée verte + lettre R de remarquables conservées intactes.
  - `WelcomeScreen.HeroLogo()` : `Icons.Outlined.Park` (sapin générique Material) → `painterResource(R.drawable.ic_arbre_canonical)` tinted `arbresColors.or`.
  - `WelcomeAnimation()` : Lottie remplacé par animation Compose pure (`rememberInfiniteTransition` + `lerp` tint gris→vert + scale 0.85→1.0, 4 s en `RepeatMode.Reverse`). Asset `welcome_loop.json` supprimé, dossier `assets/animations/` retiré, dépendance `lottie-compose` retirée du catalog + `app/build.gradle.kts`.
- [x] **« en printemps » → « au printemps »** ✅ — helper `val preposition: String get() = if (this == SPRING) "au" else "en"` ajouté dans l'enum `Season`. 3 callsites patchés (le ROADMAP citait 4 mais `ProfileScreen:374` mettait juste la saison entre parenthèses, sans préposition) : `ArboretumScreen.kt:365` + `:450` (empty state isArchive), `RemarquablesScreen.kt:378`.
- [x] **Bump versionCode → 8 / versionName → "0.8.0"** ✅ — alignement avec phases livrées 0 → 8.

## Phase 10.5 — Polish post-smoke 2026-05

Session de tests manuels device du 2026-05-04 (impr. écran dans `manual_tests/20260504/`). L'app tourne, rien de bloquant fonctionnel, mais 11 remarques d'usage qui touchent les premières secondes (splash, hero du Profil vide, plaque R des Remarquables) et le parcours nav (modal arbre remarquable, photos non zoomables, libellé `Pokédex`, tris, boussole inaccessible). Phase tampon avant Phase 11 — ce sont les défauts qui salissent la première impression d'une release publique. Une 12e remarque (usage data) est reclassée en *Idées en vrac* (investigation post-v1.0).

Arbitrages tranchés en session : Vert = écosystème arbres normaux + Arboretum, Orange = Remarquables partout (refonte complète, pas de demi-mesure). Renommage `Pokédex → Catalogue`. Splash enrichi par cascade de platanes miniatures.

### Sous-groupe A — Refonte iconographie Remarquables ✅

Couvre remarques #4 (plaque R bizarre, chanfrein top-right) et #12 (incohérence pin orange / icône verte / Arboretum gris). Contrat couleur stabilisé.

- [x] **Nouveau `ic_remarquable_badge.xml`** ✅ — disque orange `#FB8C00` (r=10.5 dans 24×24) + silhouette platane crème `#F5F1E6` dérivée linéairement de `ic_arbre_canonical` (scale 0.76 autour de (12, 11.33)). `tint = Color.Unspecified` côté Compose, la couleur *est* le sens.
- [x] **Token `remarquableOrange`** ✅ — `Color.kt` expose `RemarquableOrange = Color(0xFFFB8C00)` (aligné `MapColors.PIN_ORANGE`), `ArbresColors` data class étendue avec `remarquableOrange: Color`, light + dark partagent la même valeur (couleur de marque, pas tintée par le scheme).
- [x] **Migration des sites** ✅ — FAB ★ de la carte (`MapScreen.kt:424`), `ArbreDetailContent` (modal sheet, `ArbreDetailScreen.kt:99` + label « Arbre remarquable » passé de `colorScheme.primary` vert à `arbresColors.remarquableOrange`), card discovered de `RemarquablesScreen.kt:435` (label « Remarquable » idem). `illus_empty_remarquables.xml` réécrit : silhouette platane (alpha 0.40) + pastille orange (r=24 à center 120,116) + mini-platane crème dans la pastille.
- [x] **`ic_remarquable_plaque.xml`** supprimé du repo ✅ — `git grep ic_remarquable_plaque` = 0 résultat. `BadgeIcons.kt` garde `Icons.Outlined.Star` pour `CHASSEUR_REMARQUABLES` (le bichromy ne survit pas au tint, choix Phase 7 toujours valide).
- [x] Pas de migration côté carte MapLibre ✅ — pin orange déjà en place via `MapColors.PIN_ORANGE`.

### Sous-groupe B — Fiche remarquable enrichie + parcours croisé ✅

Couvre remarques #5 (modal remarquable mauvaise nav vers fiche-espèce non capturée), #6 (fiche-remarquable n'affiche pas la photo user), #7 (photos pas plein écran), #8 (liens fiche remarquable ↔ fiche espèce, one-to-many).

- [x] **`ArbreDetailContent` (modal sheet)** ✅ — second callback `onRemarquableClick`. Logique branchée côté caller (`MapScreen.kt`) :
  - Arbre remarquable capturé en remarquable (`arbre.remarquable && id ∈ capturedRemarquables`) → `FilledTonalButton` **« Fiche remarquable »** (icône `ic_remarquable_badge`, tint `Color.Unspecified` pour préserver la bichromie orange/crème).
  - Espèce capturée (`sk != null && sk ∈ capturedSpecies`) → `OutlinedButton` **« Fiche espèce »** (renommage de l'ancien « En savoir plus sur l'espèce »).
  - Les deux peuvent coexister, empilés verticalement avec `Spacer(8.dp)` (le bouton remarquable au-dessus, plus emphatique). Les callbacks ferment d'abord la sheet via `viewModel.closeDetail()` avant la nav.
- [x] **`RemarquableDetailScreen` — galerie « Tes photos (N) »** ✅ — `PhotoGallery` ajoutée au-dessus de `ArbreDetailContent`, masquée si N=0. `onRemarquableClick = null` explicite (déjà sur la fiche, éviter la boucle). `onSpeciesClick` cross-link vers la fiche-espèce (utile pour pivoter de l'arbre individuel vers le contexte espèce).
- [x] **`PhotoLightbox` plein écran** ✅ — nouveau composable `ui/common/PhotoLightbox.kt`. `Dialog` avec `usePlatformDefaultWidth = false` + fond noir plein écran. `rememberTransformableState` pour pinch-zoom 1×→5× + pan, `detectTapGestures` pour tap-dismiss et double-tap-reset. Pas de swipe entre photos (kept minimal). Décodage `BitmapFactory.decodeFile` sur `Dispatchers.IO` en pleine résolution. Réutilisé depuis `SpeciesDetailScreen` ET `RemarquableDetailScreen` via le state `lightboxIndex: Int?`.
- [x] **Galerie partagée extraite** ✅ — `PhotoGallery` (auparavant privé dans `SpeciesDetailScreen.kt:261-281`) déplacé en `ui/common/PhotoGallery.kt` avec param `onPhotoClick: (Int) -> Unit`. Items deviennent cliquables (`Modifier.clickable`) et ouvrent le lightbox sur l'index correspondant.
- [x] **Fiche espèce → Arbres remarquables de cette espèce** ✅ — nouvelle section `RemarquablesDeCetteEspece` insérée juste avant `ShowOnMapButton` dans `SpeciesDetailScreen`. Charge via `ArbreRepository.arbresRemarquables()` (suspend, ~200 rows en RAM) puis filtre côté Kotlin par `speciesIndex.indexOf(arbre) == sk` (lookup O(1) sur la map `byKey` de `SpeciesIndex`). Card avec lignes `[badge] [adresse] [chevron]` — masquée si N=0. Tap → `Routes.remarquableDetail(arbre.id)`.

### Sous-groupe C — Renommage Catalogue + tris listes ✅

Couvre remarques #10 (Arboretum : `Pokédex → Catalogue`, tri par count Paris décroissant) et #11 (Remarquables : `Pokédex → Catalogue`, groupage par arrondissement avec sous-headers).

Décision tranchée en session : **Liste Arboretum intouchée** (la ROADMAP la suggérait à tort). Le Catalogue Arboretum (ex-Pokédex) garde son look grid 3 cols + #numéros, mais le tri passe par count Paris décroissant — `#001` = espèce la plus présente (Platane), derniers numéros = espèces uniques/quasi uniques.

- [x] **Renommer `Pokédex → Catalogue`** ✅ — strings extraites dans `strings.xml` (`segment_liste`, `segment_catalogue`). Renommage interne aussi : enums `ArboretumViewMode.POKEDEX → CATALOGUE` + `RemarquablesViewMode.POKEDEX → CATALOGUE`, composables `PokedexView → CatalogueView` et `PokedexCell → CatalogueCell` côté Arboretum. `rememberSaveable` sérialise l'ordinal donc pas de souci de migration. 6 commentaires de doc alignés (`SpeciesIndex`, `Season`, `MapOverlays`, `CaptureLauncher`, `RemarquableDetailScreen`).
- [x] **Arboretum Catalogue trié par count Paris décroissant** ✅ — `CatalogueView` injecte `SpeciesInfoRepository`, tri par `compareByDescending<SpeciesEntry> { speciesInfoRepo.get(it.index)?.stats?.count ?: 0 }.thenBy alpha`. Mémoïsé sur `(speciesIndex, speciesInfoRepo)` — indépendant des captures, aucun re-tri au switch saison ni à l'INSERT. Le `displayNumber = position + 1` suit naturellement le nouveau tri. Espèces sans `SpeciesInfo` (~50 cultivars/n.sp.) → `count = 0` → fin de liste alpha entre elles.
- [x] **Remarquables Catalogue groupé par arrondissement** ✅ — `CatalogueView` réécrit en `LazyColumn` + `stickyHeader` (`@OptIn(ExperimentalFoundationApi::class)` requis, Compose Foundation 1.7+). Sections triées 1..20 puis « Hors Paris » (bois de Vincennes/Boulogne, ~10% des remarquables) en queue. Tri intra-section : alpha genre/espèce/id. `BadgeEvaluator.parseArrondissement` passé `internal → public`. Cards rangée pleine largeur : `DiscoveredCard` existante pour les capturés (sans date de capture en mode catalogue), nouvelle `LockedRemarquableCard` pour les non-capturés (silhouette grise + « ??? » + pastille « Remarquable » orange + adresse). Header `Surface tonalElevation=2.dp` Fraunces titleLarge.
- [x] **Build/tests/detekt verts** ✅ — `:app:testDebugUnitTest` 34 cas, `:app:detekt` 0 issue, `:app:lintDebug` clean. Bug attrapé en cours : `forEach` cassait le scope `LazyListScope` du `stickyHeader` (extension method) — remplacé par `for (section in sections)` qui préserve le receiver implicite.

### Sous-groupe D — Splash + empty state Profil + boussole carte ✅

Couvre remarques #1 (splash perçu statique 3-25 s), #3 (empty state Profil = drapeau de golf bizarre), #9 (boussole MapLibre sous status bar, intappable).

- [x] **Splash cascade de platanes en boucle** ✅ — `MiniArbreCrown` + `MiniArbreItem` privés dans `MapOverlays.kt` à la suite de `ColdStartSplash`. 7 platanes (`ic_arbre_canonical`, tailles 18-32 dp variables, crème `#F5F1E6`, alphas 0.45-0.65) en arc semi-circulaire au-dessus du hero (angles -88°→+88°, rayons 115-155 dp). Cycle infini par platane via `Animatable` + `LaunchedEffect { while(true) ... }` — fade in 600 ms → plateau 1300 ms → fade out 600 ms → invisible 1000 ms = 3500 ms, `delayMs` étalés sur 0-3000 ms pour que la cascade tourne en continu pendant tout le splash (3-25 s sur cold start). Boucle douce : un `rememberInfiniteTransition` partagé, `sway` ±1f sur `motion.sway = 2400 ms` (rotation `±5°-7°` × `sin(phaseOffset)`) + `drift` ±1f sur 3600 ms (translation Y ±3 dp × `cos(phaseOffset * 1.3f)`), phases désynchronisées via `phaseOffset = idx * 0.37f`. Note d'implémentation : passer un `Float` global (style `tick`) en paramètre à `MiniArbreItem` ne marche PAS — la lambda `graphicsLayer { }` capture la valeur à la composition et ne se ré-évalue plus en draw, d'où freeze. Il faut un `State` (l'`Animatable.value`) lu directement dans la lambda. Aussi : `LinearProgressIndicator` retiré (pas de progress fiable disponible) — la cascade porte seule le sentiment de vie.
- [x] **Investigation latence cold start** ✅ — déjà couverte par les 5 timestamps `tStart`/`tStyle`/`tEmpty`/`tJson`/`tLayers` instrumentés en Phase 10 (`MapScreen.kt:225-315`, `Log.i("MapScreen", ...)`). Le freeze ~700 ms du parsing GeoJSON 32 Mo est mesuré et masqué par le 2-passes `addArbresLayers(EMPTY_GEOJSON) → flip → setArbresGeoJson(json)`. Aucun nouveau Log à ajouter — la cascade de platanes (item ci-dessus) est ce qui rend l'attente perçue acceptable.
- [x] **Empty state Profil refondu** ✅ — `illus_empty_profile.xml` réécrit sur le pattern `illus_empty_arboretum.xml` (viewport 160×160, fillColor `#7A8F6E`, fillAlpha 0.40, pathData de la silhouette platane canonique). Plus de drapeau / chemin sinueux. Aucune modif côté Kotlin (`ProfileScreen.kt:199-209` continue de référencer `R.drawable.illus_empty_profile`).
- [x] **Rotation de la carte bloquée** ✅ — au smoke, l'approche initiale (`setCompassMargins` pour décaler la boussole sous l'inset status bar) ne suffisait pas : la boussole restait invisible au tilt. Choix pragmatique : `map.uiSettings.isRotateGesturesEnabled = false` + `map.uiSettings.isCompassEnabled = false` après `mapRef = map`. La rotation libre n'apporte rien au modèle « carnet de bord naturaliste » — la carte reste nord-en-haut comme un plan imprimé, et la boussole devenue inutile est retirée.

### Sous-groupe E — Conditionnement à la capture (sécurité d'info) ✅

Couvre les 4 remarques de la session 2026-05-04 sur la transparence : info dévoilée avant capture (#2 fiche-espèce → liste remarquables, #3 fiche-remarquable → fiche-espèce, #5 Catalogue Remarquables → adresse) et bug de groupage par arrondissement (#6) qui rendait toute la vue Catalogue inutile (tout en « Hors Paris »). Thème commun : *« ne révèle pas l'info avant que le joueur l'ait gagnée »*.

- [x] **Parser arrondissement robuste** ✅ — diagnostic posé après dump `sqlite3` de l'asset DB : la table `arbre.adresse` stocke le **format brut** du CSV OpenData (« …, PARIS 12E ARRDT », « …, PARIS 1ER ARRDT », « …, BOIS DE VINCENNES »), pas le format normalisé. La fonction `normalize_arr` côté `tools/build_dataset.py` n'est utilisée que pour les stats de `species-info.json`, jamais pour `build_address`. Le regex Kotlin historique `, (\d+)(er|e)$` ratait donc 100 % des adresses → bug latent qui plombait aussi les badges Tourneur/Tour Complet (compteur arr distincts toujours à 0 sur device). Nouveau `data/ArrKey.kt` avec `sealed class ArrKey { Paris(n) | BoisVincennes | BoisBoulogne | Other }`, `parseArrKey` qui travaille sur le segment post-dernière-virgule et accepte deux formats — brut `^PARIS (\d{1,2})(?:ER|E) ARRDT$` (fait foi sur device) et normalisé `^(\d{1,2})(?:er|e)$` (compatibilité tests historiques + futures évolutions). Détection des bois via comparaison case-insensitive sur le tail. `BadgeEvaluator.parseArrondissement` réécrit en wrapper d'1 ligne `(parseArrKey(adresse) as? ArrKey.Paris)?.num` — gain qui répare aussi les badges Tourneur/Tour Complet.
- [x] **Catalogue Remarquables — sections corrigées + adresse masquée** ✅ — `RemarquablesScreen.CatalogueView` utilise `parseArrKey` pour ses sections : 1..20, Bois de Vincennes, Bois de Boulogne, Hors Paris (les bois élevés en clé propre car ~10 % des remarquables). `LockedRemarquableCard` ne montre plus l'adresse — silhouette « ??? » + pastille « Remarquable » uniquement, l'info géo reste portée par le sticky header.
- [x] **Lien « Fiche espèce » conditionné côté fiche remarquable** ✅ — `RemarquableDetailScreen` collecte `captureRepo.capturedSpeciesIndices()` (flow non-saisonnier global) et ne propage `onSpeciesClick` à `ArbreDetailContent` que si `sk in capturedSpecies`. Sinon le bouton ne s'affiche pas (comportement déjà géré par `ArbreDetailContent` pour callback null).
- [x] **Liste remarquables masquée côté fiche espèce** ✅ — `SpeciesDetailScreen.RemarquablesDeCetteEspece` reçoit `capturedIds: Set<Long>`. Deux branches par item : `DiscoveredRemarquableRow` (badge + adresse + chevron, cliquable) si capturé, `LockedRemarquableRow` (badge + « ??? · {arrondissement} », non-cliquable) sinon. La pastille orange reste visible — incentive à chasser la même espèce dans plusieurs arrondissements.
- [x] **Tests `BadgeEvaluatorTest`** ✅ — 8 nouveaux cas. Sur `parseArrondissement` : suffixe normalisé bare (« 5e »/« 1er »/« 20e ») et **format brut OpenData** (« …, PARIS 12E ARRDT », « PARIS 1ER ARRDT », « PARIS 20E ARRDT »). Sur `parseArrKey` : Paris full address, Paris bare suffix, Paris raw avec préfixe parc, BoisVincennes (Title + raw « …, BOIS DE VINCENNES »), BoisBoulogne (Title + raw), Other (null/empty/Hauts-de-Seine). Total : 42 cas (34 → 42).

### Sous-groupe F — Polish + GPS ✅

Reporté du brief 2026-05-04. Quatre micro-items, aucun touchant la persistance / le schéma Room / le pipeline build.

- [x] **GPS first-launch** ✅ — fix en 5 couches après 3 itérations device :
  - **(1) Eager start** : `WelcomeScreen.permissionLauncher` appelle `LocationProvider.start(ctx)` immédiatement sur grant (avant `onContinue()`).
  - **(2) Reactive availability** : `MapScreen` collecte `LocationProvider.currentLocation` via `collectAsState` et la passe en clé du `remember(openedArbre.id, currentLocation)` qui calcule `availability`. La transition « Active le GPS » → « Capturer » se fait dans la seconde sans intervention.
  - **(3) `captureAvailability` non-bloquant** : pure lecture du `currentLocation.value` filtré sur âge, plus aucun fallback `currentOrLastKnown` synchrone. **Cause des 10 s d'attente observés au cold start** : `currentOrLastKnown` sur API ≥ R appelle `LocationManager.getCurrentLocation` qui peut suspendre jusqu'à 10 s en attendant un fix one-shot, gardant le bouton « Capturer » désactivé pendant tout ce délai. Idem dans `runCapture` (le path tap-Capturer). Si le fix n'est pas encore arrivé → `NoGps` instant et la seule conséquence est que le bouton devient cliquable < 1 s plus tard via la propagation flow.
  - **(4) Drift réduit** : `MIN_INTERVAL_MS` baissé de 2 000 → 500 ms, `MIN_DISTANCE_M` 1 → 0 (s'aligne sur la cadence du `LocationEngine` MapLibre — sans ça, notre `_currentLocation` retardait visiblement le pin et créait un écart ~100 m vu sur device). En complément, `LocationProvider.start` lance désormais un `getCurrentLocation` **async non-bloquant** (callback) sur API ≥ R pour seed le flow avec un fix précis dès qu'Android peut le fournir, sans attendre le 1er natural update du listener.
  - **(5) Bridge MapLibre → `LocationProvider` (cause racine)** : malgré (1)-(4), le tout 1er run post-onboarding gardait ~10 s de bouton « Capturer » désactivé. Diagnostic confirmé en device : sur Android, un `LocationListener` enregistré juste après un grant fresh de permission `ACCESS_FINE_LOCATION` peut ne recevoir aucun update pendant ~10 s, alors que le `LocationEngineDefault` instancié par MapLibre (via `useDefaultLocationEngine(true)` dans `enableLocationPin`) reçoit déjà des fix dès t≈1 s. Le re-mount du `MapScreen` (AR menu Profil) résolvait le problème par effet de bord — un listener registré APRÈS le 1er fix natural reçoit immédiatement. **Fix architecturé** : `attachMapLibreLocationBridge(component)` (file-private dans `MapScreen.kt`) attache un `LocationEngineCallback` au `component.locationEngine` qui pousse chaque fix dans `LocationProvider.feedExternalFix(loc)` — nouvelle API publique du singleton qui passe par le même filtre `isBetterFix`. Cleanup au `onDispose` via `removeLocationUpdates(callback)`. On consomme la SOURCE MapLibre au lieu d'avoir un listener parallèle qui timeout au 1er run. Bonus : élimine définitivement tout drift visuel pin-vs-distance puisque les deux partagent désormais la même source. Considéré comme idée « out-of-the-box » la plus propre vs. polling `getLastKnownLocation`, re-mount artificiel ou hack via foreground service.
- [x] **Numéro # catalogue dans la TopAppBar de `SpeciesDetailScreen`** ✅ — title slot remplacé par une `Column` à 2 lignes : nom commun + `"#NNN / 907"` en `bodySmall` / `onSurfaceVariant`. **Le numéro est le rang du Catalogue (count Paris décroissant)**, pas le `speciesIndex` Room (ordre d'ingestion CSV, arbitraire). Logique de tri extraite dans `data/CatalogueRanking.kt` (`catalogueOrder`, `catalogueRank`) — source unique partagée avec `ArboretumScreen.CatalogueView` qui consomme désormais `catalogueOrder` au lieu de dupliquer le `compareByDescending`. Le `#001` du Catalogue (Platane) est désormais cohérent entre les deux écrans. TopAppBar extraite en sous-composable `SpeciesDetailTopBar` pour rester sous le seuil detekt LongMethod.
- [x] **Tint icône FAB Arboretum** ✅ — `Icon(MenuBook, …, tint = MaterialTheme.arbresColors.feuilleSombre)`. Fond du FAB inchangé.
- [x] **Tint icône FAB loupe (remarquable proche)** ✅ — `Icon(Search, …, tint = MaterialTheme.arbresColors.remarquableOrange)`. Fond du FAB inchangé. Cohérence avec le contrat couleur Phase 10.5 A (orange = remarquable partout). Le FAB ★ Remarquables (TopEnd) garde son `tint = Color.Unspecified` — la bichromie est dans l'asset vectoriel, pas un tint.

### Sous-groupe G — Tips informatifs sur le splash ✅

Bonus product décidé en cours de Phase 10.5 (avant bump `versionCode 9`). Le `ColdStartSplash` peut tourner ~35 s pendant l'init MapLibre + chargement des 213 k arbres. Sous le wordmark (« CanoPaname » + « Réveil des 213 042 arbres parisiens »), il restait un large vide visuel comblé seulement par la couronne de mini-platanes. On y greffe un texte rotatif de tips informatifs au ton **pince-sans-rire / décalé** : transforme le temps mort en moment de lecture, pose l'identité ludique de l'app dès le 1er lancement.

- [x] **Banque fusionnée 242 tips** ✅ — `tools/splash-tips-static.json` (source manuelle commitée, ~170 phrases history/popculture/player) + `tools/build_dataset.py:write_splash_tips()` (~60 phrases dataset générées des agrégats CSV : top espèces nominatives, distributions counts/hauteur/circonférence, Pareto « 10 espèces = 50 % », « 48 = 80 % », arrondissements croisés intra-muros vs bois, hégémonie platane « domine 18 arr sur 19 », arr atypiques, genres top/diversité, rangs 4-8 désambiguïsés par binôme scientifique quand le nom commun se répète, curiosités). Sortie unique : `app/src/main/assets/splash-tips.json` (31 Ko). Sanity check au build : placeholders `{xxx}` non supportés runtime → `ValueError` immédiat. Outliers de saisie filtrés (hauteur > 60 m / circonférence > 1200 cm écartées du « plus grand arbre »).
- [x] **Catégories + filtrage `requires`** ✅ — `intro` (10 ids ordonnés, 1er lancement), `dataset` (généré), `history`/`popculture` (statique), `player` (templates avec placeholders `{captureCount}`, `{speciesCount}`, `{remarquableCount}`, `{daysSinceFirst}`). Tip avec `requires: ["X", "Y"]` éligible seulement si toutes les valeurs > 0 → début de partie ne montre aucun tip joueur creux. Set fermé de 4 placeholders, contrôlé identiquement côté Python build et Kotlin runtime.
- [x] **`SplashTipsRepository`** ✅ — `data/SplashTips.kt` parse `splash-tips.json` une fois (singleton dans `ArbresApp`, helper `rememberSplashTipsRepository()`). Fallback gracieux si JSON corrompu (Log.w + tips vide → splash continue sans tip, pas de crash).
- [x] **`OnboardingStore.splashIntroSeen`** ✅ — 2e flag booléen dans le DataStore Preferences `onboarding` (pas de nouveau fichier). Posé à `true` après la **1re rotation** en mode intro (≈ 7 s, l'utilisateur a vu 2 tips d'accueil) — pas au mount, pour qu'un kill prématuré rejoue l'intro à la session suivante.
- [x] **`SplashTipsController`** ✅ — `ui/map/SplashTipsController.kt`, composable `rememberSplashTipText(...)` :
  - **Mode figé une fois au mount** via `.first()` lu dans un `LaunchedEffect(Unit)` puis stocké dans un `mutableStateOf` — surtout PAS `collectAsState`, sinon notre propre écriture du flag re-trigger le LaunchedEffect rotation et casse la séquence intro à la 1re rotation (bug constaté en cours d'impl).
  - **Snapshot one-shot** des Flows joueur via `combine(...).first()` — pas de re-collect (aucune capture possible pendant le splash).
  - **Cadence 7 s** = 2 cycles de la couronne mini-platanes (3500 ms). Démarrage gating sur `canRotate` (= `intro.value >= 1f` côté `ColdStartSplash`) pour éviter le double-fade au tout 1er affichage.
  - Keys du `LaunchedEffect` rotation volontairement réduites à `(repository, isIntroMode.value, canRotate)` — pas de `playerSnapshot.value` qui resetait l'index à chaque arrivée du snapshot Room.
- [x] **UI `ColdStartSplash`** ✅ — `Spacer(32.dp)` + `Box.height(84.dp)` + `AnimatedContent` crossfade 220 ms inséré sous « Réveil des… ». Style `bodyMedium` blanc α 0.85, `maxLines = 3`, `TextOverflow.Ellipsis`, hauteur fixée pour éviter les sauts entre une phrase courte et une longue.
- [x] **Verif** ✅ — `python3 tools/build_dataset.py` regen complet OK, asset 31 Ko embarqué, `assembleDebug` vert, ordre intro vérifié contre la séquence cible : Bienvenue → 210 000 arbres → 907 espèces → 30 m → 247 espèces uniques → 183 remarquables → recensement 2003 → Le Forestier → Haussmann → 10 espèces = 50 %.

### Sous-groupe H — Couleur des clusters carte ✅

**Problème UI** : sur la layer `arbres-clusters`, couleur fixe vert foncé `#2E7D32`. Au cold start sans capture, Paris paraît uniformément verte alors que 99 % des pins sont gris au sol — message visuel **trompeur** (« tout est identifié ») qui sape la lisibilité de la progression.

**Solution** : 3 buckets francs reflétant la **progression du joueur** dans la zone agrégée. Aucun nouveau token couleur introduit (réutilise `PIN_GREY`, `FeuilleClaire`, `PIN_GREEN` déjà présents). Rayon, halo texte, hit-test : inchangés.

- [x] **`enrichGeoJsonWithDiscovery` dans `MapLayers.kt`** ✅ — pendant de `filterGeoJsonBySpecies` : scan linéaire string sur le rawJson (217 k features, ~32 Mo), injecte `,"discovered":0|1` avant le `}}` final de chaque feature. Critère : `id ∈ capturedRemarquables` pour les remarquables, `sk ∈ capturedSpecies` sinon. Coût ~150-300 ms en background. Mêmes contrats de format que `filterGeoJsonBySpecies` (`sk` dernière clé, ordre Python 3.7+).
- [x] **`clusterProperty` `discovered_count`** ✅ — accumulé par Supercluster via `withClusterProperty("discovered_count", sum(accumulated, get), get("discovered"))` sur les `GeoJsonOptions`. Couplé au `point_count` natif, donne 3 buckets de couleur dans un `switchCase` :
  - `discovered_count == 0` → `PIN_GREY = #9E9E9E` (même gris que les pins non découverts)
  - `discovered_count == point_count` → `PIN_GREEN = #2E7D32` (= `FeuilleSombre`, vert foncé des pins identifiés)
  - sinon → `CLUSTER_MIXED = #81C784` (= `FeuilleClaire`, vert clair du splash)
- [x] **Pipeline cold-start non-bloquant** ✅ — l'enrichment 217k features sur device prend ~5-15 s (test GrapheneOS, plus lent que prévu sur PC). Bloquer le cold-start dessus laissait la carte vide ~20 s avant que les pins n'apparaissent. Solution : **cold-start fresh pose le rawJson nu** via `setArbresGeoJson` (~700 ms, pins visibles), clusters transitoirement tous gris. Mode filtré garde son enrichment au cold-start (json < 1 Mo, instantané).
- [x] **`LaunchedEffect` mid-session debounced 1 s** ✅ — collecte `combine(speciesIndices, remarquableIds).debounce(1000)`. C'est lui qui fait le **1er enrichment** au tout 1er cold-start (~1 s après le 1er paint, les pins sont déjà visibles). Aux changements de captures pendant la session : pareil, debounce 1 s + enrich + push. À chaque émission : `enrichGeoJsonWithDiscovery` en `Dispatchers.Default`, puis `setArbresGeoJson` UI thread. Mode filtré skippé.
- [x] **Cache process-singleton `app.enrichedGeoJson` + `app.lastEnrichmentKey`** ✅ — `MutableStateFlow<String?>` + `Pair<Set<Int>, Set<Long>>?` dans `ArbresApp`. Mis à jour par le LaunchedEffect mid-session. Au remount du `MapScreen` (retour Profil → Map), le cold-start lit `app.enrichedGeoJson.value` direct → **1 seul `setGeoJson` enrichi**, pins ET clusters d'un coup, pas de double-freeze. Le LaunchedEffect compare contre `lastEnrichmentKey` et skip le re-enrich si sets inchangés.
- [x] **OptIn `FlowPreview`** ✅ — `debounce` est encore en preview en kotlinx-coroutines 1.9. Ajouté `@OptIn(kotlinx.coroutines.FlowPreview::class)` à la fonction `MapScreen`.
- [x] **Verif build** ✅ — `assembleDebug` vert (warning FlowPreview résolu après opt-in `@OptIn(kotlinx.coroutines.FlowPreview::class)` sur `MapScreen`).
- [x] **Smoke device** ✅ — validé sur GrapheneOS : pins visibles ~700 ms au cold-start fresh, clusters s'allument en 2e wave ~1-2 s plus tard ; au retour Profil → Map, lecture cache `app.enrichedGeoJson` → 1 seul freeze, pins+clusters d'un coup ; mid-session capture → pin instant via `applyDiscoveryColor`, cluster suit après 1 s de debounce. 3 buckets gris/vert clair/vert foncé cohérents avec la progression. **Bug résolu en cours d'impl** : 1re version bloquait le cold-start sur l'enrichment 217k features (~5-15 s sur device, beaucoup plus lent que sur PC) → carte vide ~20 s. Refactor : enrichment déplacé du cold-start vers le LaunchedEffect mid-session debounced, cache process-singleton dans `ArbresApp` pour les remounts.

### Sous-groupe I — Cohérence du compteur splash ✅

**Problème UX** : le splash annonçait « Réveil des **217 855** arbres parisiens » (chiffre hardcodé dans `MapOverlays.kt`), mais au dezoom max sur la carte, le cluster unique affiche **213 042** (`point_count` du GeoJSON réel). Différence de 4 813 = lignes du CSV OpenData filtrées au build par `tools/build_dataset.py` (`if genre is None or espece is None: continue`). Le user tatillon dezoome à fond et a l'impression qu'on lui a survendu de quelques milliers d'arbres.

**Solution** : la carte est la source de vérité, le splash s'aligne. On lit `dataset-stats.json:totalArbres` (déjà chargé eagerly via `DatasetStats.load` → `ArbresApp.datasetStats`) au lieu d'un nombre fixe — bénéfice secondaire : résilient aux futurs rebuilds du dataset OpenData.

- [x] **Lecture dynamique dans `ColdStartSplash`** ✅ — `rememberDatasetStats()` (helper Compose déjà adopté par `ArboretumScreen`) + `NumberFormat.getInstance(Locale.FRANCE).format(...)` pour conserver l'espace insécable étroit U+202F (« 213 042 ») cohérent avec la typographie du splash. Mémoïsé via `remember(datasetStats.totalArbres)` (le `Int` ne change pas pendant la vie du process, mais la stabilité est gratuite).
- [x] **Alignement doc** ✅ — toutes les mentions runtime-visibles synchronisées : `README.md:11`, `TESTS.md:38` (texte cold start), `TESTS.md:39` (durée), `TESTS.md:67` (smoke 213k pins + matche le cluster dezoomé), `docs/vision-jeu.md:25` et `:69`, `ROADMAP.md:11` (217 855 garde une mention pédagogique pour expliquer l'écart). Pas touché aux historiques de commits ni aux logs de dev.

### Verrou de fin de phase

- [ ] **Bump `versionCode → 9` / `versionName → "0.9.0"`** avant le tag v1.0.0 de Phase 11.
- [ ] Smoke device complet sur les 4 sous-groupes — `TESTS.md` à amender avec une section « Phase 10.5 ».
- [ ] `./gradlew test detekt` verts ; baseline detekt régénérée si refacto Remarquables (sous-groupe C) introduit du complexe.

## Phase 11 — Préparation de la release v1.0.0

> Plan détaillé issu de l'audit pré-public (équipe de 7 sous-agents — privacy, sécurité, hygiène repo, doc, légal, migration, release). Détail complet des findings, drafts et commandes : [docs/audit-pre-public.md](docs/audit-pre-public.md). Charge totale ~3,5 j, ordre logique : identité → code → légal → doc → release.

### 11A — Identité & nettoyage repo (avant tout push public)

- [ ] **P0** Backup git complet : `git clone --mirror . /tmp/arbre-app-backup-$(date +%Y%m%d).git`.
- [ ] **P0** Renommer le repo GitHub `m4xim1nus/Arbres` → `m4xim1nus/CanoPaname` (Settings → General → Rename, action manuelle web). GitHub installe une redirection 301 automatique depuis l'ancien nom. Mettre à jour le remote local : `git remote set-url origin git@github.com:m4xim1nus/CanoPaname.git`. À faire **avant** le rewrite filter-repo.
- [ ] **P0** `git filter-repo --email-callback` pour remplacer `mlv@spirtech.com` par `m4xim1nus@users.noreply.github.com` (40 commits affectés).
- [ ] **P0** Vérification post-rewrite : `git log --all --pretty=format:'%ae' | sort -u` ne contient plus que l'alias GitHub + `noreply@anthropic.com`.
- [ ] **P1** Étendre `.gitignore` : `tools/.essences-cache/`, `tools/.remarquables-cache/`, `__pycache__/`, `*.pyc`, `manual_tests/`, `.claude/`.
- [ ] **P1** Déplacer `manual_tests/` (3,9 Mo screenshots dev) hors repo (`~/dev/arbre-app-private/manual_tests/`).
- [ ] **P1** Committer le wrapper Gradle : `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.
- [ ] **P1** Supprimer `TESTS.md` du repo (archive locale).
- [ ] **P3** Validation : `gitleaks detect --source .` retourne 0 finding.

### 11B — Hardening code & assets (avant tag v1.0.0)

#### Privacy & sécurité Manifest
- [ ] **P1** `android:allowBackup="false"` dans `AndroidManifest.xml:16` (cohérent avec promesse "tout reste local"). Vider ou supprimer `backup_rules.xml` / `data_extraction_rules.xml`.
- [ ] **P1** Ajouter `app/src/main/res/xml/network_security_config.xml` HTTPS-only + référencer via `android:networkSecurityConfig` dans `AndroidManifest.xml:14`.
- [ ] **P2** Supprimer `<uses-permission ACCESS_COARSE_LOCATION>` (FINE l'inclut déjà sur API 31+, COARSE inutile vu `MAX_DISTANCE_M = 30`).

#### Capture & backup
- [ ] **P1** Strip EXIF (GPS, Make, Model, Software, ImageUniqueId) après chaque `TakePicture()` dans `CaptureLauncher.kt:107-118` via `androidx.exifinterface`.
- [ ] **P1** Hardener `BackupImporter.kt:80-104` : cap `MAX_PHOTO_BYTES = 10 MB`, `MAX_TOTAL_BYTES = 500 MB`, `entryCount ≤ 10000`, magic bytes JPEG `FF D8 FF`, refus `\\` et `..` dans basename.
- [ ] **P2** Strip `Log.i/v/d` en release via `proguard-rules.pro` (`-assumenosideeffects class android.util.Log { ... }`).

#### Migration & forward-compat
- [ ] **P0** Retirer `fallbackToDestructiveMigration()` de `ArbreDatabase.kt:64` — sinon wipe silencieux des captures à toute future migration foireuse.
- [ ] **P1** `exportSchema = true` dans `@Database`, KSP arg `room.schemaLocation` dans `app/build.gradle.kts`, committer `app/schemas/app.arbre.data.ArbreDatabase/2.json`.
- [ ] **P1** Migrer `photoPath` en basename relatif (`{uuid}.jpg`) — `MIGRATION_2_3` + helper `Capture.resolvedFile(context)` + adapter `PhotoThumbnail.decodeFile`. Évite la casse au device transfer / multi-user / debug↔release.
- [ ] **P1** Test `MigrationTestHelper` : `app/src/androidTest/java/app/arbre/data/MigrationTest.kt` couvrant `MIGRATION_1_2` et `MIGRATION_2_3`.
- [ ] **P2** `tools/build_dataset.py` crash explicite si `species-index.json` absent **et** `arbres-paris.db` existe déjà avec rows (au lieu du `[warn]` actuel).

### 11C — Légal & attributions

- [ ] **P0** Embarquer `app/src/main/assets/licenses/Fraunces-OFL.txt` (texte OFL 1.1 + copyright "Copyright 2020 The Fraunces Project Authors…"). Bloquant légal sans ça.
- [ ] **P0** Créer `NOTICE.md` racine (draft annexe B du rapport d'audit) listant : OpenData Paris ODbL, OSM, OpenFreeMap, Wikipedia FR CC BY-SA, Fraunces OFL, MapLibre BSD-2, AndroidX/Compose/Kotlin/Room Apache-2.0, org.json.
- [ ] **P0** Créer `app/src/main/assets/databases/ODbL-NOTICE.txt` avec wording §4.3 ODbL pour les 2 datasets (`les-arbres` + `arbresremarquablesparis`).
- [ ] **P0** Ajouter ligne "Source : Wikipedia FR · CC BY-SA 4.0" sous le summary dans `WikipediaBlock` (`SpeciesDetailScreen.kt:435-475`) avec lien `https://creativecommons.org/licenses/by-sa/4.0/`.
- [ ] **P0** Vérifier sur device que MapLibre affiche le bouton attribution OSM/OpenFreeMap (par défaut `uiSettings.isAttributionEnabled = true`). Si non visible, le forcer.
- [ ] **P1** `Routes.ABOUT` + `AboutScreen` Compose accessible depuis `ProfileScreen.kt` sous `HowToPlayEntry` : version (BuildConfig), attributions complètes, licences tierces, lien repo, mention "non affilié à la Ville de Paris".
- [ ] **P2** Étoffer copyright `LICENSE` : `Copyright (c) 2026 m4xim1nus (https://github.com/m4xim1nus)`.
- [ ] **P3** Recherche manuelle "CanoPaname" sur https://bases-marques.inpi.fr/ (5 min avant push public).

### 11D — Documentation publique

- [ ] **P0** Refonte `README.md` (statut v1.0, 3-4 screenshots `docs/screenshots/`, install Obtainium + fingerprint, permissions justifiées, lien PRIVACY, FAQ, attributions, note "no PR externe"). Squelette annexe D du rapport d'audit.
- [ ] **P0** Créer `CHANGELOG.md` Keep-a-Changelog avec `[1.0.0]` = synthèse phases 0 → 10.5 (draft annexe E du rapport d'audit).
- [ ] **P0** Créer `PRIVACY.md` ~200 mots tutoiement (draft annexe C du rapport d'audit).
- [ ] **P1** Épurer `CLAUDE.md` (garder le nom pour l'auto-load Claude Code) : supprimer la section `## Setup (déjà fait sur cette machine)`, neutraliser les marqueurs « Phase 10.5 sous-groupe X » dans le corps technique, ajouter un préambule de 2 lignes clarifiant que le fichier est consommé par Claude Code et sert de guide structurel pour le dev assisté.
- [ ] **P1** Pass d'épuration des commentaires Kotlin (~0,5 j) : relire les fichiers non-triviaux (`MapScreen`, `MapLayers`, `BackupImporter`, `BadgeEvaluator`, `CaptureLauncher`, `ArbreDatabase`, `SplashTipsController`, `SpeciesDetailScreen`) et appliquer la règle « garder uniquement ce qui justifie un *pourquoi* non-évident — supprimer tout ce qui décrit *quoi* ». Les marqueurs de phase (`// Phase 10.5 sous-groupe F`, `// Sprint I`) et les explications de cycle de dev partent. Garder les commentaires sur les contraintes cachées (thread MapLibre, ordinal Season persisté, contrat de format GeoJSON, etc.).
- [ ] **P1** Épurer `ROADMAP.md` (ce fichier) : supprimer `## Idées en vrac`, supprimer `### Bugs Phase 9 corrigés`, condenser Phase 10.5 (lignes 183-303 → ~15 lignes). Cible ~80 lignes au total.
- [ ] **P1** Sélectionner 3-4 screenshots depuis l'archive `manual_tests/20260504/` vers `docs/screenshots/` (recadrer + vérifier que coords GPS visibles ne pointent pas le domicile).
- [ ] **P1** Créer `.github/release-template.md` (highlights + permissions + checksum + lien Obtainium). Draft annexe I du rapport d'audit.
- [ ] **P2** Créer `SECURITY.md` ~8 lignes (contact + scope perso).
- [ ] **P2** Aligner `settings.gradle.kts` : `rootProject.name = "canopaname"`.

### 11E — Pipeline release & passage public

- [ ] **P0** Générer keystore release prod (`keytool -genkey -v -keystore arbres-release.jks ...` cf. DEVELOPING.md). Conserver hors-repo + hors-machine (perte = plus jamais d'update).
- [ ] **P0** Créer 4 secrets GitHub : `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- [ ] **P0** Créer `.github/workflows/release.yml` avec décodage keystore + **guard anti-debug-signing** (apksigner CN check, fail si `CN=Android Debug`) + génération SHA256. Draft annexe G du rapport d'audit.
- [ ] **P1** Créer `.github/workflows/build.yml` avec `assembleDebug + test + detekt + lint` sur push/PR (stub assets gitignored). Draft annexe F du rapport d'audit.
- [ ] **P1** Naming APK Obtainium : `applicationVariants.all { outputFileName = "canopaname-v\${versionName}-\${buildType}.apk" }` dans `app/build.gradle.kts`.
- [ ] **P1** Créer `RELEASE.md` (procédure pré-requis + checklist 10 étapes). Draft annexe H du rapport d'audit.
- [ ] **P1** Bump `versionCode = 10000`, `versionName = "1.0.0"` (scheme `major*10000 + minor*100 + patch`).
- [ ] **P1** **Smoke test manuel APK release signé prod sur device GrapheneOS** : carte (MapLibre), capture (Room insert + EXIF strip), redémarrage app (Room read + DataStore), export+import ZIP. Si OK → bon pour tag.
- [ ] **P1** Bascule visibilité GitHub : Settings → Make public.
- [ ] **P1** Tag : `git tag v1.0.0 && git push origin v1.0.0`. Vérifier que le workflow release se déclenche, produit l'APK draft.
- [ ] **P1** Tester l'APK draft sur device fresh, vérifier signature `apksigner verify --print-certs`. Publier la Release.
- [ ] **P1** Configurer Obtainium : URL repo `https://github.com/<user>/canopaname`, regex APK `canopaname-v.*-release\.apk$`, version detection par tag.
- [ ] **P2** Squelette `fastlane/metadata/android/fr-FR/{title,short_description,full_description,changelogs/}.txt` (option F-Droid future).
- [ ] **P3** ABI splits : trancher **non**, garder universel (59 Mo OK pour family & friends, simplifie Obtainium).
