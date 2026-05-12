# CLAUDE.md

Ce fichier est consommé automatiquement par Claude Code (claude.ai/code) à chaque session ouverte dans ce repo. Il sert de guide structurel pour le dev assisté : architecture, conventions, gotchas. Garde-le concis et factuel — un visiteur curieux passe par `README.md` et `ROADMAP.md`, pas par ici.

## Contexte produit

App **Android natif** type « Pokémon GO des arbres parisiens », à usage **personnel + family & friends**. Pas grand public : pas de classement, pas d'anti-cheat lourd, pas de backend multijoueur. Single-player avant tout. Voir `ROADMAP.md` pour le périmètre par phase et `README.md` pour le pitch.

Conséquences directes pour les choix techniques :
- Stockage local seulement (Room/SQLite). Pas de service cloud, pas d'auth.
- Une seule cible : Android. Pas de iOS, pas de cross-platform au moins jusqu'à la v1.
- Quand un arbitrage simplicité ↔ scale se présente, **toujours choisir simplicité**.

## Stack

- **Kotlin** + **Jetpack Compose** + **Material 3**, single-Activity + `NavHost` Compose.
- **MapLibre Native Android** (`org.maplibre.gl:android-sdk`) pour la carte. Jamais Google Maps : l'app doit tourner sur GrapheneOS sans Google Play Services.
- **Room** (avec KSP) pour le cache local du dataset OpenData.
- **DataStore Preferences** (`androidx.datastore.preferences`) pour les flags persistés léger (onboarding done). Pas de `SharedPreferences`.
- **Police Fraunces SemiBold** (~70 KB OFL, embarquée en `res/font/`) sur display/headline/titleLarge ; Material 3 par défaut ailleurs.
- **Gradle Kotlin DSL** + **version catalog** dans `gradle/libs.versions.toml` — toutes les versions et dépendances passent par là, ne pas hardcoder dans les `build.gradle.kts`.
- `minSdk = 26`, `targetSdk = compileSdk = 35`, `jvmTarget = 17`.

