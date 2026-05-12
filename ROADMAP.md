# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Progression

Refonte de l'expression de la progression dans l'app. Deux axes : (1) le FAB ★ devient un **mode chasse persistant** plutôt qu'un popup éphémère ; (2) **Profil et Badges** sont séparés conceptuellement — la progression chiffrée vit en **barres** sur le Profil, les badges ne sont plus que **binaires** et s'élargissent avec deux familles dynamiques **« Familier »** (un genre avec ≥ 7 espèces, les 20 arrondissements + 2 bois). En corollaire, le cycle Endgame disparaît comme cycle nommé : sa pièce maîtresse (badge « Familier du Xe ») est absorbée ici, le reste retombe en `[creuser]` ou refusé (cf. `BACKLOG.md`).

Cinq sprints :

1. ✅ **S1 — Mode chasse Étoile** (livré, `HuntPanel.kt`). Tap ★ ouvre un panneau bas pleine largeur (~28 % de l'écran, fond plein-cadre qui masque le bas de la carte) : radar animé, nom + qualification glosée (« Paysager » → « Intérêt paysager ») du remarquable non découvert le plus proche, distance live rafraîchie toutes les 5 s **en phase** avec le balayage du radar (pulse — trait qui flashe + anneau ping — au moment du refresh), ✕ au même emplacement que le FAB ★ (entrer/sortir au même endroit). Cible **dynamique** recalculée à chaque tick. `huntActive` en `remember` côté `MapScreen` → **fermeture auto** à la sortie de l'écran. FAB GPS / SnackbarHost décalés au-dessus du panneau. Cas « tous découverts » : message dédié. Radar + pulse pilotés par `withFrameNanos` (insensibles à l'échelle d'animation système — un audit des autres animations Compose figées dans ce cas est noté au BACKLOG). Plus de snackbar éphémère.

2. ✅ **S2 — Suppression des badges progressifs + recadrage du catalogue binaire** (livré). Démantèlement de `BadgeState.Progressive`, `TierDef`, `BadgeTier`, `unlockProgressive`, `ProgressiveBadgeCard` & co. `BadgeState` aplati en `data class(def, unlockedAt)`. `BadgeEvaluator.evaluate` ne fait plus que des `unlockBinaryOnce` ; param `speciesIndex` retiré (sans consommateur), idem côté `BadgeRepository`. Pas de migration Room (badges dérivés). Catalogue revu à la relecture, en anticipant S3/S4 : sortis `MARCHEUR`/`BOTANISTE`/`CHASSEUR`/`MOSAIQUE_QUERCUS` (l'identité Quercus revient binaire en S3) **et** `TOURNEUR_DE_PARIS`/`TOUR_COMPLET` (redondants avec la barre arrondissements du Profil + les futurs « Maître du Xe »). Nouveaux binaires : `PREMIERE_CAPTURE` (lance la section Badges du Profil dès la 1re capture) ; symétriques démesure `BONSAI` (< 2 m) et `JEUNE_POUSSE` (< 10 cm de circ.), seuils calés sur la distribution dataset ; `ESPECE_RARE` éclaté en 5 badges de rareté exacte — `Unique` (1 ind.), `Couple` (2), `Trinité` (3), `Quatuor` (4), `Quintette` (5), via `BadgeCatalog.ESPECE_RARETE`. `BadgeCatalog.ALL` = 10 binaires. Compteur `BadgesScreen` câblé sur `ALL.size` (wording « X / N badges » final en S5).

3. ✅ **S3 — Famille « Familier » (nouveaux binaires)** (livré). Deux familles binaires générées dynamiquement à partir du dataset (« Maître » jugé trop dur/possessif → **« Familier »** ; libellés au nom vernaculaire pluriel, jamais le binôme latin) :
   - **Familier des chênes / des érables / …** : capturer **toutes les espèces identifiées** d'un genre. Un genre a un badge ssi il a ≥ `BadgeCatalog.GENRE_FAMILIER_MIN_SPECIES` (= 7) espèces identifiées (`SpeciesIndex.genreCount`) → **26 badges** (Quercus 54 … Troène 7 ; coupure nette, rien entre 6 et 7). « Maître du chêne » n'est **pas** un cas statique — Quercus est un genre comme les autres. Libellé via `GenreInfo.nomFr` + pluralisation Kotlin (« -eau → -eaux », sinon « +s »).
   - **Familier du 12e / du Bois de Vincennes / …** : capturer **toutes les espèces** recensées dans l'arrondissement (volontairement aspirationnel — 61 espèces dans le 2e → 587 au Bois de Vincennes). 22 badges (20 arr. + 2 bois). Dénominateurs précalculés au build dans `assets/arr-species.json` (slug ArrKey → liste de speciesIndex ; généré par `tools/build_dataset.py` via `arr_key_slug`, miroir de `parseArrKey`) — pas de query Room runtime. Loader `ArrSpeciesIndex`.
   - `BadgeEvaluator.evaluate(captures, arbresById, speciesInfo, speciesIndex, arrSpecies)` renvoie désormais `Map<String, Long>` (id → ts de déblocage) ; `BadgeRepository` assemble le catalogue complet (`BadgeCatalog.full`) et zippe. `unlockedAt` figé sur la capture déclenchante (balayage chrono unique). Couverture arr propagée via `effectivelyCapturedSpecies` (capturer un chêne couvre `Quercus sp.`).
   - Logos : placeholder S3 (`Forest` pour les genres, `Place` pour les arr.) ; finalisé en S5.

4. ✅ **S4 — Bloc Progression visuel sur le Profil** (livré). `StatsCard` (tableau plat) remplacée par : une ligne **« X jours depuis ta première capture »** en tête du Profil (Fraunces `headlineSmall`, masquée à 0 capture) + une carte **Progression** = jusqu'à **7 barres** Material 3 (`ProgressBar` privé : titre `titleSmall`, `X / Y · Z %`, `LinearProgressIndicator` déterminé pleine largeur arrondi) dans cet ordre : arbres déverrouillés (`/datasetStats.totalArbres`), remarquables capturés (`/totalRemarquables`), espèces capturées (`/totalEspecesIdentifiees`, sous-texte « + N indéterminées »), genres découverts (`/speciesIndex.genres().size`), genres complétés (compte des badges `familier_genre_*` débloqués), arrondissements visités (`/22`, via `arbresParIds` + `parseArrKey`), arrondissements complétés (badges `familier_arr_*`). **Une barre à `0 / N` est entièrement masquée** → la liste se densifie avec la progression. À 0 capture : ni ligne de jours ni carte Progression, seul l'`EmptyState` reste ; sections Badges / Sauvegarde toujours visibles. Ligne « Captures totales » supprimée.

5. ✅ **S5 — Logos et polish du Profil** (livré).
   - **Logos** : badges de genre = icône partagée `Icons.Outlined.Forest` (le placeholder S3 devient le choix final — « tout le genre »). Badges d'arrondissement = **chiffre romain** (I…XX, `ArrKey.romanNumeral()`) rendu en **texte dans le cercle** en Fraunces SemiBold ; les 2 bois = **« Boulogne »** / **« Vincennes »** en texte. `BadgeDef.visual()` (`BadgeVisual.Vector`/`.Label`, `ui/badges/BadgeIcons.kt`) + `arrKeyFromSlug` (inverse de `ArrKey.idSlug`). Le cercle `BadgeIconCircle` est extrait dans son propre fichier, partagé `BadgesScreen` ↔ rangée « Derniers badges » du Profil ; taille de police du texte adaptée à la longueur pour tenir dans 48 dp.
   - **Polish Profil** : titre de section « Infos » (style `titleLarge`, comme « Badges » / « Sauvegarde ») inséré entre « Voir tous les badges » et « Comment jouer » ; couvre « Comment jouer » + « À propos ».
   - Scope volontairement restreint aux 2 items ci-dessus : pas de refonte des sections de `BadgesScreen` par `BadgeCategory` (idée retirée de `CLAUDE.md`).

## Prochains cycles

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture, conditionnalité selon la date et le genre/l'espèce ?).

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
