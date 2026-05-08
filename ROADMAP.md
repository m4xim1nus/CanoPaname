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

### Sprint 4 — Profil (compteurs, row badges, bug date, label progress)

#### Bug date « aujourd'hui »

`ProfileScreen.kt:581` (après nettoyage sprint 1 : ligne probablement décalée — chercher `private fun daysSince`) :

```kotlin
// Bug : TimeUnit.toDays arrondit en buckets de 24h flottants (capture
// hier 22h vue ce matin 8h → delta < 24h → 0 jour → « aujourd'hui »).
private fun daysSince(epochMillis: Long): Long {
    val zone = ZoneId.of("Europe/Paris")
    val captureDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return ChronoUnit.DAYS.between(captureDate, today).coerceAtLeast(0L)
}
```

Imports : `java.time.{Instant, LocalDate, ZoneId}`, `java.time.temporal.ChronoUnit`. Retirer `java.util.concurrent.TimeUnit`.

#### Compteurs globaux dataset

Dans `StatsCard` (ligne ~390-440 après sprint 1) :
- Récupérer `DatasetStats` via `rememberDatasetStats()` (déjà exposé).
- Champs : `totalArbres = 213042`, `totalEspeces = 907`, `totalRemarquables = 183` (`data/SpeciesIndex.kt` lignes 67-83).
- Ajouter 2 `StatLine` : « Espèces du Pokédex » (`$nbSpecies / ${stats.totalEspeces} (${pct}%)`) et « Arbres déverrouillés » (`$arbresDecouverts / ${stats.totalArbres} (${pct}%)`).
- **« Arbres déverrouillés »** = somme des counts par espèce capturée + count remarquables capturés. Vérifier si `ArbreRepository.compterParEspece(genre, espece)` existe (cf. `ArboretumScreen.kt:211` : `arbreRepo::compterParEspece` est une `suspend fun`). Si pas de méthode batch dispo, ajouter `suspend fun nombreArbresDecouverts(capturedSk: Set<Int>, capturedRemarquableIds: Set<Long>): Int` qui agrège côté DAO (`SELECT COUNT(*) WHERE speciesIndex IN (...) OR id IN (...)`).

#### Row preview des derniers badges débloqués

Dans `ProfileScreen` (item après stats) :
- Remplacer `BadgeGrid(badges = listOf(firstCaptureBadge))` par une rangée des N derniers badges débloqués.
- **Source** : exposer un `Flow<List<BadgeState>>` partagé. Recommandation = ajouter `fun badges(): Flow<List<BadgeState>>` dans `CaptureRepository` ou un nouveau `BadgeRepository` qui combine `toutesLesCaptures()` + `arbreRepo.arbresParIds()` + `speciesInfoRepo`. Réutilisable par `BadgesScreen`.
- Dans `ProfileScreen` : `val badges by badgeRepo.badges.collectAsState(emptyList())` ; filtrer `unlocked && unlockedAt != null`, `sortedByDescending { it.unlockedAt }`, `take(3)` (cohérent avec `GridCells.Fixed(3)`).
- Réutiliser `BadgeGrid` existant. Si aucun badge débloqué : cacher la rangée ou placeholder.
- Le `firstCaptureBadge` n'est plus nécessaire en variable séparée.

#### Label + timeout progress bar

`ProfileScreen.kt` `BackupActionCard` (ligne ~360-374 actuel) :
- Ajouter un `Text("Export en cours…" / "Import en cours…")` sous `LinearProgressIndicator` selon `BackupBusy`.
- Timeout 60 s : `LaunchedEffect(backupBusy)` qui sur `busy != Idle` lance `delay(60_000)` puis bascule `backupBusy = Idle` + snackbar warning.
- **Décision** : timeout d'affichage uniquement, ne PAS cancel la coroutine d'export/import sous-jacente (cancel sur SAF en cours = corruption potentielle du fichier choisi).

Critère : 6 stats (4 + 2 compteurs %) + rangée 3 badges récents + 2 cards backup avec label. Capture posée hier soir, vue ce matin → « il y a 1 jour ».

### Sprint 5 — Map UX

#### FAB ★ : icône Search → Star

`ui/map/MapScreen.kt` ligne ~528 (avant sprint 1 — décaler après vérification) :
```kotlin
Icons.Outlined.Search → Icons.Outlined.Star
```
Tint `MaterialTheme.arbresColors.remarquableOrange` conservé.

