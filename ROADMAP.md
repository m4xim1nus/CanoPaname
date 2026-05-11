# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Catalogue

Nettoyage et unification du catalogue d'espèces. **Objectif : un nom français unique pour chaque entrée du catalogue**, plus de doublons « Marronnier » / « Marronnier » ni d'« Aesculus hippocastanum » nu. Travail de fond amont côté `tools/build_dataset.py` pour survivre aux refresh OpenData sans intervention manuelle. Pas de gameplay adjacent dans ce cycle — on livre la fondation propre avant d'empiler.

Le doc d'analyse `docs/analyse-especes.md` (2026-05-08, complété par un balayage frais 2026-05-10) a posé les chiffres ; les décisions ci-dessous sont **figées** pour le cycle.

**Périmètre tranché**

- **Drop dur des `Non spécifié`** (811 arbres réels, 4 formes : `sp.`, `n. sp.`, vide, `americana` aberrant). Aucune valeur de jeu.
- **Tag `unknownSpecies` pour `sp.` / `n. sp.` / espece vide normalisée**. Entrée distincte par genre, **non comptée dans le compteur catalogue principal** mais visible et capturable. Une ligne dédiée « + N captures à espèce indéterminée » côté Profil leur rend justice. Pas de rattachement à l'espèce dominante du genre — refusé : c'est inventer de la donnée. Libellé final = nom français du genre (ex. « Chêne », « Tilleul ») via table `GENRE_FR`, fallback genre latin nu — pas de suffixe parenthésé en UI (S6).
- **Coquilles latines** via table `SPECIES_FIXUPS` côté script (`Olea europea` → `europaea`, etc.), appliquée avant indexation `sk` pour préserver les indices.
- **Extraction du nom vernaculaire FR** : cascade `VERNACULAR_OVERRIDES` (table manuelle ciblée top espèces Paris) → Wikidata P1843 → Wikipedia frTitle (avec filtre anti-binôme — voir audit S4bis) → Wikipedia redirects API (récupération des vrais noms français pour les articles titrés scientifiquement) → construction `{nc} ({Initiale_genre}. {epithète})` en ultime. Désambiguation auto des collisions ; **assert d'unicité au build** (raise si non-unique). Le verify S3 garde un raise sur les redondances type `Aria edulis (Aria edulis)`.
- **Format asset** : `species-index.json` gagne 3 champs : `nv` (nom vernaculaire unique), `n` (numéro Pokédex stable, identifiées seulement), `u: true` (flag `unknownSpecies`, présent uniquement quand vrai). `dataset-stats.json` gagne `totalEspecesIdentifiees` (~800).
- **Sanity checks au build** : raise si une espèce avec count > 100 perd sa page WP entre 2 builds, si un `sk` existant disparaît, si une entrée `Non spécifié` reçoit des arbres (count > 0 — zombies count=0 OK), si un `nv` final est non-unique, si un `nv` est redondant `{g} {e} ({g} {e})`. Warn pour fallback construit sur espèce > 1000 captures (candidat override).
- **UI Arboretum** : 3 modes de tri exposés en `SegmentedButton` — **Découverte** (Pokédex stable, défaut), **Fréquence** (count Paris décroissant), **Genres** (groupé par genre alphabétique avec sous-chapitres). Titre des cards = `nv`, sous-titre = binôme latin italique (si différent), numéro `#N` Pokédex stable affiché en mode Découverte. Cards `unknownSpecies` visuellement distinctes, **toujours en fin de catalogue, sans `#`**. Compteur principal `X / ~800`. Le mode Genres prépare le terrain au S7 (fiches genre).
- **Auto-débloquage des fiches `sp.`** : la fiche `(G, sp.)` se débloque dès qu'une capture quelconque touche le genre — directement (capture d'un `Tilia sp.`) ou indirectement (n'importe quel `Tilia tomentosa`, `T. cordata`, etc.). La galerie photos reste alimentée par les seules captures explicites de `sp.`.
- **Fiches genre dédiées (S8)** : écran `GenreDetailScreen` + route `Routes.GENRE`, accessibles depuis l'en-tête de chapitre du mode Genres et depuis la fiche `(G, sp.)`. Couvre les 198 genres avec espèces identifiées **et** les 3 genres only-unknown (`Genista`, `Vitex`, `Ziziphus`). Contenu : liste filtrée des espèces capturables du genre, count cumulé, photo représentative (1re capture). Badge « Mosaïque de Quercus » émerge naturellement (transféré depuis cycle Variantes).

**Sprints**

Numérotation : le S5 effectif correspond à l'ex-« sprint 4bis » dans l'historique git (commit `b0b4e2c`, livré séparément des autres sprints du périmètre `4`). S6 → S8 livrés, S9 (polish post-smoke) et S10 (clôture) restent.

1. **Pipeline amont** (`tools/build_dataset.py` seul) : drops, `SPECIES_FIXUPS`, normalisation `sp.`, tag `u`. Régénération assets. ✓ (livré 2026-05-10)
2. **Extraction nom vernaculaire v1** (script seul) : SPARQL P1843, frTitle fallback, construction, désambiguation, assert unicité. ✓ (livré 2026-05-10)
3. **Sanity checks & hardening** (script seul) : raises et warns au build, doc `tools/README.md`. ✓ (livré 2026-05-10)
4. **UI Compose v1** : `SpeciesIndex.kt`, Arboretum (`#N`, sections, sp. en fin), fiches espèce / arbre / Profil. Auto-débloquage genre-based. ✓ (livré 2026-05-10)
5. **Fiche `(G, sp.)` enrichie + carte filtrée par genre** (ex-sprint 4bis dans git) : sous la galerie photos `sp.`, mini-catalogue filtré sur le genre (mêmes cards Arboretum) ; extension de `MAP_FILTERED` à un set de `sk` (tous les `sk` du genre déjà capturés + le `sk` de l'entrée `(G, sp.)`). ✓ (livré 2026-05-10)
6. **Cascade `nv` améliorée + strip indéterminée + rapport HTML** : filtre frTitle != binôme, étape Wikipedia redirects API (cache `.wikipedia-aliases-cache/`), étape `summary_extraction` (parse incipit Wikipédia déjà cached), mapping `GENRE_FR` (121 entrées genre→FR), 30 `VERNACULAR_OVERRIDES` sourcés (agent Explore + 5 consensus), sub-sources `construct_nc_unique`/`construct_nc_disamb`/`construct_binom`, disamb zombies en suffixe court `(Genre)`. Verify enrichi (raise redondance `(g) e (g) e`). `tools/build_report.py` + rapport `docs/dataset/index.html` self-contained pour validation visuelle. Audit final : 30 binôme nu (vs 339, dont 27 typos OpenData irréductibles), 0 redondance, 32 construct_binom (vs 400). ✓ (livré 2026-05-11)
7. **UI Catalogue 3 modes** : `SegmentedButton` Découverte/Fréquence/Genres dans `ArboretumScreen.kt`, transformations à l'affichage (backend `catalogueOrder` inchangé, ordre Pokédex stable). Mode Genres avec en-têtes de chapitre `GridItemSpan(maxLineSpan)`, count cumulé par genre. Hooks data exposés côté `SpeciesIndex` (`entriesOfGenre`, `genreCount`, `capturedCountInGenre`). Tests JVM ajoutés. ✓ (livré 2026-05-11)
8. **Fiches genre dédiées + Mosaïque** : nouveau pipeline `genre-info.json` (~200 articles Wikipedia FR cachés disque), `GenreInfo`/`GenreInfoRepository`, écran `GenreDetailScreen`, route `Routes.GENRE(genre)` avec encodage URI, redirect deep-link `SPECIES(sk_unknown)` → `GENRE`. `GENRE_FR` complété 123 → 166 (43 noms FR ajoutés ; 36 exotiques rares en fallback latin). Couvre les 202 genres utiles (199 avec espèces identifiées + Genista/Vitex/Ziziphus, exclut « Non spécifié »). Arboretum : retrait des cards `(G, sp.)` des 3 modes, headers de chapitre cliquables. `BadgeEvaluator` : signature évoluée pour recevoir `SpeciesIndex` ; Botaniste exclut désormais les sp. (régression rétrocompat acceptée). Badge progressif `Mosaïque de chênes` (3/5/10, paliers Bosquet/Chênaie/Forêt, exclut `Quercus sp.`). 6 typos OpenData fixées (`SPECIES_FIXUPS` étendu : Styphnolobium japonicum, Eriobotrya japonica, Ligustrum vulgare, Ulmus parvifolia, Ulmus minor, Platanus x hispanica). Rapport HTML `docs/dataset/index.html` étendu : nouvelle section *Catalogue des genres* (202 entrées triables, contrôles cas limites genre). Tests JVM ajoutés (`SpeciesIndex.allGenres`, `GenreInfo`, `BadgeEvaluator` Mosaïque + sémantique sp.). ✓ (livré 2026-05-11, validé device GrapheneOS)
9. **Polish & cohérence post-smoke** (à faire) : 8 affinements remontés par smoke test S8, regroupés en 5 lots :
   - **Lot A — Refonte UX Arboretum** : 2 niveaux de `SegmentedButton`. Niveau 1 = *Catalogue* / *Historique* ; niveau 2 sous *Catalogue* = *Par fréquence* (= ex-Fréquence) / *Par genre* (= ex-Catalogue) ; *Historique* = ex-Découverte. HeaderCard : remplacer « + N espèces indéterminées » par « N / M genres découverts » (M = `speciesIndex.allGenres().size` = 202). Cohérence du `#N` Pokédex stable : utiliser `pokedexNumber` partout (le mode *Par genre* recalcule un `displayN` front à supprimer, pour qu'une même espèce porte le même `#N` dans *Par fréquence*, *Par genre* et la fiche espèce).
   - **Lot B — Catalogue par genre : noms FR + verrouillage non-découvert** : chapter headers du mode *Par genre* affichent `GENRE_FR[g]` (ex. « Sapin » au lieu de « Abies »), latin en sous-titre italique ; fallback latin si `GENRE_FR` absent. Genres dont 0 capture du genre (sp. ou identifiée) → chapter rendu en silhouette « ??? » non cliquable. La fiche genre n'est accessible qu'après la 1re capture du genre.
   - **Lot C — Carte filtrée par genre** : retirer les pins gris des sks non capturés. Ne montrer que les arbres des espèces capturées + arbres `(G, sp.)` du genre. Investiguer côté `filterGeoJsonBySpecies` + `MapScreen` pour identifier la source des pins gris remontée par le smoke. Mécanique de déblocage formalisée : fiche genre accessible dès qu'une capture du genre existe (sp. ou identifiée), cohérent avec Lot B.
   - **Lot D — Enrichissement fiche genre (parallélisme fiche espèce)** : étendre `compute_genre_info` pour calculer médiane hauteur, médiane circonférence, proportion du dataset Paris (%), top arr sur-représentés. Étendre `GenreStats` côté Kotlin (champs en option, rétrocompat asset legacy). `GenreDetailScreen` étoffé : nouvelle section *« À Paris »* alignée sur la fiche espèce ; densifier IdentityBlock (count + nb d'espèces). Coût asset : `genre-info.json` ~80 → ~120 Ko estimés.
   - **Lot E — Overrides éditoriaux** : étendre `VERNACULAR_OVERRIDES` : `(Aesculus, hippocastanum)` Marronnier d'Inde → Marronnier commun, `(Styphnolobium, japonicum)` Pagode japonaise → Sophora du Japon, `(Prunus, avium)` Cerisier doux → Merisier, `(Thuja, plicata)` Red cedar → Thuya géant. Régen `species-index.json`, vérifier verify (unicité préservée — surveiller la collision potentielle « Sophora du Japon » avec autres entrées Sophora).
10. **Clôture cycle** (à faire) : `SpeciesIndexTest` complet, `./gradlew test lint assembleDebug` vert, balade GrapheneOS pour smoke test (post-S9 : 2 niveaux Arboretum, fiche genre enrichie, carte filtrée propre), entrée `CHANGELOG [1.1.0]`, rotation cycle (compression en « Cycles livrés post-1.0 »), tag `v1.1.0`.

**Format de release** : `v1.1.0`. Increment additif (3 champs assets optionnels, 1 nouvelle route, 1 nouveau cache disk), pas de casse Room ni de backup. Minor bump SemVer justifié.

**Hors scope explicite, repoussé au BACKLOG** : refonte Variantes (décalée au cycle suivant intact), carte filtrée par nom commun, badges « Inspecteur », mini-quiz d'identification, tranches de fréquence Arboretum, LLM offline pour la cascade nv (refusé : philosophie auto-contenue). Le tag `unknownSpecies` posé par S1 + les fiches genre du S7 rendent ces items triviaux à intégrer plus tard.

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
