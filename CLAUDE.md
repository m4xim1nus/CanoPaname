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

- `data/` — modèle `Arbre` + `ArbreRepository`. Le repo est aujourd'hui un stub mémoire (`SAMPLE`). Quand tu remplaces par Room, garde **la même API publique** (`arbresDansBbox(...)`, `arbreParId(...)`) pour ne pas casser l'UI.
- `ui/` — toute l'UI Compose, structurée par écran (`map/`, `detail/`, …) avec un `ArbresNavHost` central.
- `ui/theme/` — `ArbresTheme` (Material3, palette verte/brune).

Conventions :
- **MapView intégrée via `AndroidView`** dans `MapScreen`. Toujours passer par `DisposableEffect` pour relayer les `onCreate/onStart/onResume/onPause/onStop/onDestroy` du `MapView` — sans ça, fuites mémoire ou crashes en navigation.
- Une bbox carte → une requête repo. Ne pas charger les 210 k arbres en mémoire d'un coup. Utiliser un index spatial (R*Tree Room ou requêtes `WHERE lat BETWEEN ... AND lon BETWEEN ...` + index composite).
- L'écran `MapScreen` a aujourd'hui un stub `addOnMapClickListener` — à remplacer par un vrai hit-test sur les symboles MapLibre quand la couche d'arbres existera.
- Géoloc : `play-services-location` est inclus pour la commodité, mais sur GrapheneOS sans GMS, prévoir un fallback `LocationManager` natif. Bien tester avant de s'appuyer sur `FusedLocationProviderClient`.

## Décisions à connaître

- **Style de carte** : OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`) — gratuit, sans clé, OSM. Référence dans `res/values/strings.xml` → `map_style_url`. C'est de la dépendance externe gentille mais sans SLA ; à terme, prévoir un fallback (Versatiles, Protomaps self-host) si OpenFreeMap tombe.
- **Warning Android 16 KB page-size** sur GrapheneOS / Android 15+ avec MapLibre 11.5.2 : non bloquant (juste un dialog au lancement, l'app fonctionne). Sera corrigé en bumpant MapLibre vers une version qui livre des libs natives 16 KB-aligned. Voir ROADMAP Phase 1.
- **Pas de Hilt / DI framework** au MVP. Si l'arbre des dépendances grossit, introduire Hilt avant que ça pourrisse — pas avant.
- **Pas de feature flags, pas d'A/B**. C'est une app perso.

## État actuel (fin de Phase 0)

- L'app build et tourne sur GrapheneOS. Carte OpenFreeMap visible, pan/zoom fonctionnels.
- 3 arbres en dur dans `ArbreRepository.SAMPLE` (Platane Tournelle, Marronnier Luxembourg, Chêne Viviani).
- Tap sur la carte → ouvre **systématiquement** le premier arbre du SAMPLE. **C'est volontaire**, pas un bug : il n'y a pas encore de couche de symboles donc pas de hit-test possible. Voir `MapScreen.kt:addOnMapClickListener` et le commentaire associé.
- Warning au lancement « Android app compatibility / 16 KB-aligned » → cosmétique, à corriger en bumpant MapLibre. Listé dans ROADMAP Phase 1.

## Quand tu travailles ici

- Avant de toucher au build, regarde `gradle/libs.versions.toml` — c'est la source de vérité des versions.
- N'introduis pas de dépendance Google/Firebase/AdMob/Analytics. Le projet doit rester installable et utilisable sans Google Play Services.
- Si tu remplaces le stub `ArbreRepository`, mets à jour la même API publique et adapte `MapScreen`/`ArbreDetailScreen` en conséquence.
- Quand tu poses une couche d'arbres sur la carte (Phase 1), supprime le stub `addOnMapClickListener` actuel et remplace-le par un vrai `queryRenderedFeatures`.
- Mets à jour `ROADMAP.md` quand tu termines une étape ou quand tu changes le périmètre d'une phase.