Données : OpenData Paris [`les-arbres`](https://opendata.paris.fr/explore/dataset/les-arbres/) (~210 k arbres) + [`arbresremarquablesparis`](https://opendata.paris.fr/explore/dataset/arbresremarquablesparis/). Embarquées dans l'APK ou téléchargées au premier lancement (voir ROADMAP).

## Commandes

Le wrapper Gradle n'est pas committé en binaire — si on repart de zéro :
```bash
# Avec Android Studio : ouvrir le dossier, il génère le wrapper automatiquement.
# Sinon, depuis une install Gradle ≥ 8.10 :
gradle wrapper
```

Ensuite :
```bash
./gradlew assembleDebug              # APK debug → app/build/outputs/apk/debug/
./gradlew installDebug               # Pousse sur appareil/émulateur connecté en ADB
./gradlew test                       # Tests unitaires JVM
./gradlew :app:testDebugUnitTest --tests "app.arbre.SomeTest"   # Test unique
./gradlew connectedDebugAndroidTest  # Tests instrumentés (appareil requis)
./gradlew lint                       # Android Lint
./gradlew assembleRelease            # APK release (signé prod si local.properties OK, sinon fallback debug)
```

Pour pousser sur le téléphone GrapheneOS sans Studio : `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### Build release signé

Pour produire un APK release signé prod :

```bash
# 1. Générer le keystore (une seule fois, à conserver hors-repo et hors-machine)
keytool -genkey -v -keystore canopaname-release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias canopaname

# 2. Renseigner local.properties (jamais committé, déjà dans .gitignore) :
#   RELEASE_STORE_FILE=canopaname-release.jks
#   RELEASE_STORE_PASSWORD=...
#   RELEASE_KEY_ALIAS=canopaname
#   RELEASE_KEY_PASSWORD=...

# 3. Build
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleRelease
# → app/build/outputs/apk/release/canopaname-vX.Y.Z-release.apk (signé prod)

# 4. Vérifier la signature
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose \
    app/build/outputs/apk/release/canopaname-vX.Y.Z-release.apk
```

Sans clé renseignée dans `local.properties`, `assembleRelease` continue de marcher (fallback signing debug) — utile pour smoke-test `isMinifyEnabled = true` sans manipuler de secrets. Le fallback est implémenté côté `app/build.gradle.kts` (lecture conditionnelle de `local.properties`). Le `.gitignore` exclut déjà `*.jks`, `*.keystore` et `local.properties`.

## Architecture

Projet mono-module `:app`. Structure (package racine `app.arbre`) :

- `data/` —
  - **Lecture seule** : `Arbre` (modèle), `ArbreEntity` + `ArbreDao` + `ArbreDatabase` (Room v2), `ArbreRepository`. Base pré-cuite dans `assets/databases/arbres-paris.db` et copiée par `Room.createFromAsset(...)` au 1er lancement. API : `arbresDansBbox(...)` (Flow), `arbreParId(...)` (suspend), `arbresRemarquables()`, `compterParEspece(...)`, `arbresParIds(ids)` (batch fetch utilisé par `BadgeEvaluator`).
  - **Écriture** : `Capture`/`CaptureEntity` + `CaptureDao` + `CaptureRepository`. La table `capture` est ajoutée par `MIGRATION_1_2` à la 1re ouverture (l'asset DB ship en v1 sans cette table). Inclut une colonne `season: Int` (ordinal de `Season`) + `captureExists(arbreId, timestamp)` pour la dédup à l'import. Flows clés : `capturedSpeciesIndices: Flow<Set<Int>>`, `capturedRemarquableIds: Flow<Set<Long>>`, ainsi que les variantes scopées saison.
  - **Lookup statique** : `SpeciesIndex.kt` charge `assets/species-index.json` une fois (entrée `(genre, espece) -> int`). `DatasetStats.kt` charge `assets/dataset-stats.json` (totaux pour les compteurs Arboretum). `SpeciesInfo.kt` charge `assets/species-info.json` (texte Wikipedia FR + stats Paris par espèce, pour la fiche-espèce). `RemarquableInfo.kt` charge `assets/remarquable-info.json` (qualification, résumé, description, cultivar). `GenreInfo.kt` charge `assets/genre-info.json` (résumé Wikipedia FR + stats par genre + `nomFr` vernaculaire). `ArrSpeciesIndex.kt` charge `assets/arr-species.json` (slug `ArrKey` → liste des `speciesIndex` recensés dans l'arrondissement — dénominateur des badges « Familier d'arrondissement »). `SplashTips.kt` charge `assets/splash-tips.json` (banque ~240 tips informatifs rotatifs du `ColdStartSplash`, fusion build static + dataset). Tous singletons dans `ArbresApp`.
  - **Saisonnalité** : `Season.kt` enum 4 saisons calendaires (WINTER/SPRING/SUMMER/AUTUMN, ordinal stable car persisté dans `CaptureEntity.season`) + helpers `current()`, `fromTimestamp()`, `fromInstant()`. `SeasonStore.kt` singleton **en mémoire seulement** (`MutableStateFlow<Season>` initialisée à `Season.current()`) — la saison vive est l'instance par défaut, on retombe dessus à chaque relancement.
  - **Onboarding** : `OnboardingStore.kt` — DataStore Preferences (file `onboarding`), 2 clés booléennes : `done` (le WelcomeScreen a été vu, lu par `ArbresNavHost` pour `startDestination`) + `splash_intro_seen` (la séquence intro de 10 tips du splash a tourné, lu par `SplashTipsController`). API : `onboardingDone`/`splashIntroSeen: Flow<Boolean>`, `markDone()`/`markSplashIntroSeen(): suspend`.
  - **Badges** : tous **binaires** (`BadgeState(def, unlockedAt)` — un critère franchi une fois fige le ts). `Badge.kt` : `BadgeCatalog.ALL` = 10 badges statiques en 5 catégories (Découverte, Botanique, Géographie, Remarquables, Démesure) ; **2 familles dynamiques** dérivées du dataset s'y ajoutent via `BadgeCatalog.full(speciesIndex, genreInfo, arrSpecies)` — « Familier des … » (`familier_genre_*`, un par genre avec ≥ `GENRE_FAMILIER_MIN_SPECIES` = 7 espèces identifiées → 26 badges ; libellé pluriel via `GenreInfo.nomFr` ; critère = toutes les espèces identifiées du genre capturées) et « Familier du … » (`familier_arr_*`, les 20 arrondissements + 2 bois ; dénominateur = `ArrSpeciesIndex` ; critère = toutes les espèces de l'arrondissement capturées). **Pas de table Room** — tout dérivé à la volée. `BadgeEvaluator.evaluate(captures, arbresById, speciesInfo, speciesIndex, arrSpecies): Map<String, Long>` (id → ts de déblocage) : balayage chronologique unique, `unlockOnce`, `unlockedAt` figé sur la capture déclenchante ; `BadgeRepository` assemble `BadgeCatalog.full` puis zippe. Couverture arr propagée par `SpeciesIndex.effectivelyCapturedSpecies` (capturer un chêne couvre `Quercus sp.`). Arrondissement parsé via `parseArrKey` (data/ArrKey.kt) — format brut OpenData (« …, PARIS 12E ARRDT ») ou normalisé ; `ArrKey.idSlug()` (slug stable, miroir de `arr_key_slug` côté build) et `ArrKey.romanNumeral()` pour les ids de badge et les logos.
  - **Helpers Compose** : `RepositoryProvider.kt` expose `rememberArbreRepository()`, `rememberCaptureRepository()`, `rememberSpeciesIndex()`, `rememberDatasetStats()`, `rememberSpeciesInfoRepository()`, `rememberRemarquableInfoRepository()`, `rememberGenreInfoRepository()`, `rememberArrSpeciesIndex()`, `rememberBadgeRepository()`, `rememberSeasonStore()`, `rememberOnboardingStore()`, `rememberSplashTipsRepository()`, `rememberBackupExporter()`, `rememberBackupImporter()` — plus les variantes `Context.xxx()` pour les VMs.
- `backup/` — `BackupExporter.kt` / `BackupImporter.kt` / `BackupModels.kt` / `BackupFilename.kt`. Sauvegarde locale ZIP via Storage Access Framework (`ACTION_CREATE_DOCUMENT` MIME `application/zip` à l'export ; `ACTION_OPEN_DOCUMENT` filtré `application/zip` + `application/octet-stream` à l'import). Contenu : `meta.json` (versionCode, versionName, `schemaVersion = 1`, count captures, `exportedAt`) + `captures.json` (sans `id` Room autoincrement) + `photos/` (UUID.jpg). Fusion **additive** : dédup `(arbreId, timestamp)` via `CaptureDao.captureExists(...)`, refus dur si `schemaVersion > CURRENT_SCHEMA_VERSION`. Pas de transaction Room enveloppante volontairement (un import partiel laisse les captures déjà ingérées, dédup garantit la reprise). Photo absente du zip → capture quand même insérée + compteur `photosMissing` remonté à l'UI.
- `ui/` — Compose par écran. `ArbresNavHost` avec `startDestination` conditionnelle (`Routes.WELCOME` si `onboardingDone == false`, sinon `Routes.MAP`). Routes : `WELCOME`, `WELCOME_REPLAY`, `MAP`, `MAP_FILTERED`, `ARBORETUM`, `SPECIES`, `PROFILE`, `BADGES`, `REMARQUABLES`, `REMARQUABLE_DETAIL`.
  - `ui/onboarding/WelcomeScreen.kt` — un seul écran scrollable : hero logo, accroche Fraunces, animation Compose pure (canopée qui scale + tint gris→vert), note privacy. Au tap « Commencer » : check `ACCESS_FINE_LOCATION` + `RequestPermission()` si besoin, puis `onContinue()` quel que soit le résultat. Mode `readOnly = true` (route `WELCOME_REPLAY` lancée depuis le Profil) montre « Fermer » au lieu de « Commencer ».
  - `ui/map/` — `MapScreen` (carte + hub : FAB GPS, FAB ★ mode chasse, FAB Arboretum, sheet détail, splash overlay cold start, `FilterSplash` dédié si `MAP_FILTERED`), `MapViewModel` (caméra + `openedArbre` + `pendingCapture` via `SavedStateHandle`), `CaptureLauncher.kt` (pipeline FileProvider + caméra + GPS guard), `PendingCapture.kt`, `HuntPanel.kt` (panneau bas pleine largeur ouvert par le FAB ★ : radar `withFrameNanos`, remarquable non découvert le plus proche recalculé à chaque tick, distance live 5 s en phase avec le balayage ; `huntActive` en `remember` côté `MapScreen` → fermeture auto à la sortie de l'écran ; remplace l'ancienne snackbar éphémère « remarquable proche »), `SplashTipsController.kt` (rotation 7 s des tips informatifs sous le wordmark du `ColdStartSplash`, mode intro figé au mount via `.first()` puis bascule en shuffle aléatoire).
  - `ui/detail/ArbreDetailScreen.kt` — `ModalBottomSheet` qui split « Arbre inconnu » / fiche complète selon `isDiscovered`. Fiche enrichie pour les remarquables : qualification + résumé + description + cultivar, lien fiche PDF Ville de Paris.
  - `ui/arboretum/ArboretumScreen.kt` — header `X/Y espèces` (scopé saison via `SeasonSelector`), cards par espèce (count Paris, photos, 1re capture), section remarquables individuelle. Cards d'espèce cliquables → `Routes.SPECIES`. `ArchiveBanner` si saison ≠ vive.
  - `ui/remarquables/` — `RemarquablesScreen` Catalogue remarquables dédié (FAB ★ Liste + 🔍 Loupe), `RemarquableDetailScreen` fiche enrichie.
  - `ui/species/` — `SpeciesDetailScreen` (fiche-espèce : identité, galerie photos user, summary Wikipedia + lien externe, stats Paris, mini-carte filtrée, célébration 1re capture si `celebrate=true`) ; `SpeciesMiniMap` (composable carte non-clusterisée filtrée sur `sk`).
  - `ui/profile/ProfileScreen.kt` — accessible TopStart de la carte. En tête (si ≥ 1 capture) : ligne « X jours depuis ta première capture » + carte `Progression` (jusqu'à 7 barres Material 3). Section « Badges » : rangée « Derniers badges » (3 derniers déblocages, `BadgeUnlock(def, displayLabel = def.label, unlockedAt)`, cercle = `BadgeIconCircle`) + lien `AllBadgesEntry` → `BadgesScreen`. Section « Infos » : `HowToPlayEntry` → `Routes.WELCOME_REPLAY`, `AboutEntry` → `AboutScreen`. Section « Sauvegarde » : boutons Export/Import (SAF), snackbar feedback.
  - `ui/badges/` — `BadgesScreen` grille `LazyVerticalGrid` 3 colonnes, spans full-width pour l'en-tête (« X / N débloqués », N = `badgeRepo.catalog.size`) et les titres de section (« Débloqués » / « À débloquer »). `BadgeCard` affiche `BadgeDef.visual()` dans le cercle : `ImageVector` Outlined pour la plupart (`BadgeDef.icon()` — genres « Familier » → `Forest`), **texte** pour les badges « Familier d'arrondissement » (chiffre romain I…XX en Fraunces, ou « Boulogne »·« Vincennes » pour les bois), silhouette `Lock` grise si verrouillé ; + libellé, critère, date. `tertiaryContainer` si débloqué, `surfaceVariant` sinon. Le cercle est le composable partagé `ui/badges/BadgeIconCircle.kt` (réutilisé par la rangée « Derniers badges » du Profil) ; `BadgeVisual` (`Vector`/`Label`) dans `ui/badges/BadgeIcons.kt`.
  - `ui/common/` — `PhotoThumbnail.kt` (décode fichier image local avec downsample paramétrable, partagé Arboretum + fiche-espèce). `SeasonSelector.kt` (pill compacte TopAppBar Arboretum/Remarquables). `ArchiveBanner.kt` (bandeau plein-écran en mode archive read-only).
- `ui/theme/` — `ArbresTheme` (Material3), `Color.kt` palette verte/brune, `ArbresColors.kt` tokens couleur custom (or, feuilleClaire, feuilleSombre, ecorce, remarquableOrange) exposés via `staticCompositionLocalOf` (`LocalArbresColors` interne) + extension publique `MaterialTheme.arbresColors`. `Type.kt` charge la Fraunces SemiBold depuis `res/font/`. Tinting saisonnier discret du `surface` (light + dark).
- `util/LocationProvider` — wrapper autour de `LocationManager` natif (sans GMS).
- `tools/build_dataset.py` — génère `arbres-paris.db` (Room) **et** `arbres-paris.geojson` (MapLibre, propriétés `id`/`remarquable`/`sk`) **et** `species-index.json` **et** `dataset-stats.json` **et** `species-info.json` (fiches Wikipedia FR + stats Paris par espèce) **et** `genre-info.json` (fiches genre + `nomFr` vernaculaire) **et** `remarquables-info.json` **et** `splash-tips.json` (fusion `tools/splash-tips-static.json` manuel + ~60 tips dataset générés des agrégats CSV ; sanity check des placeholders `{xxx}` au build) **et** `arr-species.json` (slug `ArrKey` → `[speciesIndex]` recensés ; clé dérivée par `arr_key_slug`, miroir exact de `parseArrKey` côté Kotlin — invariant à tenir si tu touches à l'un ou l'autre) à partir du CSV OpenData. Filtre les rows sans `genre`/`espece`. Préserve les `speciesIndex` entre runs (lit le `species-index.json` existant) — sans ça, regénérer casserait les captures déjà stockées en Room (qui réfèrent l'espèce par int). Le schéma SQL doit rester rigoureusement aligné avec celui que Room produit pour `ArbreEntity`. Les ~907 appels Wikipedia REST sont cachés dans `tools/.wikipedia-cache/{sk}.json` (incluant les misses) pour rendre les builds suivants instantanés.

Conventions :
- **MapView intégrée via `AndroidView`** dans `MapScreen`. Toujours passer par `DisposableEffect` pour relayer les `onCreate/onStart/onResume/onPause/onStop/onDestroy` du `MapView` — sans ça, fuites mémoire ou crashes en navigation.
- **Source GeoJSON clusterisée** chargée en RAM puis poussée via `setGeoJson(jsonString)`. Ne pas utiliser le constructeur `GeoJsonSource(id, URI("asset://..."))` avec clustering : MapLibre tile l'asset par bloc et le clustering Supercluster casse cross-tile (apparence : grilles d'arbres à zoom haut, rien à dezoom). Voir `MapScreen.addArbresLayers`.
- **Hit-test à deux niveaux** : query d'abord la layer `arbres-clusters` (tap → `getClusterExpansionZoom` + zoom in), puis `arbres-points` (tap → fiche).
- **Coloration grise/verte des points** : expression `case(remarquable, match-id, match-sk)` reconstruite via `buildDiscoveryExpression` à chaque changement des Flows captures, appliquée par `setProperties(circleColor(...))` sur la layer `arbres-points`. Les ids `Long` sont cast en `Int` pour le `match` (les `idbase` parisiens tiennent dans 32 bits, évite un quirk historique du DSL Java MapLibre sur les longArrays).
- **Coloration des clusters** : 3 buckets `gris / vert clair / vert foncé` selon `discovered_count` vs `point_count`, accumulés via `clusterProperty`. Le flag `discovered: 0|1` est embarqué par feature à l'enrichissement (`enrichGeoJsonWithDiscovery` dans `MapLayers.kt`) — coût ~5-15 s sur device pour 217k features, **trop lourd pour bloquer le 1er paint des pins individuels**. Pipeline pensé pour ça :
  - **Cold-start fresh** (process vient de démarrer, `app.enrichedGeoJson.value == null`) : pose le rawJson nu via `setArbresGeoJson` → pins visibles en ~700 ms, clusters tous gris (`discovered_count` non-défini → bucket 0). Pas d'enrichment ici, intentionnellement.
  - **`LaunchedEffect` mid-session debounced 1 s** : c'est lui qui fait le **1er enrichment** ~1 s après le 1er paint, en background (`Dispatchers.Default`), puis re-pousse via `setArbresGeoJson` (~300-500 ms UI). Les clusters s'allument en 2e wave. Mémoise dans `app.enrichedGeoJson` + `app.lastEnrichmentKey` (Pair des sets) pour les remounts.
  - **Remount `MapScreen`** (retour Profil → Map, sets inchangés) : cold-start lit `app.enrichedGeoJson.value` directement → 1 seul `setGeoJson` enrichi, pins ET clusters d'un coup. Le LaunchedEffect mid-session voit l'émission initiale, compare contre `lastEnrichmentKey`, skip le re-enrich.
  - **Mid-session après capture** : nouveau set émis → debounce 1 s → enrich + push. Le pin individuel est déjà passé au vert via `applyDiscoveryColor` (sans debounce) avant que le cluster ne se mette à jour.
  - Mode filtré : enrichment au cold-start (json < 1 Mo, instantané), `LaunchedEffect` mid-session skippé.
  - **Contrat** : `enrichGeoJsonWithDiscovery` repose sur le même contrat de format que `filterGeoJsonBySpecies` (`sk` dernière clé de `properties`, ordre stable Python 3.7+). Si tu changes l'ordre côté `tools/build_dataset.py`, casser le contrat se traduit par des clusters tous gris (le flag `discovered` mal injecté serait silencieusement ignoré par MapLibre).
- **Capture flow** : `rememberCaptureController` dans `ui/map/CaptureLauncher.kt`. Au tap : permission CAMERA → GPS frais (< 30 s, < 30 m) → URI FileProvider sous `getExternalFilesDir(null)/captures/{uuid}.jpg` → `TakePicture()` → INSERT Room avec check `file.length() > 0`. L'état pendant (`PendingCapture`) est sauvegardé dans le `SavedStateHandle` du `MapViewModel` pour survivre à un process death pendant l'intent caméra.
- **Migration Room sur asset DB** : pour ajouter une table à l'asset DB (qui ship en v1), passer la DB en version 2 dans `@Database`, ajouter `addMigrations(MIGRATION_1_2)` dans `databaseBuilder`. Le `CREATE TABLE` de la migration **doit matcher pile-poil** ce que Room génère pour la nouvelle entity (sinon le schemaCheck rejette au runtime). Pour l'asset DB elle-même, n'altérer que des tables côté script Python — pas de migration sur les tables seedées.
- **Géoloc** : `LocationManager` natif uniquement, `play-services-location` retiré. Sur GrapheneOS sans GMS, `LocationEngineDefault` de MapLibre retombe sur `AndroidLocationEngineImpl` (basé sur `LocationManager`). **Bridge MapLibre → `LocationProvider`** : `attachMapLibreLocationBridge(component)` (file-private dans `MapScreen.kt`) attache un `LocationEngineCallback` au `component.locationEngine` ; chaque fix MapLibre nourrit `LocationProvider.feedExternalFix(loc)` qui passe par le même filtre `isBetterFix` que les natural updates. Indispensable au tout 1er run post-onboarding où un `LocationListener` natif registré juste après un grant fresh de permission peut ne recevoir aucun update pendant ~10 s alors que MapLibre reçoit ses fix immédiatement. Cleanup via `removeLocationUpdates(callback)` au `onDispose` du `MapView`. `captureAvailability` est désormais non-bloquant (lecture pure de `currentLocation.value` filtrée sur âge) et le sheet recompute via `remember(openedArbre.id, currentLocation)`.
- **Caméra mémorisée** dans `MapViewModel.lastCamera` pour survivre au remount de `MapScreen` après visite de la fiche détail.
- **FileProvider** : authority `${applicationId}.fileprovider`, paths déclarés dans `res/xml/file_paths.xml` (uniquement `external-files-path` sous `captures/` — privé à l'app, effacé à la désinstallation).
- **Onboarding + nav conditionnelle** : `ArbresNavHost` calcule `startDestination` depuis `onboardingStore.onboardingDone.collectAsState(initial = null)`. `null` (round-trip DataStore initial) → fallback `MAP`, le splash overlay du `MapScreen` couvre déjà la transition donc pas de flicker. Au tap « Commencer », `popUpTo(WELCOME) { inclusive = true }` pour purger le backstack.
- **Splash tips** : la rotation des tips sous « Réveil des… » est pilotée par `rememberSplashTipText(...)` dans `SplashTipsController.kt`. **Ne pas observer `splashIntroSeen` via `collectAsState`** : on appelle `markSplashIntroSeen()` après la 1re rotation, le re-emit du Flow ferait re-launch le LaunchedEffect rotation et casserait la séquence intro. Le mode (`intro` vs `random`) est lu **une seule fois au mount** via `.first()` et figé dans un `mutableStateOf`. Idem pour le snapshot joueur : `combine(...).first()` une fois, pas de re-collect. Les keys du LaunchedEffect rotation sont volontairement réduites à `(repository, isIntroMode.value, canRotate)` — pas de `playerSnapshot.value` qui resetait l'index. Pour étendre la banque : ajouter des phrases dans `tools/splash-tips-static.json` (ne pas oublier d'ajouter à `intro` si tu veux les voir au tout-1er lancement, et 10 ids exactement). Les phrases dataset sont générées dynamiquement par `write_splash_tips()` à partir des agrégats CSV ; les placeholders `{xxx}` sont vérifiés au build contre le set runtime `{captureCount, speciesCount, remarquableCount, daysSinceFirst}`.
- **Saisonnalité** : `SeasonStore` est **en mémoire seulement** (volontairement non persisté — la saison vive est l'instance par défaut, retour explicite vers archive attendu chaque session). Les écrans qui montrent l'état de découverte (Map, Arboretum, Remarquables) lisent le flow ; les écrans globaux (Profil global, Badges) ignorent la sélection. `Season.ordinal` est stable car persisté tel quel dans `CaptureEntity.season` — ne pas réordonner l'enum.
- **Backup additif** : pas de transaction Room enveloppante. Re-import du même ZIP idempotent via dédup `(arbreId, timestamp)`. Bumper `BackupModels.CURRENT_SCHEMA_VERSION` quand le format change ; garder l'ancien lecteur en path versionné. Photos perdues → comptage `photosMissing` remonté, pas de blocage.
- **Badges** : tous binaires. Ajouter un badge **statique** = 1 `BadgeDef` dans `Badge.kt` (l'enregistrer dans `BadgeCatalog.ALL`) + 1 appel `unlockOnce(...)` dans `BadgeEvaluator.evaluate` + 1 branche dans `BadgeIcons.icon()`. Les familles **dynamiques** (`familier_genre_*` / `familier_arr_*`) sont assemblées par `BadgeCatalog.full(speciesIndex, genreInfo, arrSpecies)` à partir du dataset — pas d'ajout manuel par badge. Pas de table Room, pas de migration. `BadgeEvaluator.evaluate(...)` renvoie `Map<String, Long>` (id → ts), `BadgeRepository` zippe avec `BadgeCatalog.full`. `unlockedAt` figé sur la capture déclenchante — un balayage chronologique unique suffit.
- **Routes** : toute nouvelle destination passe par `Routes` (object) + un `composable(...)` dans `ArbresNavHost`. Les helpers (`Routes.species(sk)`, `Routes.mapFiltered(sk)`, `Routes.remarquableDetail(id)`, `Routes.map(pulseArbreId)`) construisent les paths. `Routes.MAP` est un pattern avec query param optionnel `pulseArbreId` ; quand présent, `MapScreen` lookup l'arbre, fly-to ~600 ms à zoom 20 (un seul pin tient à l'écran — aucun doute possible sur l'individu ciblé), puis halo pulse 2 s au callback `onFinish` (cf. `addOrUpdatePulseSource` + `animatePulse` dans `MapLayers.kt`, `ValueAnimator` JVM avec retrait auto à la fin). Pas d'ouverture du sheet : l'utilisateur tape l'arbre lui-même s'il veut la fiche. Câblé depuis fiche-remarquable (bouton bas) et `PhotoLightbox` (icône `Outlined.Map` `Alignment.TopStart`).
- **Tokens couleur** : ne pas hardcoder de `Color(0xFF...)` dans les composables. Aller chercher via `MaterialTheme.arbresColors` (or, écorce, feuilleClaire, feuilleSombre, remarquableOrange) ou via le scheme Material 3 standard. La Fraunces ne s'applique qu'aux niveaux display/headline/titleLarge — laisser le reste sur la pile Type Material par défaut.

## Décisions à connaître

- **Style de carte** : OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`) — gratuit, sans clé, OSM. Référence dans `res/values/strings.xml` → `map_style_url`. Dépendance externe gentille mais sans SLA ; à terme, prévoir un fallback (Versatiles, Protomaps self-host) si OpenFreeMap tombe.
- **MapLibre Android 11.11.0** — choisi pour le 16 KB page-size alignment côté `libmaplibre.so`. Warning résiduel possible sur `libandroidx.graphics.path.so` (tiré par Compose) : indépendant de MapLibre, à régler via bump du Compose BOM si encore visible.
- **Pas de Hilt / DI framework** au MVP. Singletons exposés via `ArbresApp` : `arbreRepository`, `captureRepository`, `speciesIndex`, `datasetStats`, `speciesInfoRepository`, `remarquableInfoRepository`, `genreInfoRepository`, `arrSpeciesIndex`, `splashTipsRepository`, `badgeRepository`, `seasonStore`, `onboardingStore`, `backupExporter`, `backupImporter`. Helpers Compose dans `data/RepositoryProvider.kt` (`rememberXxx()` + extensions `Context.xxx()` pour les VMs).
- **Licence** : MIT (cf. `LICENSE` à la racine).
- **Pas de feature flags, pas d'A/B**. C'est une app perso.
- **Pas de service externe au runtime.** La fiche-espèce ne fait pas exception : tout est pré-baké dans `assets/species-info.json` (texte Wikipedia FR + stats Paris) à build-time par `tools/build_dataset.py`. Les images Wikipedia sont volontairement absentes — les photos des captures utilisateur servent d'illustration.

