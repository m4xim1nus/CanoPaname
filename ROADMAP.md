# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Polissage

Cycle court de correctifs (retours utilisateurs accumulés depuis `v1.3.0`) + passe de nettoyage des `.md` et des commentaires datés du code (« S9 Lot B », « livré cycle Catalogue »…), pour que le code et la prose se relisent sans contexte interne. Aucune casse de schéma, aucun changement d'architecture. Cible release `1.3.1`.

Sept sprints :

- **S1 — Retours UX textes + alignement RemarquablesScreen** *(livré)* : libellé HuntPanel `"Arbre remarquable non capturé le plus proche"`, RemarquablesScreen aligné sur ArboretumScreen (enum `LISTE/CATALOGUE` → `CATALOGUE/HISTORIQUE`, ordre des segmented inversé, default Catalogue, composable interne `ListeView` → `HistoriqueView`), retrait des tips `app.season_tint` **et** `app.season_scope` (aucune saisonnalité live à date), dédoublonnage `dataset.rank_5` ↔ `dataset.iconic.acer_platanoides` via garde `iconic_sk` dans la boucle rank de `tools/build_dataset.py`.
- **S2 — Easter egg radar** *(livré)* : triple-tap sur `RadarGlyph` → toggle persisté (nouveau `RadarObscureStore`, DataStore Preferences) qui remplace titre + qualification du `HuntTargetText` par `???`. Caché, non communiqué, distance live conservée.
- **S3 — Bug zombies Arboretum + majuscule `nv` + certification compteur** *(livré)* : `SpeciesEntry.isActive` (`!unknownSpecies && pokedexNumber != null`) devient le filtre canonique Arboretum / fiche-espèce / fiche-genre — les 16 fiches `—` disparaissent. `SpeciesDetailScreen` bascule sur `DatasetStats.totalEspecesIdentifiees` (source unique). `tools/build_dataset.py` capitalise `nv` à la source via `_capitalize_nv` (préfixe `x ` botanique préservé) — `Quercus canariensis = "Chêne zéen"`. Rebuild complet : 930 entrées, 782 identifiées (1..782 sans trou), confirmé partout (README + tips déjà alignés).
- **S4 — Nettoyage `.md` + commentaires datés** *(livré)* : refonte ciblée de `CLAUDE.md` (Architecture + Conventions trop denses, anti-charte intro, 169 → 149 lignes, gain visuel ×2 sur les puces ramassées), nettoyage des références chronologiques (« S9 Lot B », « cycle Catalogue », « sprint 4bis »…) dans 14 fichiers Kotlin + `tools/build_dataset.py`. `CHANGELOG.md` intact. Baseline detekt régénérée (17 → 16 issues, une `LongMethod` disparue par effet de bord de la simplification des KDoc).
- **S5 — Hygiène pré-release** *(livré)* : (a) 4 tests JVM réparés (`BadgeEvaluatorTest` × 2 + `SpeciesIndexTest` × 2 — `pokedexNumber` ajouté aux fixtures `SpeciesEntry`, le filtre `isActive` issu de S3 redonne ses entries). (b) 4 quickfixes detekt A : extension `RemarquableInfo.isEmpty()` (`ComplexCondition` à 5 nullités → 1 appel), `object Routes` extrait dans `ui/Routes.kt` (`MatchingDeclarationName`), `MapViewModel.consumePending()` refactorée à 3 returns via val nullables + double `if` (`ReturnCount` 8 → 3), `ProfileScreen.ProgressionCard` ramené de 13 scalaires à 7 `ProgressionState(numerator, denominator)`. (c) refactor B + 3 sous-radar : `ArbreDetailContent` + jumeau privé `DiscoveredContent` migrés sur `ArbreDetailState` + `ArbreDetailActions` (sites d'appel `MapScreen.kt` + `RemarquableDetailScreen.kt` adaptés), `GenreDetailScreen` (6 callbacks) → `GenreActions` (fichier dédié), `SpeciesDetailScreen` (6 callbacks + `celebrate`) → `SpeciesActions` (fichier dédié, `celebrate` reste param direct). Baseline detekt régénérée : 16 → 9 issues figées. Suite JVM verte (`BadgeEvaluatorTest`, `SpeciesIndexTest`, `BackupImporter`), lint OK, suite Python 87/87. Aucun changement de comportement.
- **S6 — Unification dénominateur « genres »** : barre Profil *Genres découverts* passe de `X / 200` à `X / 203`, alignée sur le compteur Arboretum. `ProfileScreen` migre du couple `genres()` + `mapNotNull(genreOf)` vers le couple `allGenres()` + `genreHasAnyCapture` qu'utilise déjà `ArboretumScreen`. Effet de bord corrigé au passage : une capture `(Genista|Vitex|Ziziphus) sp.` fait désormais monter le compteur du Profil (sémantique « touché le genre »). `SpeciesIndex.genres()` conservée mais KDoc clarifiée : ne sert qu'aux chapitres alphabétiques du mode Catalogue, pas aux dénominateurs. Aucune migration.
- **S7 — Clôture `v1.3.1`** : bump version, tests + lint + detekt + suite Python verts, smoke test device GrapheneOS, APK release signé, entrée `CHANGELOG.md [1.3.1]`, tag git, rotation de cycle dans ce fichier.

Items absorbés / introduits listés dans `BACKLOG.md` sous *Cycle Polissage (en cours)*.

## Prochains cycles

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture, conditionnalité selon la date et le genre/l'espèce ?).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_4_5`, backup `schemaVersion = 3`. Badges variantes émergent naturellement. Items détaillés dans `BACKLOG.md`.

## Cycles livrés post-1.0

### Réveil — `1.3.0` (2026-05-12)

Cycle de polish sur les écrans de chargement et les animations Compose. Six sprints : refresh des valeurs des splash tips + outil HTML de revue (`tools/build_tips_preview.py` → `docs/tips/index.html`) ; fix du bug d'intro tips (le splash partait direct en aléatoire au 1er lancement — cause : `ArbresNavHost.startDestination` dérivé d'un `Flow` → reconstruction du graphe `NavHost` → `MapScreen` monté 3×) ; fix d'un cold-start bloquant ~30 s (`computeInitialCamera` faisait un `getCurrentLocation()` synchrone sur le chemin critique) + recadrage caméra auto au 1er fix GPS ; splash cold-start opaque jusqu'au rendu effectif des pins (`awaitArbresRendered`) + plancher de durée ; animations clés repassées en `withFrameNanos` via `ui/common/FrameClock.kt` (insensibles à l'échelle d'animation système = 0) ; `FilterSplash` réécrit au look du `ColdStartSplash` (`SplashScaffold` privé mutualisé, « Réveil des {nv pluriel} parisiens »). Banque de tips refondue (≈ 231, +15 catégorie `app`, 18 popculture supprimés, placeholder `{captureCount}` retiré). Aucune casse de schéma. Détails dans `CHANGELOG.md` `[1.3.0]`.

