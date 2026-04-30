# Roadmap

App perso, pas de calendrier engageant. Phases ordonnées du plus pragmatique au plus ambitieux. Tout est négociable. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé.

## Phase 0 — Scaffold ✅

Squelette Gradle/Kotlin/Compose, NavHost, MapLibre, Room, GeoJSON, icône, build/install GrapheneOS via ADB et Android Studio.

## Phase 1 — MVP « voir les arbres autour de moi » ✅

217 855 arbres réels de Paris affichés et tappables. Style OpenFreeMap, MapLibre 11.11.0, géoloc native (`LocationManager`, sans GMS), Room avec index `(latitude, longitude)`, source GeoJSON clusterisée, hit-test à deux niveaux.

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

**Très utiles (à faire si bande passante) :**

- [ ] **detekt minimal + intégration Gradle** — Gradle plugin `io.gitlab.arturbosch.detekt` + `detekt.yml` souple (focus complexité, null-safety, fonctions trop longues, wildcard imports). Tâche `./gradlew detekt` ajoutée. Pose le baseline avant le repo public, empêche la régression future. 2-3 h.
- [ ] **Extraction `MapScreen.kt`** — 898 LOC dont un seul `@Composable` de ~570 LOC. Splitter en 5-6 sous-composables sur le modèle `ProfileScreen` (déjà bien factorisé en 8 helpers) : `MapHostView` (AndroidView + DisposableEffect lifecycle), `MapFabs` (GPS + ★ + Arboretum), `MapSheets` (sheet détail + filter splash), `MapPermissions` (location rationale flow). Le moment idéal c'est *avant* d'y rajouter les animations Phase 7 (saison, climax) et avant un éventuel refactor hub navigation (idée-vrac CPO #10). 4-6 h.

## Phase 9 — Préparation de la release v1.0.0

- [ ] **CI GitHub Actions** — workflow `.github/workflows/build.yml` qui exécute `./gradlew assembleDebug test detekt` sur push et PR (le test passera grâce à Phase 8, detekt aussi). Job de release optionnel sur tag `v*` qui produit l'APK signé via secrets GitHub (keystore + passwords stockés en `secrets.RELEASE_KEYSTORE_BASE64` etc.). 1-2 h de setup.
- [ ] À la veille de la `v1.0.0` : générer keystore release prod, pousser repo public sur GitHub, créer GitHub Release avec APK signé, exposer URL Obtainium. La machinerie côté `build.gradle.kts` est prête (Phase 6) — ne reste plus qu'à provisionner le keystore et le rendre public.
