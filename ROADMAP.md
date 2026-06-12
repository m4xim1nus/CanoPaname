# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Netteté

Polish de la boucle carte + capture. Ouvert le 2026-06-11. L'item 1 est volontairement le plus gros : livré, testé et committé en premier, il sert de socle aux items 3 et 4.

1. ✅ **MapView persistante across navigation** *(livré le 2026-06-11, sous-étapes A/B/C, validé device)* — l'instance `MapView` (style + source 217 k features comprises) est hissée hors de `MapScreen` dans un `MapHost` scopé Activity (créé au niveau `ArbresNavHost`, lifecycle GL relayé depuis l'Activity via `LifecycleEventObserver`) ; `MapScreen` ne fait plus qu'attacher/détacher la view (`AndroidView`, contrainte « un seul parent » gérée par detach du parent précédent) et poser ses listeners d'interaction per-mount. Retour sur la carte = instantané, **zéro splash si les pins sont déjà rendus** ; splash standard sinon (présence pas prête). Coloration : pins re-appliqués sub-frame (`applyDiscoveryColor` = swap d'expression, coût indépendant du nombre d'arbres) ; enrichment clusters déplacé dans le scope du holder → continue de tourner pendant qu'on est sur un autre écran, rattrapage silencieux au pire (cohérent avec le 2-vagues du cold start). La carte filtrée (`MAP_FILTERED`) garde sa MapView jetable, comportement intact. Trois sous-étapes commitables : (A) hoist + splash conditionnel, (B) observers de contenu holder-scoped, (C) cleanup (`lastCamera`/`freshMount` obsolètes côté mode normal, conventions `CLAUDE.md`). Invariants à tester : onboarding + intro tips install frais, `pulseArbreId` (deux entrées MAP en crossfade), recadrage GPS auto une seule fois par vie d'Activity, bridge location détaché hors-carte, process death pendant l'intent caméra.
2. ✅ **Cône de vision sur le pin Location** *(livré le 2026-06-11, validé device le 2026-06-12)* — secteur orienté selon la boussole du téléphone (capteur d'orientation), façon Google Maps. Par ailleurs, passer le pin Location au dessus des arbres. Implémentation : `RenderMode.COMPASS` (CompassEngine interne MapLibre, zéro code capteur) + `bearingDrawable` custom (`ic_location_cone`, secteur annulaire ±30° en fondu radial bleu puck) ; z-order réglé par `LocationComponentOptions.layerAbove(CLUSTER_COUNT_LAYER_ID)` — déterministe quel que soit le timing d'activation. Capteur boussole coupé hors-carte (déjà couvert par le disable du composant au dispose).
3. ✅ **Filtres rapides depuis la sheet d'un arbre non remarquable** *(livré le 2026-06-12, validé device : filtre/défiltre + spinner nickel)* — boutons « Toute l'espèce » / « Tout le genre » dans la sheet d'un pin découvert non remarquable, défiltrage en un clic via banner ✕ (slot du FAB 🔍). Implémentation : état `MapHost.quickFilter` (persiste aux navigations, meurt avec l'Activity), le runner d'enrichment de `launchDiscoveryObservers` devient le pousseur de source unique (subset filtré ou corpus enrichi, `collectLatest`) — re-push `setGeoJson` sur la source persistante, jamais `setFilter` de layer (clusters fantômes) ; filtrage toujours sur le rawJson (contrat suffixe `"sk":N}}`) puis enrich du subset. Set genre = `SpeciesIndex.genreFilterSet` partagé avec la fiche genre. Caméra ease vers Paris overview au filtrage, intacte au défiltrage ; `pulseArbreId` défiltre d'office. Deux retours smoke absorbés : spinner mini-platane frame-clock (insensible à échelle d'animation 0) et fin de busy calée sur l'event `onSourceChanged` (bascule effective de la source, pas le retour de `setGeoJson`).
4. **Transition de capture sans flash carte** — couvrir la bascule validation photo → fiche espèce par un overlay pour ne jamais repasser visuellement par la carte.
5. _(inclusion à discuter en cours de cycle)_ Fold WelcomeScreen → intro depuis la carte (retour F&F : le WelcomeScreen n'est pas lu).

## Prochains cycles

### Herbier

Enrichissement des fiches espèces : saisonnalité (calendriers floraison/fructification), attributs, pictos, photos de référence — parsing des fiches-essences Ville de Paris (200 espèces) + cascade Wikidata→iNat (photos) / Wikidata→POWO→Wikipedia FR (attributs) pour le reste. Inclut le fallback texte des fiches sans Wikipedia et le résidu botanique post-Catalogue. Détails dans `PROSPECTION_ARBORETUM.md` et items `[→Herbier]` du BACKLOG. Dépendance assumée : le calendrier de floraison alimentera la suggestion « en fleur » de Variantes.

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture, conditionnalité selon la date et le genre/l'espèce ?).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_4_5`, backup `schemaVersion = 3`. Badges variantes émergent naturellement. Items détaillés dans `BACKLOG.md`.

## Cycles livrés post-1.0

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
