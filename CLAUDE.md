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

- `data/` — `Arbre` (modèle), `ArbreEntity` + `ArbreDao` + `ArbreDatabase` (Room), `ArbreRepository`. La base est pré-cuite dans `assets/databases/arbres-paris.db` et copiée par `Room.createFromAsset(...)` au premier lancement. API publique : `arbresDansBbox(...)` (Flow), `arbreParId(...)` (suspend).
- `ui/` — toute l'UI Compose, structurée par écran (`map/`, `detail/`, …) avec un `ArbresNavHost` central. `MapViewModel` mémorise la dernière `CameraPosition`.
- `ui/theme/` — `ArbresTheme` (Material3, palette verte/brune).
- `util/LocationProvider` — wrapper autour de `LocationManager` natif (sans GMS).
- `tools/build_dataset.py` — génère `arbres-paris.db` (Room) **et** `arbres-paris.geojson` (MapLibre) à partir du CSV OpenData. Le schéma SQL doit rester rigoureusement aligné avec celui que Room produit pour `ArbreEntity` (sinon `createFromAsset` rejette au runtime). Régénérer après chaque changement d'entité.

Conventions :
- **MapView intégrée via `AndroidView`** dans `MapScreen`. Toujours passer par `DisposableEffect` pour relayer les `onCreate/onStart/onResume/onPause/onStop/onDestroy` du `MapView` — sans ça, fuites mémoire ou crashes en navigation.
- **Source GeoJSON clusterisée** chargée en RAM puis poussée via `setGeoJson(jsonString)`. Ne pas utiliser le constructeur `GeoJsonSource(id, URI("asset://..."))` avec clustering : MapLibre tile l'asset par bloc et le clustering Supercluster casse cross-tile (apparence : grilles d'arbres à zoom haut, rien à dezoom). Voir `MapScreen.addArbresLayers`.
- **Hit-test à deux niveaux** : query d'abord la layer `arbres-clusters` (tap → `getClusterExpansionZoom` + zoom in), puis `arbres-points` (tap → fiche).
- **Géoloc** : `LocationManager` natif uniquement, `play-services-location` retiré. Sur GrapheneOS sans GMS, `LocationEngineDefault` de MapLibre retombe sur `AndroidLocationEngineImpl` (basé sur `LocationManager`).
- **Caméra mémorisée** dans `MapViewModel.lastCamera` pour survivre au remount de `MapScreen` après visite de la fiche détail.

## Décisions à connaître

- **Style de carte** : OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`) — gratuit, sans clé, OSM. Référence dans `res/values/strings.xml` → `map_style_url`. Dépendance externe gentille mais sans SLA ; à terme, prévoir un fallback (Versatiles, Protomaps self-host) si OpenFreeMap tombe.
- **MapLibre Android 11.11.0** — choisi pour le 16 KB page-size alignment côté `libmaplibre.so`. Warning résiduel possible sur `libandroidx.graphics.path.so` (tiré par Compose) : indépendant de MapLibre, à régler via bump du Compose BOM si encore visible.
- **Pas de Hilt / DI framework** au MVP. Singletons exposés via `ArbresApp` (`arbreRepository`). Helper Compose : `rememberArbreRepository()`.
- **Pas de feature flags, pas d'A/B**. C'est une app perso.

## Quand tu travailles ici

- Avant de toucher au build, regarde `gradle/libs.versions.toml` — c'est la source de vérité des versions.
- N'introduis pas de dépendance Google/Firebase/AdMob/Analytics. Le projet doit rester installable et utilisable sans Google Play Services.
- Si tu modifies `ArbreEntity`, regénère la base avec `python3 tools/build_dataset.py` (sinon Room rejette l'asset). Le schéma SQL du script doit matcher pile-poil ce que Room génère.
- Mets à jour `ROADMAP.md` quand tu termines une étape ou quand tu changes le périmètre d'une phase.