#### Snackbar distance remarquable 3-4 s → 5 s

`ui/map/MapScreen.kt` ligne ~462-464 : `snackbar.showSnackbar("Plus proche remarquable non découvert : ${nearest.second.toInt()} m")` sans paramètre `duration` → default `Short` (4 s).

Approche : nouveau helper `ui/common/Snackbars.kt` :
```kotlin
suspend fun showSnackbarFor(host: SnackbarHostState, msg: String, ms: Long = 5000) {
    val job = CoroutineScope(currentCoroutineContext()).launch {
        host.showSnackbar(msg, duration = SnackbarDuration.Indefinite)
    }
    delay(ms)
    host.currentSnackbarData?.dismiss()
    job.cancel()
}
```
À utiliser dans MapScreen et partout où on veut 5 s pile.

#### FAB GPS pulse pendant gap permission → 1er fix

`ui/map/MapScreen.kt` lignes ~534-548 (FAB GPS) :
- State `awaitingFirstFix: Boolean`. True quand `permissionLauncher` callback reçoit `granted=true`. False dès que `LocationProvider.currentLocation.collectAsState()` passe non-null.
- Pulse via `Modifier.scale(...)` ou `Modifier.alpha(...)` animé via `rememberInfiniteTransition` quand `awaitingFirstFix == true`.
- Snackbar « Localisation en cours… » à l'entrée dans `awaitingFirstFix` ; dismiss au passage à `false`.

#### BroadcastReceiver `PROVIDERS_CHANGED_ACTION`

`util/LocationProvider.kt` :
- BroadcastReceiver privé écoutant `LocationManager.PROVIDERS_CHANGED_ACTION`. Sur réception : si `isProviderEnabled(GPS_PROVIDER)` ou `NETWORK_PROVIDER` redevient true → redémarrer le listener (`stop()` + `start()`).
- Enregistrement dans `start(ctx)` (ligne 68 actuel), désenregistrement dans `stop()` (ligne 105). `ctx.applicationContext` pour éviter fuite Activity.
- `RECEIVER_NOT_EXPORTED` (Android 13+).

#### Cluster ★ ring orange

`ui/map/MapLayers.kt` lignes 53-60 :
- Ajouter `clusterProperty` `has_remarquable_count`. Comme MapLibre n'auto-cast pas `boolean`, injecter `remarquable_int` (0/1) côté Kotlin dans `enrichGeoJsonWithDiscovery` (zero-coût, même boucle que `discovered`). Ne PAS modifier `tools/build_dataset.py` (invaliderait l'asset GeoJSON pré-cuit).
- Sur la layer `arbres-clusters` (lignes 83-105) : remplacer `circleStrokeColor("#FFFFFF")` et `circleStrokeWidth(2f)` par expressions `switchCase` qui passent à orange (`0xFFFB8C00`, ring 3 dp) si `has_remarquable_count > 0`.
- **Risque** : si MapLibre Native 11.11.0 ne supporte pas l'expression sur `circleStrokeColor`, fallback double-layer (ring orange filtré sur `has_remarquable_count > 0`, sous la principale). À tester device.
- Token `remarquableOrange` dispo : `Color.kt:16` `RemarquableOrange = Color(0xFFFB8C00)` ; `ArbresColors.kt:18` exposé via `MaterialTheme.arbresColors`.

#### Haptiques

`ui/map/MapScreen.kt` ligne ~603-631 (ouverture `ModalBottomSheet`) :
- Wrapper avec `LaunchedEffect(openedArbre.id)` qui appelle `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`.

`ui/map/CaptureLauncher.kt` ligne 148 (haptique capture déplacée) :
- Aujourd'hui : `captureHaptic()` post-INSERT.
- Cible : déplacer au début de `runCapture()` après les checks GPS/distance, avant `launcher.launch(photoUri)`.

`ui/map/CaptureLauncher.kt` lignes 112-118 (annulation caméra) :
- Aujourd'hui : `if (!success) { file.delete(); return@launch }` silencieux.
- Cible : ajouter `HapticFeedbackType.TextHandleMove` (équivalent Tick) + `snackbar.showSnackbar("Capture annulée")` (durée Short).

Critère : FAB ★ étoile, pulse FAB GPS post-permission, BroadcastReceiver, cluster ring orange, annulation caméra avec Tick + snackbar, haptique sheet ouverture, haptique capture au tap.

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
