# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Boussole — viseur `v1.4.0`

Cycle ouvert le 2026-05-14. Thème : **navigation + lisibilité de la progression**. Un correctif structurel (le critère « arrondissement complété » est aujourd'hui injouable), trois apports inspirés du PWA livré par un admirateur (recherche universelle, réorg FAB, bar chart Profile), une extension du système de badges (paliers Pokédex), et du polish + side-tasks. Aucune migration Room, mais régénération `arr-species.json` (champ `remarquables` + centroids par arr).

Items détaillés dans `BACKLOG.md` (section « Cycle Boussole »).

**S1 — Modèle « arrondissement complété » sur remarquables (structurel)** — Le dénominateur actuel = *toutes* les espèces de l'arr (incluant `sp.`) rend la complétion quasi-impossible. On bascule sur les *arbres remarquables* de l'arr. Conserver « Arrondissements visités » = n'importe quel capture. Touche `tools/build_dataset.py` (ajouter `remarquables` dans `arr-species.json`), `ArrSpeciesIndex.remarquablesOf(arr)`, `BadgeEvaluator.evaluateFamilierArr` (l.78-98), description badge « Familier du Xe » (`Badge.kt` l.169-182), barre `ProgressionCard` (`ProfileScreen.kt` l.494-500), `BadgeEvaluatorTest`. À faire en premier : les sprints S2/S5 dépendent du dataset régénéré.

**S2 — Réorganisation des FAB Map** — Layout validé :

```
┌──────────────────────┐
│ 🔍              ⊙  │  top
│                      │
│         (carte)      │
│                      │
│                  🌳  │  bot-end
│                  📖  │  pile [Remarq, Arbo, Profil]
│ ★          ●     👤  │
└──────────────────────┘
  ★=Chasse        ●=HuntPanel
```

`MapScreen.kt:717-794`. Conserver `bottomShiftForHunt` et `awaitingFirstFix` pulse (déplacés avec Localiser top-end). Vérifier non-chevauchement pile bottom-end / HuntPanel bottom-center. **🔍 Recherche** et **⊙ Localiser** rendus discrets (`containerColor = surfaceContainerHigh`, `contentColor = onSurfaceVariant`, taille 56 dp conservée — alignés sur la palette utilitaire de `HuntPanel`). Le FAB 🔍 est posé en stub (snackbar « Recherche : bientôt »), le wiring de la sheet arrive en S3.

**S3 — Recherche universelle** — FAB loupe top-start (déjà posé en S2) → bottom-sheet 3 sections, **périmètre minimal** : espèces capturées (FR/latin), genres découverts, arrondissements (parseur `"1"`/`"01"`/`"1er"`/`"75001"`/`"premier"`). Tap espèce → `Routes.species(sk)` ; tap genre → `Routes.genre(...)` ; tap arrondissement → fly-to centroid + close. **Hors périmètre v1.4** : adresse précise, OpenData id, sheet récap arr. Pré-calculer les centroids arr côté Python ; étendre `Routes.MAP` avec query param `flyToArr` (analogue à `pulseArbreId`). Précharger le sheet content (cf. memo `feedback_compose_sheet`). Nouveau composable `ui/map/UniversalSearchSheet.kt`.

**S4 — Bar chart Profile** — Card additionnelle **sous** `ProgressionCard` (ne pas remplacer les 7 barres). **Pencher** : 2 graphes séparés, hebdomadaires, fenêtre 12 semaines glissantes — graphe 1 = captures totales par semaine, graphe 2 = nouvelles découvertes d'espèces par semaine. Compose `Canvas` hand-rolled, zéro dépendance. Bucket par semaine ISO (`WeekFields.ISO`) depuis `CaptureDao.allCaptures()`. **À rediscuter au démarrage du sprint** : fenêtre exacte (8 / 12 / 16 sem), empilés ou tabs, condition de masquage si historique < 2 semaines, couleurs (or vs feuilleClaire).

**S5 — Badges Pokédex progression** — 6 badges statiques binaires : `pokedex_10`, `pokedex_20`, `pokedex_50`, `pokedex_100`, `pokedex_200`, `pokedex_500`. Critère : avoir capturé **toutes** les espèces ayant `pokedexNumber ∈ [1..N]`. Le `pokedexNumber` est déjà calculé côté `build_dataset.py` (ordre décroissant de count, stable entre regénérations) et exposé via `SpeciesEntry.pokedexNumber`. Ajouter à `BadgeCatalog.ALL` ; brancher `evaluatePokedexRange(...)` dans `BadgeEvaluator` (utiliser `effectivelyCapturedSpecies` pour la propagation `sp.`) ; 6 icônes dans `BadgeIcons.kt` (variantes médaille graduée). Pas de migration.

**S6 — Polish chapitres + tip erroné** — (a) Texte fixe « arrondissement » dans titres de chapitres Remarquables/Catalogue (1er-20ème, pas pour les 2 Bois) ; (b) compteur N/M à côté de ces titres (cohérence avec Arboretum par genre) ; (c) tip splash citant « 930 espèces » à corriger — recompter le périmètre exact, ajuster ou retirer.

**S7 — Side-tasks de clôture** — (a) Revérifier la pertinence d'un texte fallback pour fiches espèces sans Wiki post-Catalogue 1.1.0 : audit `assets/species-info.json`, si ≤ 5% des espèces réellement capturables manquent de texte → fermer `[refusé]`, sinon proposer un fallback ; (b) Screenshots README de 3 à 6, couvrir les nouveaux écrans v1.4 (Map avec FAB Recherche, sheet Recherche, Profile avec bar charts) ; (c) Dette detekt accumulée par S2/S3 : 6 issues à traiter (3× MapScreen suite à l'ajout de `onGenreClick` qui décale la signature des entries baseliné, 1× UniversalSearchSheet `LongMethod`, 2× SearchData `ReturnCount` + `LoopWithTooManyJumpStatements`). Choix à faire : refactorer pour rentrer dans les seuils, ou rebaseliner en assumant la dérive (DoD cycle = `baseline ≤ 9`, rebaseline brut amènerait à 12).

