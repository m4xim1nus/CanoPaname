# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
- **Gradle Kotlin DSL** + **version catalog** dans `gradle/libs.versions.toml` — toutes les versions et dépendances passent par là, ne pas hardcoder dans les `build.gradle.kts`.
- `minSdk = 26`, `targetSdk = compileSdk = 35`, `jvmTarget = 17`.

Données : OpenData Paris [`les-arbres`](https://opendata.paris.fr/explore/dataset/les-arbres/) (~210 k arbres) + [`arbresremarquablesparis`](https://opendata.paris.fr/explore/dataset/arbresremarquablesparis/). Embarquées dans l'APK ou téléchargées au premier lancement (voir ROADMAP).

## Setup (déjà fait sur cette machine)

- Android Studio installé dans `/opt/android-studio`, lancement par `studio` (PATH ajouté à `~/.bashrc`).
- Android SDK installé par Studio dans `~/Android/Sdk` (API 35).
- Le wrapper Gradle a été généré au premier import par Studio.
- Téléphone GrapheneOS branché en USB, debug ADB activé, autorisation persistante accordée à cet ordinateur.

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
./gradlew assembleRelease            # APK release (signé en debug par défaut, voir build.gradle.kts)
```

Pour pousser sur le téléphone GrapheneOS sans Studio : `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

Projet mono-module `:app`. Structure (package racine `app.arbre`) :

- `data/` —
  - **Lecture seule** : `Arbre` (modèle), `ArbreEntity` + `ArbreDao` + `ArbreDatabase` (Room v2), `ArbreRepository`. Base pré-cuite dans `assets/databases/arbres-paris.db` et copiée par `Room.createFromAsset(...)` au 1er lancement. API : `arbresDansBbox(...)` (Flow), `arbreParId(...)` (suspend), `arbresRemarquables()`, `compterParEspece(...)`.
  - **Écriture** : `Capture`/`CaptureEntity` + `CaptureDao` + `CaptureRepository`. La table `capture` est ajoutée par `MIGRATION_1_2` à la 1re ouverture (l'asset DB ship en v1 sans cette table). Flows clés : `capturedSpeciesIndices: Flow<Set<Int>>`, `capturedRemarquableIds: Flow<Set<Long>>`.
  - **Lookup statique** : `SpeciesIndex.kt` charge `assets/species-index.json` une fois (entrée `(genre, espece) -> int`). `DatasetStats.kt` charge `assets/dataset-stats.json` (totaux pour les compteurs Arboretum). Les deux sont singletons dans `ArbresApp`.
- `ui/` — Compose par écran. `ArbresNavHost` (`Routes.MAP`, `Routes.ARBORETUM`).
  - `ui/map/` — `MapScreen` (la carte + le hub : FAB GPS, FAB ★ remarquable proche, FAB Arboretum, sheet détail), `MapViewModel` (caméra + `openedArbre` + `pendingCapture` via `SavedStateHandle`), `CaptureLauncher.kt` (pipeline FileProvider + caméra + GPS guard), `PendingCapture.kt` (data class persisté).
  - `ui/detail/ArbreDetailScreen.kt` — `ModalBottomSheet` qui split « Arbre inconnu » / fiche complète selon `isDiscovered`.
  - `ui/arboretum/ArboretumScreen.kt` — header `X/Y espèces`, cards par espèce (count Paris, photos, 1re capture), section remarquables individuelle, thumbnails via `BitmapFactory` + `LaunchedEffect`.
- `ui/theme/` — `ArbresTheme` (Material3, palette verte/brune).
- `util/LocationProvider` — wrapper autour de `LocationManager` natif (sans GMS).
- `tools/build_dataset.py` — génère `arbres-paris.db` (Room) **et** `arbres-paris.geojson` (MapLibre, propriétés `id`/`remarquable`/`sk`) **et** `species-index.json` **et** `dataset-stats.json` à partir du CSV OpenData. Filtre les rows sans `genre`/`espece`. Préserve les `speciesIndex` entre runs (lit le `species-index.json` existant) — sans ça, regénérer casserait les captures déjà stockées en Room (qui réfèrent l'espèce par int). Le schéma SQL doit rester rigoureusement aligné avec celui que Room produit pour `ArbreEntity`.

Conventions :
- **MapView intégrée via `AndroidView`** dans `MapScreen`. Toujours passer par `DisposableEffect` pour relayer les `onCreate/onStart/onResume/onPause/onStop/onDestroy` du `MapView` — sans ça, fuites mémoire ou crashes en navigation.
- **Source GeoJSON clusterisée** chargée en RAM puis poussée via `setGeoJson(jsonString)`. Ne pas utiliser le constructeur `GeoJsonSource(id, URI("asset://..."))` avec clustering : MapLibre tile l'asset par bloc et le clustering Supercluster casse cross-tile (apparence : grilles d'arbres à zoom haut, rien à dezoom). Voir `MapScreen.addArbresLayers`.
- **Hit-test à deux niveaux** : query d'abord la layer `arbres-clusters` (tap → `getClusterExpansionZoom` + zoom in), puis `arbres-points` (tap → fiche).
- **Coloration grise/verte des points** : expression `case(remarquable, match-id, match-sk)` reconstruite via `buildDiscoveryExpression` à chaque changement des Flows captures, appliquée par `setProperties(circleColor(...))` sur la layer `arbres-points`. Les ids `Long` sont cast en `Int` pour le `match` (les `idbase` parisiens tiennent dans 32 bits, évite un quirk historique du DSL Java MapLibre sur les longArrays). **Les clusters restent verts** au dezoom — limite assumée du MVP.
- **Capture flow** : `rememberCaptureController` dans `ui/map/CaptureLauncher.kt`. Au tap : permission CAMERA → GPS frais (< 30 s, < 30 m) → URI FileProvider sous `getExternalFilesDir(null)/captures/{uuid}.jpg` → `TakePicture()` → INSERT Room avec check `file.length() > 0`. L'état pendant (`PendingCapture`) est sauvegardé dans le `SavedStateHandle` du `MapViewModel` pour survivre à un process death pendant l'intent caméra.
- **Migration Room sur asset DB** : pour ajouter une table à l'asset DB (qui ship en v1), passer la DB en version 2 dans `@Database`, ajouter `addMigrations(MIGRATION_1_2)` dans `databaseBuilder`. Le `CREATE TABLE` de la migration **doit matcher pile-poil** ce que Room génère pour la nouvelle entity (sinon le schemaCheck rejette au runtime). Pour l'asset DB elle-même, n'altérer que des tables côté script Python — pas de migration sur les tables seedées.
- **Géoloc** : `LocationManager` natif uniquement, `play-services-location` retiré. Sur GrapheneOS sans GMS, `LocationEngineDefault` de MapLibre retombe sur `AndroidLocationEngineImpl` (basé sur `LocationManager`).
- **Caméra mémorisée** dans `MapViewModel.lastCamera` pour survivre au remount de `MapScreen` après visite de la fiche détail.
- **FileProvider** : authority `${applicationId}.fileprovider`, paths déclarés dans `res/xml/file_paths.xml` (uniquement `external-files-path` sous `captures/` — privé à l'app, effacé à la désinstallation).

## Décisions à connaître

- **Style de carte** : OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`) — gratuit, sans clé, OSM. Référence dans `res/values/strings.xml` → `map_style_url`. Dépendance externe gentille mais sans SLA ; à terme, prévoir un fallback (Versatiles, Protomaps self-host) si OpenFreeMap tombe.
- **MapLibre Android 11.11.0** — choisi pour le 16 KB page-size alignment côté `libmaplibre.so`. Warning résiduel possible sur `libandroidx.graphics.path.so` (tiré par Compose) : indépendant de MapLibre, à régler via bump du Compose BOM si encore visible.
- **Pas de Hilt / DI framework** au MVP. Singletons exposés via `ArbresApp` (`arbreRepository`, `captureRepository`, `speciesIndex`, `datasetStats`). Helpers Compose : `rememberArbreRepository()`, `rememberCaptureRepository()`, `rememberSpeciesIndex()`, `rememberDatasetStats()`.
- **Pas de feature flags, pas d'A/B**. C'est une app perso.
- **Pas de service externe** au runtime au MVP. La fiche-espèce de l'Arboretum (Phase 2.5) sera le 1er endroit où l'app appellera Wikipedia/Wikidata.

## Quand tu travailles ici

- Avant de toucher au build, regarde `gradle/libs.versions.toml` — c'est la source de vérité des versions.
- N'introduis pas de dépendance Google/Firebase/AdMob/Analytics. Le projet doit rester installable et utilisable sans Google Play Services.
- Si tu modifies `ArbreEntity`, regénère la base avec `python3 tools/build_dataset.py` (sinon Room rejette l'asset). Le schéma SQL du script doit matcher pile-poil ce que Room génère.
- Si tu ajoutes une nouvelle entity Room, **pas d'évolution du schéma asset** : ajouter une migration `MIGRATION_N_N+1` dans `ArbreDatabase` qui crée la table côté Room (cf. `MIGRATION_1_2` qui crée `capture`).
- **Ne supprime ni ne réindexe `species-index.json`** sans réfléchir : les rows `capture.speciesIndex` réfèrent ces ints. Le script de build préserve les indices existants — toujours commiter `species-index.json` avec la nouvelle version.
- Mets à jour `ROADMAP.md` quand tu termines une étape ou quand tu changes le périmètre d'une phase.
