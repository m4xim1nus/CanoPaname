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

## Commandes

Le wrapper Gradle n'est pas committé en binaire — bootstrap une fois :
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

- `data/` — modèle `Arbre` + `ArbreRepository`. Le repo est aujourd'hui un stub mémoire (`SAMPLE`). Quand tu remplaces par Room, garde **la même API publique** (`arbresDansBbox(...)`, `arbreParId(...)`) pour ne pas casser l'UI.
- `ui/` — toute l'UI Compose, structurée par écran (`map/`, `detail/`, …) avec un `ArbresNavHost` central.
- `ui/theme/` — `ArbresTheme` (Material3, palette verte/brune).

Conventions :
- **MapView intégrée via `AndroidView`** dans `MapScreen`. Toujours passer par `DisposableEffect` pour relayer les `onCreate/onStart/onResume/onPause/onStop/onDestroy` du `MapView` — sans ça, fuites mémoire ou crashes en navigation.
- Une bbox carte → une requête repo. Ne pas charger les 210 k arbres en mémoire d'un coup. Utiliser un index spatial (R*Tree Room ou requêtes `WHERE lat BETWEEN ... AND lon BETWEEN ...` + index composite).
- L'écran `MapScreen` a aujourd'hui un stub `addOnMapClickListener` — à remplacer par un vrai hit-test sur les symboles MapLibre quand la couche d'arbres existera.
- Géoloc : `play-services-location` est inclus pour la commodité, mais sur GrapheneOS sans GMS, prévoir un fallback `LocationManager` natif. Bien tester avant de s'appuyer sur `FusedLocationProviderClient`.

## Décisions à connaître

- **Style de carte** : pour l'instant `demotiles.maplibre.org` (faible qualité, suffisant pour debug). À remplacer par un style OSM correct (Protomaps, MapTiler key gratuite, ou tuiles vectorielles self-host) avant la phase « usage réel ». Référence dans `res/values/strings.xml` → `map_style_url`.
- **Pas de Hilt / DI framework** au MVP. Si l'arbre des dépendances grossit, introduire Hilt avant que ça pourrisse — pas avant.
- **Pas de feature flags, pas d'A/B**. C'est une app perso.

## Quand tu travailles ici

- Avant de toucher au build, regarde `gradle/libs.versions.toml` — c'est la source de vérité des versions.
- N'introduis pas de dépendance Google/Firebase/AdMob/Analytics. Le projet doit rester installable et utilisable sans Google Play Services.
- Si tu remplaces le stub `ArbreRepository`, mets à jour la même API publique et adapte `MapScreen`/`ArbreDetailScreen` en conséquence.
- Mets à jour `ROADMAP.md` quand tu termines une étape ou quand tu changes le périmètre d'une phase.