**Definition of done** : tests JVM verts (`BadgeEvaluatorTest` couvre nouveau critère arr + 6 paliers Pokédex), detekt baseline ≤ 9, build release signé local OK, smoke test device GrapheneOS (S1 à S6 visuels), backup/restore sanity check, entrée `CHANGELOG.md [1.4.0]` rédigée, tag `v1.4.0` poussé, rotation cycle Boussole → *Cycles livrés post-1.0*.

## Prochains cycles

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture, conditionnalité selon la date et le genre/l'espèce ?).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_4_5`, backup `schemaVersion = 3`. Badges variantes émergent naturellement. Items détaillés dans `BACKLOG.md`.

## Cycles livrés post-1.0

### Reproductibilité — `1.3.2` (2026-05-13)

Hotfix sorti dans les heures qui ont suivi `1.3.1`. La CI Release re-téléchargeait le CSV OpenData live à chaque tag (via `tools/build_dataset.py` invoqué dans `release.yml`), faisant diverger l'APK release du repo committé — `v1.3.1` téléchargée depuis GitHub affichait `217 264 / 784` quand un build local du même commit affichait `217 042 / 782` (snapshot CSV figé au 28 avril). Contrat repensé : « l'APK release = ce qui est committé au tag ». Les binaires `arbres-paris.db` (~31 Mo) et `arbres-paris.geojson` (~33 Mo) sont désormais committés, le workflow CI ne fait plus que `assembleRelease`. Refresh dataset en passant (217 264 arbres, 784 espèces identifiées, 934 catalogue, 204 genres, 183 remarquables) + fixup `Z. alatum → Z. armatum` + overrides éditoriaux « Poivrier du Timut » et genre « Faux-poivrier ». Indices `species-index.json` strictement préservés. Détails dans `CHANGELOG.md` `[1.3.2]`.

### Polissage — `1.3.1` (2026-05-13)

Cycle court de correctifs (retours utilisateurs depuis `v1.3.0`) + passe de nettoyage des `.md` et commentaires datés. Six sprints, aucune casse de schéma : alignement `RemarquablesScreen` ↔ `ArboretumScreen` + libellés HuntPanel + tips dédoublonnés ; easter egg radar persisté (triple-tap → masquage `???`) ; filtre canonique `SpeciesEntry.isActive` qui purge les 16 fiches zombies + capitalisation `nv` à la source (`Quercus canariensis = "Chêne zéen"`) ; refonte `CLAUDE.md` 169 → 149 lignes + retrait ~50 références chronologiques dans 14 fichiers ; hygiène pré-release (4 tests JVM réparés + 7 refactors detekt, baseline 16 → 9) ; unification dénominateur genres Profil `200 → 203`. Détails dans `CHANGELOG.md` `[1.3.1]`.

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
