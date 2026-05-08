# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours — Vérité & Friction

Patch copy + UX + dette saison. **Zéro nouvelle feature, zéro casse de schéma.** Cible : version `1.1.0`.

Décisions cycle :
- **Découpage 5 sprints / 5 commits autonomes**. Chacun compile et passe `./gradlew lint test`. Tag SemVer après sprint 5.
- **Bullets Welcome supprimés net** (les 4 `welcome_bullet_*` de `strings.xml` étaient déclarés mais jamais rendus depuis longtemps — décision user 2026-05-08 : couper plutôt que ressusciter).
- **2 badges saisonniers**, pas 3 (la ROADMAP/BACKLOG initiale parlait de 3, mais le code n'en a que deux : `RONDE_DES_SAISONS`, `ANNEE_COMPLETE`).
- **Snackbar 5 s** : `SnackbarDuration` Material 3 ne propose que `Short` (4 s) / `Long` (10 s) / `Indefinite`. Solution = helper `showSnackbarFor(host, msg, ms = 5000)` via `Indefinite` + `delay` + `dismiss()`.

### Sprint 1 — Suppression UI saisons + 2 badges saisonniers ✅ livré

Schéma DB intact (`Season.ordinal` dans `CaptureEntity.season`, enum `Season`, `SeasonStore`, `rememberSeasonStore` conservés pour le cycle Variantes). Composables `SeasonSelector` / `ArchiveBanner` / `SeasonAmbience` également laissés en place mais plus appelés.

Fichiers modifiés :
- `data/Badge.kt` : retiré `RONDE_DES_SAISONS`, `ANNEE_COMPLETE`, `BadgeCategory.SAISONS`. `BadgeCatalog.ALL.size` passe à **13**.
- `data/BadgeEvaluator.kt` : retirées les branches `seenSeasons` et `seenYearMonths` ; supprimés `yearMonthOf` et `hasTwelveConsecutiveMonths` ; imports `java.time.{Instant, YearMonth, ZoneId}` retirés.
- `ui/badges/BadgeIcons.kt` : retirés mappings `CalendarMonth` / `DateRange`.
- `ui/theme/Theme.kt` : `seasonalSurface` + `animateColorAsState` supprimés ; le scheme Material 3 est posé direct.
- `ui/theme/Color.kt` : tokens `Saison*` retirés.
- `ui/arboretum/ArboretumScreen.kt` : SeasonSelector/ArchiveBanner retirés, filtre `it.season == selectedSeason` retiré, `HeaderCard` simplifié, `ArboretumEmptyState` global.
- `ui/remarquables/RemarquablesScreen.kt` : idem ; `capturedRemarquableIds(season)` → `capturedRemarquableIds()`.
- `ui/profile/ProfileScreen.kt` : `ScopeSelector` + enum `ProfileScope` + Flows `capturedSpeciesSeason` / `capturedRemarquablesSeason` + `seasonSuffix` retirés. Stats global-only.
- `ui/map/MapScreen.kt` : `SeasonAmbience` retiré, Flows captures passent en versions sans paramètre saison (cold-start filtré inclus).
- `test/data/BadgeEvaluatorTest.kt` : retirés tests `yearMonthOf`, `hasTwelveConsecutiveMonths`, `ronde des saisons`, `annee complete`. `evaluate with no captures` : `15` → `13`.

Résultat : `assembleDebug + lint + test` PASS.

### Sprint 2 — Communication & docs ✅ livré

| Fichier | Édit |
|---|---|
| `README.md` L17 | « 907 espèces à découvrir » → « 907 espèces à découvrir, dont 528 avec fiches enrichies » ; retirer « saisonnalité » de la liste features. |
| `README.md` L23 | Supprimer la phrase « Différenciateur clé vs. Pokémon : la **saisonnalité réelle**… » (caduque). |
| `README.md` Section « Données et vie privée » | Ajouter « Tuiles cartographiques OpenStreetMap via OpenFreeMap, sans envoi de données personnelles. » |
| `PRIVACY.md` L18-20 | Vérifier formulation `tiles.openfreemap.org` et OpenStreetMap (déjà mentionnés, ajustement mineur si besoin). |
| `CHANGELOG.md` | Ne pas modifier `[1.0.0]` (figé Keep a Changelog). Ajouter section `[1.1.0]` ou `[Unreleased]` en tête : `Modifié` (precision « fiches remarquables enrichies accessibles après capture »), `Retiré` (UI saisonnalité, 2 badges saisonniers), `Corrigé` (bug date Profil, etc.) — détails à compléter à la rotation. |

Critère de done : diff lisible, aucune promesse cassée (« 907 espèces » seul → toujours suivi de « dont X enrichies »).

### Sprint 3 — Copy in-app ✅ livré

Fichiers modifiés :
- `app/src/main/res/values/strings.xml` : retirées `welcome_bullet_grey`/`proximity`/`species`/`remarquables` (jamais référencées en Kotlin).
- `ui/detail/ArbreDetailScreen.kt` : copy `UnknownContent` non-remarquable refondue (« Non capturé. … < 30 m. ») ; label `TooFar` enrichi (« Trop loin (X m / max 30 m). Rapproche-toi. ») ; case `CaptureAvailability.Archived` retiré du `when` (mort depuis sprint 1).
- `ui/map/CaptureLauncher.kt` : `CaptureAvailability.Archived` retiré de la sealed class.
- `ui/common/EmptyState.kt` : body passe de `bodyMedium` à `bodyLarge` (16 sp) — propage Profile, Arboretum, Badges, Remarquables.

Résultat : `assembleDebug + lint + test` PASS.

### Sprint 4 — Profil (compteurs, row badges, bug date, label progress) ✅ livré

Fichiers modifiés :
- `data/ArbreDao.kt` : ajout `compterArbresOrdinairesParEspece(genre, espece)` (filtre `remarquable = 0`).
- `data/ArbreRepository.kt` : ajout `nombreArbresDecouverts(capturedSk, capturedRemarquableIds, speciesIndex)` — boucle reverse-lookup + `compterArbresOrdinairesParEspece` ; cohérent avec la coloration carte (pas de double-comptage).
- `data/BadgeRepository.kt` (nouveau) : `fun badges(): Flow<List<BadgeState>>` qui combine `CaptureRepository.toutesLesCaptures()` + `ArbreRepository.arbresParIds(...)` + `SpeciesInfoRepository`. Source unique partagée Profile + Badges.
- `ArbresApp.kt` + `data/RepositoryProvider.kt` : exposition `badgeRepository` + `rememberBadgeRepository()` + extension Context.
- `ui/profile/ProfileScreen.kt` :
  - `daysSince(epochMillis)` recodée en `ChronoUnit.DAYS.between(LocalDate, LocalDate)` zone `Europe/Paris` (corrige le « aujourd'hui » bidon quand la capture posée hier soir est vue ce matin).
  - 2 nouvelles `StatLine` : « Espèces du Catalogue » (`X / 907 (Y %)`) + « Arbres déverrouillés » (`X / 213 042 (Y %)`, calculé en async via `LaunchedEffect`). Helper `formatProgress`.
  - `BadgeGrid(listOf(firstCaptureBadge))` → row des 3 derniers badges débloqués (`badgeRepo.badges()` → filter + sortByDesc(unlockedAt) + take(3)). Section masquée si vide. `firstCaptureBadge` supprimé.
  - `BackupActionCard` reçoit `progressLabel: String` ; texte « Export/Import en cours… » sous le `LinearProgressIndicator`. `LaunchedEffect(backupBusy)` au top-level : timeout d'affichage 60 s qui rebascule à `Idle` + snackbar warning, **sans** cancel de la coroutine SAF.
  - Imports : `java.time.{Instant, LocalDate, ZoneId}`, `java.time.temporal.ChronoUnit`, `kotlinx.coroutines.delay`, `LaunchedEffect`, `rememberArbreRepository`, `rememberBadgeRepository`, `rememberDatasetStats`, `rememberSpeciesIndex`. Retirés : `TimeUnit`, `BadgeCatalog`.
- `ui/badges/BadgesScreen.kt` : remplace le pipeline `produceState` + `BadgeEvaluator.evaluate(...)` local par `badgeRepo.badges().collectAsState(emptyList())`. Imports `Arbre`, `BadgeEvaluator`, `produceState`, `remember`, `rememberArbreRepository`, `rememberCaptureRepository`, `rememberSpeciesInfoRepository` retirés.

Résultat : `assembleDebug + lint + test` PASS.

### Sprint 5 — Map UX ✅ livré

Sémantique du ring orange tranchée user (2026-05-08) : ring orange = mémoriser les découvertes remarquables (≥1 remarquable capturé dans le cluster), pas radar de ce qui reste à trouver.

Fichiers modifiés / créés :
- `ui/common/Snackbars.kt` (nouveau) — helper `showSnackbarFor(host, msg, ms = 5000)` : `SnackbarDuration.Indefinite` + `delay` + `dismiss`, cancellable via `coroutineScope`. Réutilisé dans MapScreen et CaptureLauncher.
- `ui/map/MapScreen.kt` :
  - Import `Search` → `Star` ; FAB ★ utilise `Icons.Outlined.Star` (tint `remarquableOrange` conservé).
  - Snackbar distance remarquable passe à `showSnackbarFor(...)` (5 s).
  - State `awaitingFirstFix` + `LaunchedEffect(awaitingFirstFix)` qui montre « Localisation en cours… » et attend `LocationProvider.currentLocation.filterNotNull().first()` avec `withTimeoutOrNull(30_000)` ; warning « GPS indisponible — sors à découvert » si timeout.
  - FAB GPS : `rememberInfiniteTransition` + `animateFloat(1f → 1.12f, tween 800 ms, RepeatMode.Reverse)` + `Modifier.scale(pulseScale)` (draw-only, hitbox stable).
  - `LaunchedEffect(openedArbre.id)` dans le bloc `if (openedArbre != null)` : `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` au mount du sheet.
- `util/LocationProvider.kt` :
  - Champ `private var appContext: Context?` mémorisé dans `start(...)` pour permettre le rebind depuis le receiver.
  - `BroadcastReceiver` privé sur `PROVIDERS_CHANGED_ACTION` : rebind via `stop() + start(appContext)` quand GPS/NETWORK redevient enabled.
  - `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` dans `start(...)` ; unregister défensif (try/catch `IllegalArgumentException`) AVANT `removeUpdates(listener)` dans `stop()`.
- `ui/map/MapLayers.kt` :
  - `enrichGeoJsonWithDiscovery` : injection `discovered_remarquable: 0|1` (1 ssi `remarquable && id ∈ capturedRemarquables`) à côté de `discovered`, dans la même boucle.
  - `addArbresLayers` : `clusterProperty("discovered_remarquable_count", sum, get("discovered_remarquable"))` accumulé par Supercluster.
  - Layer `arbres-clusters` : `circleStrokeColor` et `circleStrokeWidth` passent en `switchCase(gt(discovered_remarquable_count, 0), orange/3dp, white/2dp)`. **Test device requis** : aucune autre expression `switchCase` sur stroke n'existe dans le projet (1re utilisation pour MapLibre Native 11.11.0). Fallback double-layer prêt si KO.
- `ui/map/CaptureLauncher.kt` :
  - `val haptic = LocalHapticFeedback.current` ajouté à `rememberCaptureController`.
  - Bloc `if (!success)` du callback `TakePicture` : `haptic.performHapticFeedback(TextHandleMove)` + `showSnackbarFor(snackbar, "Capture annulée", 2500)`.
  - `runCapture(...)` reçoit `captureHaptic: () -> Unit` en param. Appel `captureHaptic()` déplacé juste avant `launcher.launch(photoUri)` ; ancien appel post-INSERT retiré.

Résultat : `assembleDebug + lint + test` PASS. Smoke device en attente (cf. `VERIFICATIONS.md` pour la check-list).

### Sprint 6 — Clôture cycle (procédure CLAUDE.md)

1. Compresser ce bloc « Cycle en cours » en 3-5 lignes.
2. Pousser le résumé en tête de « Cycles livrés post-1.0 » avec date et `1.1.0` (MINOR : retrait de feature + UX).
3. `CHANGELOG.md` : entrée `[1.1.0]` détaillée.
4. Promouvoir « Photos » → « Cycle en cours » avec items détaillés depuis BACKLOG.
5. Marquer dans `BACKLOG.md` les 22 items absorbés comme livrés.
6. Bumper `versionCode` (10000 → 10100 ?) et `versionName` ("1.1.0") dans `app/build.gradle.kts`.
7. Tag `v1.1.0` (sous validation user).

## Prochains cycles

### Photos — *next*

Photos multiples par espèce et par arbre individuel, visibles dans le modal détail (espèce + remarquable). Suppression d'une photo possible tant qu'il en reste ≥ 1 sur l'espèce. Backup `schemaVersion = 2` rétro-compatible lecture v1.

Profondeur Arboretum associée :
- Tranches de fréquence (« +10 000 », « 2 000-10 000 », « 1 000-2 000 », « 100-1 000 », « < 100 ») avec sticky headers, miroir des arrondissements pour les remarquables.
- Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche d'espèce non capturée (symétrise le radar ★).
- Restructuration des badges progressifs en barres + paliers visibles (1, 10, 25, 50, 100, 250…), en place des 4 marches abruptes actuelles.

Bonus carte : depuis la fiche d'un remarquable, bouton « Voir sur la carte » qui recentre, zoome et pulse 2 s sur le pin.

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_3_4`, backup `schemaVersion = 3`. Badges variantes émergent naturellement.

### Endgame

Cycle de rétention long terme à programmer après stabilisation Variantes :
- Maîtrise par arrondissement (carte chromatique vert/jaune/gris, badge « Maître du Xe »).
- Quêtes hebdomadaires locales, opt-in, sans notification push.
- Pré-affichage de la fiche remarquable enrichie même non capturé, avec bandeau « Pas encore découvert ».
- Fallback Wikipedia pour les 379 espèces sans fiche (« Famille X. Y individus à Paris. »).

## Cycles livrés post-1.0

*Vide — premier cycle post-1.0 en cours.*

## Historique pré-1.0

Archive figée des phases qui ont mené à v1.0.0 (2026-05-05). Détails verbeux dans `CHANGELOG.md` `[1.0.0]` et l'historique git.

- **Phases 0-2 — Scaffold → MVP carte → Capture** : squelette Gradle/Kotlin/Compose, MapLibre + 213 042 arbres clusterisés, géoloc native sans GMS, capture photo + GPS + Room (`MIGRATION_1_2` table `capture`), Arboretum, découverte par espèce.
- **Phases 2.5-7 — Profondeur & gameplay** : fiche-espèce Wikipedia FR (528/907) + stats Paris, mini-carte filtrée, fiche enrichie remarquables, ProfileScreen, saisonnalité 4 buckets calendaires, revue graphique (icône platane, palette `ArbresColors`, Fraunces SemiBold, splash animé), 15 badges en 6 catégories (`BadgeEvaluator` pur), backup ZIP via SAF dédup idempotent, onboarding `WelcomeScreen` + `OnboardingStore`, texture sensorielle (`ArbresMotion`, haptiques, climax capture).
- **Phases 8-10.5 — Pré-release & polish v1.0** : hygiène (tests `BadgeEvaluator` + `BackupImporter`, detekt baseline, extraction `MapLayers.kt`), 1re session device GrapheneOS et fix de 9 bugs, rebranding *Arbres* → *CanoPaname* (surface utilisateur), splash 2-passes, refonte iconographie remarquables, `PhotoLightbox`, renommage Pokédex → Catalogue, banque ~240 splash tips, coloration progressive des clusters carte.
- **Phase 11 — Audit pré-public** : refonte `README.md` v1.0, `CHANGELOG.md` Keep-a-Changelog, `PRIVACY.md`, `SECURITY.md`, `.github/release-template.md`, légal & attributions (OFL Fraunces, ODbL OpenData, CC BY-SA Wikipédia, `AboutScreen`).
- **Phase 12 — Hot fixes post-tests live** : tint hero Welcome, retours-ligne splash tips, célébration nouvelle espèce, `filterGeoJsonBySpecies` skip remarquables non capturés, bouton « Fiche espèce » conditionné, recompress JPEG long-edge 1600 / quality 85.
- **Phase 13 — Hardening, identité & passage public** :
  - **13A** code & assets durcis (manifest privacy, strip EXIF, `BackupImporter` anti-zip-bomb, ProGuard strip Logs, `MIGRATION_2_3` photoPath basename, schema 2.json, ~250 commentaires nettoyés).
  - **13B** repo renommé `Arbres` → `CanoPaname`, email projet dédié `canopaname@pm.me`, `git filter-repo` 40 commits, `gitleaks` 0 finding, wrapper Gradle committé.
  - **13C** keystore prod hors-machine, GitHub Actions `build.yml` + `release.yml`, naming APK Obtainium, `RELEASE.md` checklist, bump `versionCode = 10000` / `versionName = "1.0.0"`, repo public, tag `v1.0.0` pushé, [release publiée](https://github.com/m4xim1nus/CanoPaname/releases/tag/v1.0.0).
