# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Herbier

Enrichissement des fiches espèces : saisonnalité (calendriers floraison/fructification), attributs, photos de référence. Pressenti avant Variantes — dépendance assumée : le calendrier de floraison alimentera la suggestion d'état « en fleur » de Variantes. Détails et chiffres dans `PROSPECTION_ARBORETUM.md`, items `[→Herbier]` du BACKLOG.

#### Item 1 — Upgrade Arboretum (le gros morceau, éclaté en 13 sprints)

Plan complet (contexte, archi, risques) : `/home/max/.claude/plans/on-attaque-le-cycle-temporal-abelson.md`. Sources chiffrées : `PROSPECTION_ARBORETUM.md`.

**Décisions actées (2026-06-14)** : périmètre *tout d'un coup* (200 fiches API+PDF + cascade fallback 584) ; *cascade photos complète* embarquée WebP (officielles Ville de Paris + Wikidata P18/iNat filtre CC0/CC-BY) ; *pas de silhouettes procédurales* (identité = vraie photo, sinon placeholder + pills) ; cellule Arboretum non capturée garde « ??? » (photo de réf. seulement sur la fiche détail, désormais consultable même non capturée) ; inégalité 200/584 assumée et visible, jamais inventée.

| # | Sprint | Type | Dép. |
|---|---|---|---|
| S1 | Champs API fiches-essences (200) → `species-info.json` | data | — |
| S2 | Étendre `SpeciesInfo` (Kotlin, champs nullable) + parsing + tests | binding | S1 |
| S3 | `AttributesBlock` (pills) sur la fiche espèce | UI | S2 |
| S4 | Parsing PDF : calendriers floraison/fructification + « À retenir » | data | S1 |
| S5 | `SeasonalityCalendar` + bloc « À retenir » | UI | S2,S4 |
| S6 | Parsing PDF : champs textuels restants (famille, hauteur, descriptions, encarts, services éco) | data | S4 |
| S7 | Affichage champs textuels (enrichir blocs) | UI | S2,S6 |
| ~~S8~~ | ~~Cascade attributs 584 (Wikidata→POWO→Wikipedia FR→EOL)~~ — **abandonné 2026-07-15** (sondes : rendement = famille seule ~98 %, reste indisponible ; ne justifie pas un sprint ; piste famille-seule réactivable au BACKLOG) | data | S2 |
| S9 | Photos officielles 200 (extraction PDF → WebP) | data | S4 |
| S10 | Cascade photos 584 (Wikidata P18→iNat, filtre licence) | data | S9 |
| S11 | `ReferencePhotoBlock` + `SpeciesPhotoRepository` + `CREDITS.md` + écran crédits | UI | S2,S9 |
| S12 | Fiche détail consultable non capturée (cellules « ??? » tappables) | UI | S3,S11 |
| S13 | Hygiène & clôture item 1 (detekt, tests, `CHANGELOG`, `CLAUDE.md`, screenshots, commit assets) | clôture | tous |

Ordre : slice verticale d'abord (S1→S3 prouvent le pipeline bout-en-bout), puis saisonnalité (S4→S5, le gros gain Variantes), puis le reste, puis photos, puis découverte, puis clôture. **S8 abandonné le 2026-07-15** (cascade attributs : sondes réseau → rendement réel = famille seule, tout le reste indisponible sur Wikidata ; ne justifie pas un sprint — cf. BACKLOG, piste famille-seule réactivable). S4/S10 restent les plus lourds (scindables si besoin). Rupture à acter : `build_dataset.py` cesse d'être stdlib-only (`pymupdf`/`Pillow` build-time → `tools/requirements.txt`) ; runtime app inchangé. Suivi sprint-par-sprint : `BACKLOG.md`.

#### Items 2 et 3 (après l'item 1)

2. **Texte fallback pour les fiches espèces sans Wikipedia** — audit : 254/784 espèces identifiées (32 %) sans `summary`, ne couvrant que 7 778/204 364 arbres (3,8 %), quasi exclusivement hybrides/cultivars/sous-espèces. Fallback gratuit via `genre-info.json.summary` (texte Wikipedia du genre, déjà cuit). À trancher : lecture pondérée par arbres (sous le seuil 5 %) vs par espèce (bien au-dessus) selon la friction utilisateur ressentie.
3. **Résidu botanique post-Catalogue** — 11 entrées avec `nv == binôme nu` et count ≤ 2 (`Ehretia macrophylla`, `Sophora flavescens`, `Betula occidentalis`, `Crataegus japonicum`…), botaniquement douteuses : réelles mais rares, ou saisies erronées. Recherche botanique pour trancher keep / rebinder.

_(« fold WelcomeScreen » reste en `[creuser]` dans le BACKLOG, section « Onboarding & premier lancement », à re-discuter avant arbitrage)_


## Prochains cycles

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture, conditionnalité selon la date et le genre/l'espèce ?).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_4_5`, backup `schemaVersion = 3`. Badges variantes émergent naturellement. Items détaillés dans `BACKLOG.md`.

## Cycles livrés post-1.0

### Netteté — `1.5.0` (2026-06-13)

Polish de la boucle carte + capture. Six items, aucune casse de schéma. Socle : la `MapView` (217 k features) hissée hors de `MapScreen` dans un `MapHost` Activity-scopé, rendu gelé hors-écran → retour carte instantané, zéro splash si pins rendus. Sur ce socle, trois apports d'interaction : cône de vision boussole sur le puck (`RenderMode.COMPASS`, au-dessus des arbres), filtres rapides « espèce / genre » depuis la sheet (re-push `setGeoJson` du subset), blip directionnel north-up sur le radar de chasse (rayon = distance, refresh théâtralisé). Deux passes de correction éliminent les derniers flashs : cadrage caméra (`isRecentFix`, pulse one-shot, filtre sans dézoom) et voile de transition capture → fiche espèce. Détails dans `CHANGELOG.md` `[1.5.0]`.

### Boussole — `1.4.0` (2026-05-16)

Cycle thématique **navigation + lisibilité de la progression**. Sept sprints + deux passes de polish device. Apports majeurs : Recherche universelle (FAB 🔍 + sheet 3 sections espèces/genres/arrondissements avec parseur ordinaux/zipcodes), histogrammes hebdomadaires Profil au long-press d'une barre `ProgressionCard` (pipeline pur Kotlin, fenêtre ISO bornée 16 sem), 6 badges Pokédex paliers 10/20/50/100/200/500 (libellés gradués), réorganisation FAB Map (🔍 top-start, ⊙ top-end, pile bot-end, ★ Chasse bot-start) en verre dépoli. Correctif structurel : « Familier d'arrondissement » bascule du dénominateur *espèces de l'arr* (injouable) vers les **ids d'arbres remarquables physiques** (aligné sur le compteur `N / M` du Catalogue) — itération en deux temps post-test device. Polish chapitres Remarquables/Catalogue (headerLabel `Xe arrondissement` + compteur `N / M`), splash tips réalignés `934 / 784`, screenshots README portés à 6. Clôture detekt : extract `LazyListScope.searchSection(...)`, refacto `SearchData.build()` en chaînes `filter().map()`, `parseArrQuery` 7 → 2 returns. Baseline reste à 9. Détails dans `CHANGELOG.md` `[1.4.0]`.

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
