# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Progression

Refonte de l'expression de la progression dans l'app. Deux axes : (1) le FAB ★ devient un **mode chasse persistant** plutôt qu'un popup éphémère ; (2) **Profil et Badges** sont séparés conceptuellement — la progression chiffrée vit en **barres** sur le Profil, les badges ne sont plus que **binaires** et s'élargissent avec une famille **Maîtres** (genre, arrondissement, chêne). En corollaire, le cycle Endgame disparaît comme cycle nommé : sa pièce maîtresse (badge Maître du Xe) est absorbée ici, le reste retombe en `[creuser]` ou refusé (cf. `BACKLOG.md`).

Cinq sprints :

1. **S1 — Mode chasse Étoile**. Tap ★ ouvre un panneau bas persistant (distance live au remarquable non découvert le plus proche, libellé espèce/qualification, bouton ✕). Le FAB ★ devient un toggle. Distance recomposée via `combine(LocationProvider.currentLocation, huntTarget)` throttlé ~1 Hz. État volatil dans `MapViewModel.huntTarget` (pas de `SavedStateHandle`, exit auto à la sortie de l'écran). Anti-collision FAB GPS via creux à droite du panneau. Cas « tous découverts » : message dédié dans le panneau (plus de snackbar éphémère).

2. **S2 — Suppression des badges progressifs**. Démantèlement de `BadgeState.Progressive`, `TierDef`, `ProgressiveBadgeCard`, `unlockProgressive`. Retrait du catalogue : `MARCHEUR`, `BOTANISTE`, `CHASSEUR`, `MOSAIQUE_QUERCUS` (l'identité Quercus revient en S3 en binaire). `BadgeEvaluator.evaluate` ne fait plus que des `unlockBinaryOnce`. Pas de migration Room (badges dérivés). Le compteur « X / 22 paliers » de `BadgesScreen` devient « X / N badges » en S5.

3. **S3 — Famille « Maîtres » (nouveaux binaires)**. Trois familles, toutes générées dynamiquement à partir du catalogue d'espèces / de la DB :
   - **Maître du chêne** (statique) : toutes les espèces du genre Quercus capturées.
   - **Maître de genre X** (dynamique) : pour chaque genre avec ≥ N espèces (N fixé pendant le sprint en regardant la distribution réelle ; cible : Acer / Quercus / Prunus / Tilia / Platanus passent, genres triviaux à 1 espèce exclus).
   - **Maître d'arrondissement X** (dynamique, 22 badges : 20 arr. + 2 bois). Nouvelle query Room `ArbreDao.especesParArrondissement` (DISTINCT `genre, espece` groupé sur `ArrKey`). Résultat cacheable au démarrage.
   - `BadgeEvaluator` gagne deux helpers spécialisés (`unlockMaitreGenre`, `unlockMaitreArrondissement`) avec `unlockedAt` figé sur la capture déclenchante.

4. **S4 — Bloc Progression visuel sur le Profil**. Refonte de `StatsCard` (tableau plat) en **hero card temps** (grand chiffre Fraunces « X jours depuis ta 1re capture ») + **6 barres** Material 3 : arbres déverrouillés (`X / 213 042`), espèces (`X / 907`), genres découverts (`X / 203`), genres complets (`X / N_majeurs`), remarquables (`X / 183`), arrondissements visités (`X / 22`). Composable `ProgressBar` réutilisable. Le bloc « Derniers badges » (3 derniers binaires) et la section Sauvegarde restent en dessous, inchangés. Décision pendant le sprint : sort des « espèces indéterminées » (retiré ou en sous-texte de la barre Espèces).

5. **S5 — Refonte BadgesScreen pour la nouvelle population**. ~60-80 badges binaires à présenter (5 binaires conservés + 1 Maître chêne + N Maîtres de genre + 22 Maîtres d'arrondissement). Sections par `BadgeCategory` (`DECOUVERTE`, `BOTANIQUE` = Maîtres de genre, `GEOGRAPHIE` = Maîtres d'arrondissement, `REMARQUABLES`, `DEMESURE`). Grille 3 colonnes conservée, plus de `GridItemSpan(maxLineSpan)`. Carte Maître affiche un sous-titre dynamique « X / Y espèces dans le genre/arrondissement » pour donner du sens même non débloqué. En-tête « X / N débloqués » avec N = `BadgeCatalog.ALL.size` dynamique.

## Prochains cycles

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_4_5`, backup `schemaVersion = 3`. Badges variantes émergent naturellement. Items détaillés dans `BACKLOG.md`.

## Cycles livrés post-1.0

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
