# CLAUDE.md

Ce fichier est consommé automatiquement par Claude Code à chaque session ouverte dans ce repo : invariants cross-fichiers, gotchas, contrats inter-modules, principes produit. **Ce qui n'a pas sa place ici** : inventaire file-par-file (`ls` + KDoc fait mieux), description d'algorithme déjà claire dans le code, énumération des helpers Compose / routes / repos (`grep` est plus fiable et toujours à jour). Le détail dense d'un pipeline précis vit en commentaire de tête du fichier concerné, pas ici. Un visiteur curieux passe par `README.md` et `ROADMAP.md`.

## Contexte produit

App **Android natif** type « Pokémon GO des arbres parisiens », à usage **personnel + family & friends**. Pas grand public : pas de classement, pas d'anti-cheat lourd, pas de backend multijoueur. Single-player avant tout. Voir `ROADMAP.md` pour le périmètre par phase et `README.md` pour le pitch.

Conséquences directes pour les choix techniques :
- Stockage local seulement (Room/SQLite). Pas de service cloud, pas d'auth.
- Une seule cible : Android natif.
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

Projet mono-module `:app`, package racine `app.arbre`. Squelette :

- `data/` — modèles Room (`Arbre`/`Capture` + DAOs + `ArbreDatabase` v2, asset DB pré-cuite copiée via `Room.createFromAsset(...)`), repositories statiques chargés depuis `assets/*.json` (`SpeciesIndex`, `DatasetStats`, `SpeciesInfo`, `GenreInfo`, `RemarquableInfo`, `ArrSpeciesIndex`, `SplashTips`), stores DataStore (`OnboardingStore`, `RadarObscureStore`), `SeasonStore` en mémoire, évaluateur de badges pur (`BadgeEvaluator`), helpers Compose (`RepositoryProvider`). Tous les singletons sont exposés via `ArbresApp`. Contrat à tenir : `parseArrKey` / `ArrKey.idSlug` (Kotlin) miroir exact de `arr_key_slug` (Python).
- `backup/` — export/import ZIP via Storage Access Framework. Fusion additive avec dédup `(arbreId, timestamp)` via `CaptureDao.captureExists(...)` ; refus dur si `schemaVersion > CURRENT_SCHEMA_VERSION`. Pas de transaction Room enveloppante (reprise garantie par la dédup). Photos absentes → compteur `photosMissing` remonté, pas de blocage.
- `ui/` — Compose par écran, single-Activity + `ArbresNavHost` (routes dans `Routes` object). Sous-packages par écran : `map/`, `arboretum/`, `species/`, `genre/`, `remarquables/`, `profile/`, `badges/`, `detail/`, `onboarding/`, `common/`.
- `ui/theme/` — `ArbresTheme` Material3, palette dans `Color.kt`, tokens custom exposés via `MaterialTheme.arbresColors` (extension publique de `LocalArbresColors`), Fraunces SemiBold chargée dans `Type.kt`.
- `util/LocationProvider` — wrapper `LocationManager` natif (sans Google Play Services).
- `tools/build_dataset.py` — génère tous les assets (`arbres-paris.db` Room, `arbres-paris.geojson` MapLibre, et les JSON lookup) depuis le CSV OpenData. **Préserve les `speciesIndex` entre runs** (les `capture.speciesIndex` y réfèrent par int — toujours commiter le `species-index.json` régénéré). Schéma SQL aligné pile-poil avec ce que Room génère pour `ArbreEntity`. Cache Wikipedia REST sous `tools/.wikipedia-cache/{sk}.json` pour des builds suivants instantanés.