### Progression — `1.2.0` (2026-05-12)

Refonte de l'expression de la progression. Le FAB ★ devient un mode chasse persistant (`HuntPanel.kt` — radar `withFrameNanos`, cible remarquable dynamique, distance live 5 s). Profil et Badges séparés conceptuellement : progression chiffrée en jusqu'à 7 barres Material 3 sur le Profil (arbres déverrouillés / remarquables / espèces / genres découverts / genres complétés / arrondissements visités / arrondissements complétés) + ligne « X jours depuis ta première capture » ; badges désormais tous binaires (`BadgeState` aplati, fin des progressifs/paliers), catalogue = 10 statiques + 2 familles dynamiques dérivées du dataset — « Familier des … » (26 genres à ≥ 7 espèces identifiées) et « Familier du … » (22 = 20 arr. + 2 bois, dénominateurs précalculés dans `assets/arr-species.json`). Cycle « Endgame » dissous, sa maîtrise par arrondissement absorbée ici. Six sprints. Quickfix detekt en clôture (baseline enfin wirée). Détails dans `CHANGELOG.md` `[1.2.0]`.

### Catalogue — `1.1.0` (2026-05-11)

Refonte du catalogue d'espèces : nettoyage data amont (drop dur des `Non spécifié`, normalisation `sp.`, `SPECIES_FIXUPS`), cascade de noms vernaculaires français uniques avec assert d'unicité au build (Wikidata P1843 → Wikipedia frTitle filtré → redirects API → ~30 overrides éditoriaux → construction), 202 fiches genre dédiées avec stats Paris, Arboretum à 2 niveaux *Catalogue* / *Historique*, badge progressif *Mosaïque de chênes*. Dix sprints. Refresh OpenData absorbé en passant (217 042 arbres, 183 remarquables, 929 entrées catalogue dont 803 identifiées). Détails dans `CHANGELOG.md` `[1.1.0]`.

### Photos et progressivité — `1.0.2` (2026-05-10)

Profondeur et lisibilité après v1.0.1, zéro casse de schéma. Six sprints atomiques : re-capture + suppression de captures (CRUD complet sans table photo 1:N), refonte `PhotoLightbox` (bornes zoom/pan + swipe `HorizontalPager`), refonte badges en multi-paliers visibles (catalogue 13 → 8, 22 paliers, `BadgeState` sealed binaire/progressif), saut vers l'arbre exact sur la carte (`Routes.map(pulseArbreId)` fly-to zoom 20 + halo pulse 2 s), galerie photos cliquable dans le sheet de détail arbre. Détails dans `CHANGELOG.md` `[1.0.2]`.

### Vérité & Friction — `1.0.1` (2026-05-09)

Patch dette + UX, zéro casse de schéma. Retrait UI saisons (schéma `Season` conservé pour Variantes), 2 badges saisonniers retirés (catalogue 15 → 13). Cinq sprints atomiques : suppression UI saisons + badges, alignement README/PRIVACY/CHANGELOG, refonte copy in-app (`UnknownContent`, `TooFar`, `EmptyState`), refonte Profil (compteurs catalogue + arbres déverrouillés, row 3 derniers badges, fix bug date « aujourd'hui »), Map UX (FAB ★ étoile, pulse GPS post-permission, ring orange clusters remarquables, reprise GPS auto, haptiques sheet & capture). Détails dans `CHANGELOG.md` `[1.0.1]`.

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