## Workflow & docs

Trois fichiers se partagent la planification produit, sans recouvrement :

| Fichier | Rôle | Quand l'éditer |
|---|---|---|
| `ROADMAP.md` | Plan opérationnel vivant : *Cycle en cours* (très détaillé), *Prochains cycles* (1-3 noms, scope dégradé), *Cycles livrés post-1.0* (résumé 3-5 lignes), *Historique pré-1.0* (figé). | À chaque rotation de cycle, et quand le détail d'un prochain cycle se précise. |
| `BACKLOG.md` | File d'attente non ordonnée des items capturés (audit, retours, idées, bugs). 1 ligne = 1 item, format `- [TAG] description (origine, date)`. | Append-only en cours de session. Tri en lot au début d'un cycle. |
| `CHANGELOG.md` | Vérité release immuable (Keep a Changelog, SemVer). | À chaque tag, et là **seulement**. |

**Naming** : les cycles sont nommés par codename court (ex. *Vérité*, *Photos*, *Variantes*) — **jamais** par numéro SemVer. SemVer n'apparaît que dans `CHANGELOG.md` au moment du tag, choisi a posteriori selon ce qui a réellement shippé.

**Tags BACKLOG** : `[ ]` (à trier), `[→Codename]` (rangé dans un cycle), `[creuser]` (mérite réflexion avant arbitrage), `[refusé]` (tranché négatif, conserver la trace pour ne pas re-débattre). Origine : `audit`, `user:moi`, `user:F&F`, `gh#42`, `device-test`. Triage en 5 min en début de cycle.