Données : OpenData Paris [`les-arbres`](https://opendata.paris.fr/explore/dataset/les-arbres/) (217 264 arbres) + [`arbresremarquablesparis`](https://opendata.paris.fr/explore/dataset/arbresremarquablesparis/) (183 remarquables). Tout embarqué dans l'APK à build-time. Binaires `arbres-paris.db` (~31 Mo) et `arbres-paris.geojson` (~33 Mo) sont **committés** dans le repo (cycle *Reproductibilité*, v1.3.2) — la CI ne régénère plus le dataset à la volée. Toute mise à jour du dataset passe par un run local de `tools/build_dataset.py` + un commit explicite des assets régénérés.

Conventions :
- **MapView principale persistante via `MapHost`** (Activity-scopé, `remember` dans `ArbresNavHost` — jamais dans `ArbresApp`, leak Activity) : lifecycle GL relayé depuis l'Activity, rendu gelé (`onPause`) quand aucun `MapScreen` ne l'affiche (sinon render loop + pulse GPS saccadent les écrans au-dessus), pipeline d'init one-shot + observers de découverte dans `MapHost.scope` (survivent aux navigations ; une view en pause gèle aussi les callbacks `setStyle`). `MAP_FILTERED` garde une MapView jetable locale relayée par le mount (`AndroidView` + `DisposableEffect`). **Détail en tête de `ui/map/MapHost.kt` et `ui/map/MapScreen.kt`.**
- **Source GeoJSON clusterisée** : `setGeoJson(jsonString)` sur source en RAM. Jamais `GeoJsonSource(id, URI("asset://..."))` avec clustering — MapLibre tile l'asset, le clustering Supercluster casse cross-tile (grilles d'arbres à zoom haut, rien à dezoom).
- **Hit-test à deux niveaux** : query `arbres-clusters` (tap → `getClusterExpansionZoom` + zoom in), puis `arbres-points` (tap → fiche).
- **Coloration pins** : expression `case(remarquable, match-id, match-sk)` reconstruite via `buildDiscoveryExpression`, appliquée par `setProperties(circleColor(...))`. Les ids `Long` sont cast en `Int` pour le `match` (les `idbase` parisiens tiennent dans 32 bits — quirk DSL Java MapLibre sur les longArrays).
- **Coloration clusters** : pipeline 2-vagues (rawJson nu d'abord, puis `enrichGeoJsonWithDiscovery` debounced 1 s mémoïsé dans `ArbresApp`). Contrat de format : `sk` dernière clé de `properties` (ordre stable Python 3.7+), miroir build/Kotlin. **Détail complet en tête de `ui/map/MapLayers.kt`.**
- **Capture flow** : `rememberCaptureController` dans `ui/map/CaptureLauncher.kt`. Au tap : permission CAMERA → GPS frais (< 30 s, < 30 m) → URI FileProvider → `TakePicture()` → INSERT Room (check `file.length() > 0`). État pendant (`PendingCapture`) dans le `SavedStateHandle` du `MapViewModel` (survie process death pendant l'intent caméra).
- **Migration Room sur asset DB** : bump version dans `@Database` + `addMigrations(...)`. `CREATE TABLE` matche pile-poil ce que Room génère, sinon `schemaCheck` rejette au runtime (cf. `MIGRATION_1_2` qui crée `capture`). Pour l'asset DB elle-même, n'altérer que côté script Python.
- **Géoloc** : `LocationManager` natif uniquement (sans Google Play Services). Bridge MapLibre → `LocationProvider` via `attachMapLibreLocationBridge`, indispensable au 1er run post-grant (`LocationListener` natif peut rester muet ~10 s alors que MapLibre reçoit ses fix). `computeInitialCamera` non-bloquant sur le chemin critique. Pin Location en `RenderMode.COMPASS` : le cône de vision (`ic_location_cone` via `bearingDrawable`) est orienté par le CompassEngine interne MapLibre — capteur coupé avec `isLocationComponentEnabled = false` au dispose ; `layerAbove(CLUSTER_COUNT_LAYER_ID)` fixe le z-order du puck au-dessus des arbres indépendamment du timing d'activation. **Détail complet en tête de `ui/map/MapScreen.kt`.**
- **Caméra** : persiste dans la view du `MapHost` entre deux mounts ; recadrage GPS auto une seule fois par vie d'Activity (`MapHost.autoRecenterDone`) — la caméra de l'utilisateur est sacrée. Tout fix servant à cadrer (bootstrap, recadrage auto, FAB localisation) passe par `isRecentFix()` (15 min, `LocationProvider`) : un last-known de la veille ne cadre jamais la carte — on démarre sur Paris et on recentre au 1er fix frais.
- **Filtre rapide** (`MapHost.quickFilter`, boutons sheet « Toute l'espèce » / « Tout le genre ») : le runner holder-scoped de `launchDiscoveryObservers` re-pousse le subset sur la source persistante (`setGeoJson` — jamais `setFilter` de layer, le clustering Supercluster ne refiltre pas). Toujours filtrer le **rawJson** puis enrichir le subset : `filterGeoJsonBySpecies` matche `"sk":N}}` en suffixe, le JSON enrichi ne matche plus. Survit aux navigations, annulé par un saut pulse (`pendingPulseArbreId`). La caméra ne bouge ni au filtrage ni au défiltrage.
- **FileProvider** : authority `${applicationId}.fileprovider`, paths dans `res/xml/file_paths.xml` (uniquement `external-files-path/captures/` — privé à l'app, effacé à la désinstallation).
- **Onboarding + nav** : `ArbresNavHost.startDestination` est **une constante** (`Routes.map()`). `NavHost` mémoïse son graphe sur `(route, startDestination)` ; si `startDestination` était dérivée d'un `Flow`, le round-trip `null → false → true` reconstruirait le graphe et remonterait `MapScreen` plusieurs fois — une instance transiente jouerait la séquence intro tips et appellerait `markSplashIntroSeen()` avant l'instance stable, l'intro ne joue alors jamais. Redirection vers `WELCOME` via `LaunchedEffect(onboardingDone)`, splash overlay couvre la transition. `popUpTo(WELCOME) { inclusive = true }` au tap « Commencer » pour purger le backstack.
- **Splash tips** : `.first()` au mount sur `splashIntroSeen` (figé dans un `mutableStateOf`), jamais `collectAsState` — sinon le re-emit après `markSplashIntroSeen()` re-launch le `LaunchedEffect` rotation et casse la séquence intro. **Détail complet en tête de `ui/map/SplashTipsController.kt`.**
- **Saisonnalité** : `SeasonStore` en mémoire seulement (la saison vive est l'instance par défaut à chaque relancement). Les écrans de découverte (Map, Arboretum, Remarquables) lisent le flow ; les écrans globaux (Profil, Badges) l'ignorent. `Season.ordinal` est stable car persisté dans `CaptureEntity.season` — ne pas réordonner l'enum.
- **Backup additif** : pas de transaction Room enveloppante, idempotent via dédup `(arbreId, timestamp)`. Bumper `BackupModels.CURRENT_SCHEMA_VERSION` quand le format change, garder l'ancien lecteur en path versionné.
- **Badges binaires** : `BadgeState(def, unlockedAt)`, un critère franchi une fois fige le ts. Statiques dans `BadgeCatalog.ALL` (10 badges), familles dynamiques (`familier_genre_*` 26 genres ≥ `GENRE_FAMILIER_MIN_SPECIES`=7 espèces / `familier_arr_*` 20 arr. + 2 bois) assemblées par `BadgeCatalog.full(...)`. Pas de table Room, dérivé à la volée. Couverture arr propagée par `SpeciesIndex.effectivelyCapturedSpecies` (capturer un chêne couvre `Quercus sp.`). Contrat `arr_key_slug` (Python) ↔ `parseArrKey` / `ArrKey.idSlug` (Kotlin) à tenir aligné.
- **Routes** : déclarer dans `Routes` (helper de construction si paramétrée) + `composable(...)` dans `ArbresNavHost`. Paramètres typés via `navArgument(...)`. Le saut « voir cet arbre » (fly-to ~600 ms à zoom 20 + halo pulse 2 s, `ValueAnimator` JVM à retrait auto) ne passe **pas** par un param de route — il rejouerait à chaque retour sur l'entrée MAP : c'est l'intent one-shot `MapHost.pendingPulseArbreId`, posé avant un `navigate(Routes.map())` en `launchSingleTop` (jamais deux entrées MAP empilées), consommé par l'effet pulse de `MapScreen`. Câblé depuis fiche-remarquable, fiche-espèce et fiche-genre (via `PhotoLightbox`).
- **Tokens couleur** : pas de `Color(0xFF...)` hardcodé — `MaterialTheme.arbresColors` (or, écorce, feuilleClaire, feuilleSombre, remarquableOrange) ou Material3 scheme. Fraunces uniquement sur display/headline/titleLarge.

## Décisions à connaître

- **Style de carte** : OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`) — gratuit, sans clé, OSM. Référence dans `res/values/strings.xml` → `map_style_url`. Dépendance externe gentille mais sans SLA ; à terme, prévoir un fallback (Versatiles, Protomaps self-host) si OpenFreeMap tombe.
- **MapLibre Android 11.11.0** — choisi pour le 16 KB page-size alignment côté `libmaplibre.so`. Warning résiduel possible sur `libandroidx.graphics.path.so` (tiré par Compose) : indépendant de MapLibre, à régler via bump du Compose BOM si encore visible.
- **Pas de Hilt / DI framework** au MVP. Singletons construits dans `ArbresApp` et consommés via `data/RepositoryProvider.kt` (`rememberXxx()` côté Compose, extensions `Context.xxx()` côté VMs).
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
