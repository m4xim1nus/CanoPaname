# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Catalogue

Nettoyage et unification du catalogue d'espèces. **Objectif : un nom français unique pour chaque entrée du catalogue**, plus de doublons « Marronnier » / « Marronnier » ni d'« Aesculus hippocastanum » nu. Travail de fond amont côté `tools/build_dataset.py` pour survivre aux refresh OpenData sans intervention manuelle. Pas de gameplay adjacent dans ce cycle — on livre la fondation propre avant d'empiler.

Le doc d'analyse `docs/analyse-especes.md` (2026-05-08, complété par un balayage frais 2026-05-10) a posé les chiffres ; les décisions ci-dessous sont **figées** pour le cycle.

**Périmètre tranché**

- **Drop dur des `Non spécifié`** (811 arbres réels, 4 formes : `sp.`, `n. sp.`, vide, `americana` aberrant). Aucune valeur de jeu.
- **Tag `unknownSpecies` pour `sp.` / `n. sp.` / espece vide normalisée**. Libellé construit (« Tilleul (espèce indéterminée) »), entrée distincte par genre, **non comptée dans le compteur catalogue principal** mais visible et capturable. Une ligne dédiée « + N captures à espèce indéterminée » côté Profil leur rend justice. Pas de rattachement à l'espèce dominante du genre — refusé : c'est inventer de la donnée.
- **Coquilles latines** via table `SPECIES_FIXUPS` côté script (`Olea europea` → `europaea`, etc.), appliquée avant indexation `sk` pour préserver les indices.
- **Extraction du nom vernaculaire FR** : Wikidata `P1843` prioritaire (gratuit, `qid` déjà stocké en cache) → regex sur summary Wikipedia FR en fallback → construction `{nc} ({Initiale_genre}. {epithète})` en ultime, override manuel `VERNACULAR_OVERRIDES` toujours gagnant. Désambiguation auto des collisions ; **assert d'unicité au build** (raise si non-unique).
- **Format asset** : `species-index.json` gagne 3 champs : `nv` (nom vernaculaire unique), `n` (numéro Pokédex stable, identifiées seulement), `u: true` (flag `unknownSpecies`, présent uniquement quand vrai). `dataset-stats.json` gagne `totalEspecesIdentifiees` (~800).
- **Sanity checks au build** : raise si une espèce avec count > 100 perd sa page WP entre 2 builds, si un `sk` existant disparaît, si un genre `Non spécifié` réapparaît avec count > 50, si un `nv` final est non-unique. Warn pour fallback construit sur espèce > 1000 captures (candidat override).
- **UI Arboretum** : titre des cards = `nv`, sous-titre = binôme latin italique, numéro `#N` Pokédex stable. Cards `unknownSpecies` visuellement distinctes, **toujours en fin de catalogue, sans `#`**. Compteur principal `X / ~800`.
- **Auto-débloquage des fiches `sp.`** : la fiche `Tilia (espèce indéterminée)` se débloque dès qu'une capture quelconque touche le genre `Tilia` — directement (capture d'un `Tilia sp.`) ou indirectement (n'importe quel `Tilia tomentosa`, `T. cordata`, etc.). La galerie photos reste alimentée par les seules captures explicites de `sp.`.

**Sprints**

1. **Pipeline amont** (`tools/build_dataset.py` seul) : drops, `SPECIES_FIXUPS`, normalisation `sp.`, tag `u`. Régénération assets.
2. **Extraction nom vernaculaire** (script seul) : SPARQL étendu `P1843`, regex, fallback construit, désambiguation auto, assert unicité, écriture `nv` partout.
3. **Sanity checks & hardening** (script seul) : raises et warns au build, refactor mineur isolant la phase espèces, doc `tools/README.md`.
4. **UI Compose** : `SpeciesIndex.kt`, Arboretum (`#N`, sections, sp. en fin), fiches espèce / arbre / Profil, splash tips. Auto-débloquage genre-based pour les fiches `sp.`.
5. **Tests, smoke device, clôture** : `SpeciesIndexTest`, run `./gradlew test lint`, balade GrapheneOS, entrée `CHANGELOG [1.1.0]`, rotation cycle, tag `v1.1.0`.

**Format de release** : `v1.1.0`. Increment additif (3 champs assets optionnels), pas de casse Room ni de backup. Minor bump SemVer justifié.

**Hors scope explicite, repoussé au BACKLOG** : refonte Variantes (décalée au cycle suivant intact), carte filtrée par nom commun, badges « Inspecteur » / « Mosaïque de chênes », mini-quiz d'identification, tranches de fréquence Arboretum. Le tag `unknownSpecies` posé par ce cycle rend ces items triviaux à intégrer plus tard.

## Prochains cycles

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_4_5`, backup `schemaVersion = 3`. Badges variantes émergent naturellement. Items détaillés dans `BACKLOG.md`.

### Endgame

Cycle de rétention long terme à programmer après stabilisation Variantes :
- Maîtrise par arrondissement (carte chromatique vert/jaune/gris, badge « Maître du Xe »).
- Quêtes hebdomadaires locales, opt-in, sans notification push.
- Pré-affichage de la fiche remarquable enrichie même non capturé, avec bandeau « Pas encore découvert ».
- Fallback Wikipedia pour les 379 espèces sans fiche (« Famille X. Y individus à Paris. »).

## Cycles livrés post-1.0

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