**Procédure de rotation d'un cycle** (à exécuter quand l'utilisateur dit « ferme le cycle X » ou équivalent) :
1. Compresser le bloc « Cycle en cours » de `ROADMAP.md` à 3-5 lignes (intent + résultat + lien CHANGELOG entry).
2. Pousser ce résumé en tête de « Cycles livrés post-1.0 » avec date et version SemVer effective.
3. Vérifier que `CHANGELOG.md` a bien la nouvelle entrée `[X.Y.Z]` détaillée.
4. Promouvoir le cycle suivant de « Prochains cycles » → « Cycle en cours », et le détailler item par item depuis le BACKLOG (items `[→Codename]` du nouveau cycle).
5. Marquer dans `BACKLOG.md` les items absorbés par le cycle clôturé comme livrés (les retirer ou ligne barrée).

**GitHub Issues** = boîte aux lettres externe, pas backlog. Quand un retour arrive en issue, le rapatrier dans `BACKLOG.md` (avec `gh#N` en origine), répondre « noté, suivi via CHANGELOG » et fermer. Évite de gérer deux backlogs.

## Quand tu travailles ici

- Avant de toucher au build, regarde `gradle/libs.versions.toml` — c'est la source de vérité des versions.
- N'introduis pas de dépendance Google/Firebase/AdMob/Analytics. Le projet doit rester installable et utilisable sans Google Play Services.
- Si tu modifies `ArbreEntity`, regénère la base avec `python3 tools/build_dataset.py` (sinon Room rejette l'asset). Le schéma SQL du script doit matcher pile-poil ce que Room génère.
- Si tu ajoutes une nouvelle entity Room, **pas d'évolution du schéma asset** : ajouter une migration `MIGRATION_N_N+1` dans `ArbreDatabase` qui crée la table côté Room (cf. `MIGRATION_1_2` qui crée `capture`).
- **Ne supprime ni ne réindexe `species-index.json`** sans réfléchir : les rows `capture.speciesIndex` réfèrent ces ints. Le script de build préserve les indices existants — toujours commiter `species-index.json` avec la nouvelle version.
- **Ne réordonne pas `Season`** : son `ordinal` est persisté dans `CaptureEntity.season`. Ajouter un nouveau bucket = ajouter à la fin et migrer les rows existantes côté Room.
- Si tu ajoutes un badge statique : `BadgeDef` dans `data/Badge.kt` (l'enregistrer dans `BadgeCatalog.ALL`) + branche `unlockOnce(...)` dans `BadgeEvaluator.evaluate(...)` + branche dans `ui/badges/BadgeIcons.kt`. Le compteur du `BadgesScreen` se base sur `badgeRepo.catalog.size` — pas de constante hardcodée. Les familles « Familier » sont dérivées du dataset, pas à éditer badge par badge. Si tu touches `arr_key_slug` (build) ou `parseArrKey`/`ArrKey.idSlug` (Kotlin), garde-les alignés — sinon le dénominateur de `arr-species.json` ne correspond plus à ce que l'évaluateur accumule.
- Si tu changes le format de backup : bumper `BackupModels.CURRENT_SCHEMA_VERSION`, garder un path d'import versionné (le format actuel = v1), tester re-import du même zip = idempotent.
- Si tu ajoutes une route : la déclarer dans `Routes` (avec helper de construction si paramétrée) **et** ajouter le `composable(...)` correspondant dans `ArbresNavHost`. Les paramètres typés passent par `navArgument(...)`.
- Tout retour, idée ou bug capté en cours de session va d'abord dans `BACKLOG.md` (1 ligne, tag `[ ]` si non trié). Ne pas écrire directement dans `ROADMAP.md` — c'est la rotation de cycle qui promeut.
