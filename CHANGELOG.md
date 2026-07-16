# Changelog

Format basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions [SemVer](https://semver.org/lang/fr/).

## [1.6.0] — 2026-07-16

Cycle *Herbier* — thème **enrichissement des fiches espèces** : attributs structurés, calendriers de saisonnalité, photos de référence. L'item parapluie « Upgrade Arboretum » a été éclaté en 13 sprints, dont **deux non retenus** : S8 abandonné (les sondes réseau réelles montrent que la cascade d'attributs Wikidata ne rapporte quasi que la famille — hauteur 0 %, port/feuillage/croissance absents — gain insuffisant pour un sprint) et S12 refusé (renversement produit assumé : la fiche espèce n'est **pas** consultable tant que l'espèce n'est pas capturée — c'est le cœur du game design, la découverte se mérite en marchant). Aucune casse de schéma Room. Une rupture de build assumée : `tools/build_dataset.py` cesse d'être stdlib-only (`pymupdf` + `Pillow` deviennent des dépendances **build-time**, déclarées dans `tools/requirements.txt`) — le runtime de l'app reste, lui, 100 % offline.

### Ajouté

- **Attributs structurés des fiches-essences Ville de Paris** (200 espèces, S1-S3 + S6-S7) : extension de `fetch_essences()` / `_build_essences_index()` (API) puis parsing des PDF fiches-essences (`essence_pdf.py`) → clés `ess` dans `species-info.json` (port, feuillage, famille, hauteur, envergure, croissance, longévité, indigénat, origine…). Rendu device : bloc **« Caractéristiques »** (`AttributesBlock`, pills Material 3) sur `SpeciesDetailScreen`, 1er livrable qui valide le pipeline bout-en-bout.
- **Calendriers de floraison / fructification** (S4-S5) : bitfields 12 mois extraits des PDF (contrat **bit 0 = janvier**, miroir Python ↔ Kotlin), plus le bloc « À RETENIR » (atouts / limites). Rendu : `SeasonalityCalendar` (strip 12 mois ×2, mention « en floraison ce mois-ci ») et bloc **« À retenir »** sur la fiche. Couverture floraison 199/200, fructification 193/200 (7 vides véridiques : espèces stériles), atouts/limites 194/200. Alimentera l'état « en fleur » du cycle *Variantes*.
- **Champs textuels des fiches-essences** (S6-S7) : famille, hauteur/envergure chiffrées, croissance, longévité, descriptions d'identification, encarts éditoriaux, services écosystémiques. **Couverture textuelle 200/200** : les 6 fiches « ancien template » se sont révélées être des exports **rasterisés** du template récent (zéro couche texte) → 90 valeurs transcrites à la main (deux agents indépendants, diff réconcilié) dans `tools/essence-overrides.json`, fusion fill-only avant le merge PDF. UI : `EssenceParisBlock` (prose + lien PDF + attribution **ODbL**) et `ServicesEcoBlock` ; attribution des fiches-essences ajoutée dans About / NOTICE.
- **Photos de référence embarquées** (S9-S10) : extraction des photos officielles des PDF fiches-essences (`pymupdf.get_images()` → recollage de fragments + gardes géométriques → WebP via `Pillow`) puis cascade **Wikidata P18 → iNaturalist** en licence stricte **CC0 / PD / CC-BY** (rejet -SA/-NC/-ND), une photo par trou. Total : **499 espèces illustrées · 777 photos** — 433 Ville de Paris (ODbL), 184 Wikimedia Commons, 160 iNaturalist, **0 licence interdite**, attribution CC-BY tracée par image. Manifest `species-photos.json` (crédit / licence / source par image) ; nouveau module `tools/species_photos_cascade.py` (fonctions pures + réseau + cache multi-niveaux + ledger déterministe, builds successifs offline byte-identiques).
- **UI photos de référence** (S11) : hero **mosaïque** `SpeciesHero` (photo principale + détails Paris ≤ 3 en collage façon PDF, fallback hero texte pour les espèces sans photo), `ReferencePhotoLightbox` avec caption d'attribution (généralisation de `PhotoLightbox` en `ZoomablePage` param `key`/`loader`, gestes intacts), premier décodage d'asset image de l'app (`AssetImage`), écran **Crédits photos** (`PhotoCreditsScreen`, route `PHOTO_CREDITS`, entrée depuis `AboutScreen`), fichier `CREDITS.md` généré au build (crédits par photo groupés par source). La photo de référence devient aussi la **vignette des cellules Catalogue capturées** (Par fréquence, Par genre, mini-catalogue de fiche genre) via `CataloguePhotos` — garde anti-spoiler : une espèce non capturée reste une silhouette.
- **Fallback texte genre** (item 2) : les ~235 espèces identifiées sans page Wikipedia dédiée (hybrides / cultivars / sous-espèces) reçoivent en repli le résumé Wikipedia du **genre** (déjà cuit dans `genre-info.json`), sous un titre explicite « À propos du genre {NomFr} » — le lien et l'attribution CC BY-SA pointent alors l'article du genre, jamais présenté comme celui de l'espèce. Les 12 espèces dont le genre n'a pas non plus de résumé gardent le placeholder.

### Modifié

- **Refonte design de la fiche espèce** : nouvelle hiérarchie visuelle intégrant hero photo, bloc caractéristiques, calendrier de saisonnalité et encarts Ville de Paris (polish S6/S7).
- **Refresh dataset OpenData** (snapshot 2026-07-16) : **217 960 arbres** (vs 217 264, +696), **938 entrées catalogue** (vs 934, +4), **778 espèces identifiées** (vs 784, −6 — le résidu botanique de l'item 3 en replie 3 en `sp.`, le reste retombe du CSV amont), **183 remarquables** (stable, un doublon `idbase` amont désormais neutralisé). Indices `species-index.json` strictement préservés (compatible toute capture antérieure).
- **Célébration « nouvelle espèce » déplacée dans le voile de transition** capture → fiche : le climax vit désormais dans le voile `CaptureTransitionSplash` (jusqu'ici muet) sous forme de séquence frame-clock 2,2 s (kicker « Nouvelle espèce » → nom vernaculaire + haptique → binôme latin → filet or), skippable au tap dès que la fiche est prête. `CelebrationHero` et le param de route `celebrate` sont supprimés — la fiche espèce redevient **100 % normale** dès son ouverture.

### Corrigé

- **Résidu botanique** (item 3) : recherche POWO / GBIF sur 11 taxa `nv == binôme nu` à count ≤ 2. 5 corrections via `SPECIES_FIXUPS` (sk sources préservés en zombies, 0 réassignation → captures Room intactes) — 2 rebinds de synonymes (Phellodendron japonicum → amurense ; Zanthoxylum bungei → bungeanum, nouveau sk canonique « Poivrier du Sichuan ») et 3 collapses de noms botaniquement invalides en `Genre sp.` (Crataegus japonicum, Crataegus baccata, Carpinus carpinifolia). Les 6 taxa restants sont des noms POWO valides, rares mais réels, gardés tels quels.
- **Textes essences — reconstruction de prose** : ordinaux en exposant recollés (« 12 e » → « 12e », « 19 e siècle », « 1 er ») via regex de `_clean` bornée sur les frontières de mot (« 3 euros » épargné), les valeurs de `essence-overrides.json` passant aussi par `_clean` au merge ; fins de paragraphe détectées **géométriquement** (retrait de la dernière ligne d'un corps justifié) → 3 run-ons corrigés (Ginkgo, Chêne vert, Davidia), aucun point inséré sur une césure intra-phrase. Impact : 88 recollages d'ordinaux + 3 points, zéro faux positif.
- **Retombées du refresh OpenData** : passe `nv` sur les espèces live régressées (le CSV a perdu diacritiques et noms FR dans `libellefrancais`) — 4 overrides `VERNACULAR_OVERRIDES` (« Aubépine » restauré sur sk 471/636/702, « Pommier à fleurs (M. robusta) » sur sk 514), chaîne qualifiée complète pour rester cohérent avec la fratrie. Pagination de `_fetch_remarquables_page` rendue déterministe : tri stable `order_by=arbres_idbase` + dédup par `idbase` (un doublon amont `2002365` cessait aléatoirement de faire perdre / dupliquer des records entre deux pages).
- **Haptique manquante sur le ✕ du HuntPanel** (`TextHandleMove`, aligné sur le geste d'annulation de capture) — les autres gestes carte en avaient un, pas la sortie du mode chasse.
- **Replay de l'animation de célébration à la rotation d'écran** : effet de bord corrigé par la refonte ci-dessus (l'ancien `CelebrationHero` rejouait via le param de route à chaque recomposition).

### Privacy

- Inchangé : 100 % local, aucune télémétrie, aucun service tiers au runtime. Les nouvelles photos de référence sont cuites à **build-time** (fiches-essences PDF + Wikidata/iNaturalist) et embarquées dans l'APK ; aucun appel réseau n'est ajouté au runtime.

## [1.5.0] — 2026-06-13

Cycle *Netteté* — thème **polish de la boucle carte + capture**. Six items livrés et validés device, aucune casse de schéma. L'instance `MapView` (style + source 217 k features) est hissée hors de `MapScreen` pour rendre le retour carte instantané, et sert de socle aux trois apports d'interaction : cône de vision boussole sur le puck, filtres rapides depuis la sheet d'un arbre, blip directionnel sur le radar de chasse. Deux passes de correction du cadrage caméra et de la transition de capture éliminent les derniers flashs visuels de la boucle.

### Ajouté

- **Cône de vision boussole** sur le pin Location : secteur orienté par la boussole du téléphone, façon Google Maps (`RenderMode.COMPASS` — CompassEngine interne MapLibre, zéro code capteur — + `bearingDrawable` custom `ic_location_cone`, secteur annulaire ±30° en fondu radial bleu puck). Le puck passe au-dessus des arbres via `LocationComponentOptions.layerAbove(CLUSTER_COUNT_LAYER_ID)`, z-order déterministe quel que soit le timing d'activation. Capteur boussole coupé hors-carte.
- **Filtres rapides** « Toute l'espèce » / « Tout le genre » dans la sheet d'un pin découvert non remarquable, défiltrage en un clic via banner ✕ (slot du FAB 🔍). État `MapHost.quickFilter` (persiste aux navigations, meurt avec l'Activity) ; le runner d'enrichment de `launchDiscoveryObservers` devient le pousseur de source unique, re-push `setGeoJson` du subset filtré sur la source persistante (jamais `setFilter` de layer — clusters fantômes). Set genre partagé avec la fiche genre (`SpeciesIndex.genreFilterSet`). Spinner mini-platane frame-clock (insensible à l'échelle d'animation 0), fin de busy calée sur l'event `onSourceChanged`.
- **Blip directionnel north-up** sur le radar du mode Chasse : blip cible orienté 0° = nord (zéro capteur boussole, cohérent avec la carte et le cône de vision ; repère « N » au-dessus du cadran), **rayon = distance** (mapping log `distanceToRadiusFrac` : 25 m → centre, ~105 m → anneau 1, ~450 m → anneau 2, ≥ 2 km → bord). Sous 25 m, plus de direction (bearing GPS = bruit), pulse central à la place (hystérésis de sortie 35 m). Refresh théâtralisé : pulse de barre + saut du blip + chiffre de distance commités au passage de la barre sur le blip (révélation sonar). Test JVM `DistanceToRadiusFracTest`.

### Modifié

- **MapView persistante across navigation** : l'instance `MapView` est hissée hors de `MapScreen` dans un `MapHost` scopé Activity (créé au niveau `ArbresNavHost`, lifecycle GL relayé depuis l'Activity), rendu gelé (`onPause`) quand aucun `MapScreen` ne l'affiche. Retour sur la carte instantané, **zéro splash si les pins sont déjà rendus**. Coloration pins re-appliquée sub-frame (swap d'expression), enrichment clusters déplacé dans le scope du holder → continue de tourner hors-écran. La carte filtrée (`MAP_FILTERED`) garde sa MapView jetable. Nettoyage `lastCamera` / `freshMount` obsolètes.
- **Transition de capture sans flash carte** : la bascule validation photo → fiche espèce est couverte par un voile plein écran (`CaptureTransitionSplash`, habillage `SplashScaffold` muet — platane frame-clock). La carte n'est plus jamais visible entre les deux. Décision « 1re espèce » figée AVANT le launch caméra (`PendingCapture.willCelebrate`, SavedStateHandle — source unique du voile ET de la nav) ; voile rendu par `ArbresNavHost` au-dessus du NavHost, levé sur entrée SPECIES `RESUMED` + plancher 600 ms + timeout filet 6 s. Au passage : `CaptureCallbacks`, `runCapture` scindé en `prepareCapture(): Uri?`.

### Corrigé

- **Cadrage carte — trois effets de bord** : (1) relance le lendemain centrée sur la position de la veille — nouveau prédicat `Location.isRecentFix()` (15 min, âge négatif = périmé post-reboot) appliqué au seed GPS, au bootstrap caméra, au recadrage auto et au FAB localisation (qui attend désormais un fix frais au lieu de recentrer sur un last-known) ; (2) retour carte après un saut pulse → re-fly vers l'arbre — le query param `pulseArbreId` rejouait à chaque remount, remplacé par l'intent one-shot `MapHost.pendingPulseArbreId` + `navigate(Routes.map())` en `launchSingleTop` (plus jamais deux entrées MAP empilées) ; (3) le filtre rapide dézoomait sur Paris — supprimé, le cadrage ne bouge ni au filtrage ni au défiltrage. Nettoyage : `MapHost.lastCamera` et `LocationProvider.currentOrLastKnown` (code mort) supprimés.
- **Crash carte filtrée** corrigé en clôture de cycle, + polish device divers.
- Recadrage GPS auto grillé par l'instance MAP transiente pré-onboarding : la séquence de découverte ne consomme plus le `first()` du recadrage avant l'instance stable.

### Privacy

- Inchangé : 100 % local, aucune télémétrie, aucun service tiers au runtime.

## [1.4.0] — 2026-05-16

Cycle *Boussole* — thème **navigation + lisibilité de la progression**. Sept sprints + deux passes de polish post-test device. Un correctif structurel (le critère « arrondissement complété » était injouable), trois apports inspirés du PWA livré par un admirateur (recherche universelle, réorg FAB Map, histogrammes Profil sur long-press), une extension du système de badges (6 paliers Pokédex), un polish chapitres + tips, et une clôture detekt + screenshots README. Aucune migration Room, régénération `arr-species.json` (ajout `remarquable_ids` + centroids par arr).

### Ajouté

- **Recherche universelle** (S3) : FAB loupe top-start sur la carte → `ModalBottomSheet` 3 sections (espèces capturées, genres découverts, 22 arrondissements). Tap espèce → fiche-espèce ; tap genre → fiche-genre ; tap arrondissement → fly-to centroïde z13 700 ms via `MapViewModel.pendingArrFlyTo` (pas de query param, pas de remount `MapScreen`, `lastCamera` préservée). Recherche insensible casse + accents (NFD + strip diacritiques + lowercase pré-cuit dans `SearchData`). `parseArrQuery` accepte chiffres, ordinaux abrégés (`1er`/`2e`/`4ème`), ordinaux français littéraux (`premier`/`deuxième`… `vingtième`), codes postaux `750NN`, et mots-clés `vincennes`/`boulogne`.
- **Histogrammes Profil au long-press** (S4) : long-press sur une barre de `ProgressionCard` ouvre un `ModalBottomSheet` avec l'histogramme hebdomadaire des nouveautés liées à la métrique (7 barres graphables, fenêtre ISO adaptative bornée à 16 sem). Pipeline pur Kotlin (`ProgressionHistory.kt`, IsoWeek Europe/Paris, dispatch par metric ; les 6 métriques triviales bucketisent `min(ts) groupBy clé`, « Arbres déverrouillés » rejoue les captures via snapshots cumulatifs). Histogramme Canvas hand-rolled (`arbresColors.or`, semaine courante dimmer 0.5), tooltip-on-tap auto-dismiss 2,5 s.
- **6 badges Pokédex binaires** (S5) : `pokedex_{10,20,50,100,200,500}`, catégorie Découverte. Palier N débloqué dès que toutes les espèces actives ayant `pokedexNumber ∈ [1..N]` sont capturées hors remarquables (alignement Catalogue/Arboretum par fréquence). Libellés gradués *Promeneur curieux → Apprenti botaniste → Botaniste → Herboriste → Naturaliste → Dendrologue*. Icône commune AutoMirrored `MenuBook` + seuil chiffré rendu sous l'icône via nouveau `BadgeVisual.VectorWithBadge`.
- **Réorganisation FAB Map** (S2) : layout final = 🔍 Recherche top-start, ⊙ Localiser top-end, pile [Remarquables, Arboretum, Profil] bottom-end, ★ Chasse bottom-start. Recherche + Localiser passent en palette **verre dépoli** (`White.copy(alpha = 0.78)` + icône asphalte `Color(0xFF3C4043)`, palette figée hors thème car ces FAB survolent toujours la carte claire OpenFreeMap), élévations strictement à 0 sur tous les états pour neutraliser le tonal overlay Material 3.
- **Polish chapitres Remarquables / Catalogue** (S6) : `ArrKey.headerLabel()` rend `"1er arrondissement"` / `"5e arrondissement"` sur les sticky headers (Bois et "Hors Paris" inchangés, `label()` court préservé). `ArrondissementHeader` passe en `Row` avec compteur `N / M` aligné droite (`bodyMedium` / `onSurfaceVariant`), cohérent avec `GenreChapterHeader` de l'Arboretum.
- 6 nouveaux screenshots `docs/screenshots/0{1-6}-*.png` couvrent l'app dans son ensemble (onboarding, carte, arboretum, fiche-espèce, remarquables, profil). `README.md` mis à jour.
- Splash tip `app.progress_history` (statique + asset embarqué + `docs/tips/index.html` régénéré) pour la découvrabilité des histogrammes.

### Modifié

- **Critère « Familier d'arrondissement » / « Arrondissements complétés »** (S1 + Polish S1 affiné post-test device) : le dénominateur passe de **toutes les espèces de l'arr** (50-150 par arr, de facto injouable) à **chaque arbre remarquable physique** de l'arr. Itération en deux temps : (1) S1 a basculé sur les *espèces* d'arbres remarquables (`remarquables: [...sk...]`), (2) Polish post-test a basculé sur les **ids d'arbres** (`remarquable_ids: [...idbase...]`) pour aligner sur le compteur `N / M` du Catalogue Remarquables qui, lui, compte des arbres individuels — sur le 19e (12 arbres = 9 espèces uniques), 10 captures débloquaient le badge alors que 2 arbres restaient à voir. `ArrSpeciesIndex.remarquableArbreIdsOf(key): Set<Long>` ; `BadgeEvaluator.evaluateFamilierArr` compare directement des `Set<Long>` d'ids, sans propagation espèce/genre. 2 arr sans aucun arbre remarquable (2e, 6e) exclus du dénominateur « Complétés » (20 au lieu de 22). « Visités » garde son dénominateur 22. Aucune migration utilisateur : les badges injustement débloqués se referment au prochain mount du Profil.
- Splash tips compteurs alignés : `intro.species_count` 782 → 784, `app.catalogue` 930 / 782 → 934 / 784. Suppression de `player.species_progression` et `player.species_share` (placeholder `{speciesCount}` sans dénominateur stable, retrait plutôt que rafistolage). `tools/splash-tips-static.json` réaligné à la source pour qu'une régénération `build_dataset.py` ne ré-introduise pas la régression. 229 → 227 tips.
- `UniversalSearchSheet.kt` : helper `LazyListScope.searchSection(label, items, key, row)` factorise les 3 blocs `stickyHeader { … }; items { … }` (Espèces/Genres/Arrondissements). Fonction principale passe de 102 à ~76 lignes.
- `SearchData.kt` : `build()` remplace 3 boucles `for + buildList + continue` par des chaînes `filter().map().sortedXxx()`. `parseArrQuery` refactorée en chaîne `?:` (7 returns → 2), comportement strictement préservé.
- `app/detekt-baseline.xml` : 3 IDs `MapScreen` mis à jour pour intégrer `onGenreClick: (String) -> Unit = {}` (signature drift S2). Baseline reste à 9 exactement, DoD `≤ 9` respectée.

### Corrigé

- **Sheet Recherche universelle plein écran systématique** (Polish S3 post-test device) : le sheet adoptait la hauteur de son contenu (`OutlinedTextField` + `LazyColumn` bornée à 520 dp), soit ~75 % de l'écran sur un téléphone moderne — perçu comme une « ouverture à 3/4 » avec un re-cadrage quand l'IME montait/redescendait. Le `Column` conteneur passe en `fillMaxSize()`, la `LazyColumn` en `weight(1f)` (suppression du `heightIn(max = 520.dp)`) : le sheet est désormais toujours plein écran, IME ou pas. Compromis assumé sur l'IME visible au mount = 2 back-press pour fermer (comportement Android natif universel).
- **FAB Recherche / Localiser sur carte claire** (Polish S2 post-test device) : le rendu initial `surfaceContainerHigh` thème-aware donnait deux pastilles gris quasi-noires sur la carte claire OpenFreeMap en thème dark (palette résolue contre le thème app, pas contre la chromie de la carte). Bascule sur **verre dépoli** détaillée ci-dessus.

### Privacy

- Inchangé : 100 % local, aucune télémétrie, aucun service tiers au runtime.

## [1.3.2] — 2026-05-13

Hotfix de reproductibilité du build release, sorti dans les heures qui ont suivi `v1.3.1`. La CI Release ré-exécutait `tools/build_dataset.py` avant `assembleRelease`, ce qui re-téléchargeait le CSV OpenData Paris **live** à chaque tag — l'APK publié reflétait alors l'OpenData du jour, pas le contenu du repo committé. Symptôme : `v1.3.1` téléchargé depuis GitHub Releases affichait `217 264 arbres / 784 espèces` alors qu'un build Android Studio local du même commit affichait `217 042 / 782` (snapshot CSV figé au 28 avril). Cause racine : `download()` du script skip si le CSV existe localement (gitignored en CI ⇒ toujours re-téléchargé là-bas, jamais en local). Le contrat passe à « l'APK release = ce qui est committé au tag », plus aucune dépendance OpenData live au runtime du build. Refresh dataset embarqué en passant pour aligner sur la version vue par les users de la 1.3.1.

### Corrigé

- `.github/workflows/release.yml` ne ré-exécute plus `build_dataset.py` ni n'installe Python (steps `setup-python`, `Cache Wikipedia dataset cache`, `Build dataset assets` retirés). La CI consomme désormais exactement les assets committés au tag.

### Ajouté

- `app/src/main/assets/databases/arbres-paris.db` (~31 Mo) et `app/src/main/assets/arbres-paris.geojson` (~33 Mo) sont désormais **committés au repo** (retirés du `.gitignore`). Pré-condition pour que le build CI marche sans régénération dynamique. Toute future mise à jour du dataset OpenData = run local de `tools/build_dataset.py` + commit explicite des assets régénérés.
- `SPECIES_FIXUPS["Zanthoxylum alatum"] → "Zanthoxylum armatum"` (`tools/build_dataset.py`) : `Z. alatum` est l'ancien nom du *Poivrier du Timut*, Wikipedia FR ne porte que la forme `armatum` (Q6170892). Le fixup permet de récupérer la fiche Wikipedia + applique l'override nv « Poivrier du Timut ».
- `GENRE_FR["Schinus"] = "Faux-poivrier"` (`tools/build_dataset.py`) : genre apparu avec le refresh dataset, nom français francophone usuel.

### Modifié

- Refresh dataset OpenData (snapshot 28 avril → 13 mai 2026) : **217 264 arbres** (vs 217 042, +222), **784 espèces identifiées** (vs 782, +2 ; Pokédex `#784` Faux-poivrier odorant *Schinus molle*, `#567` Poivrier du Timut *Zanthoxylum armatum*), **934 entrées catalogue** (vs 930, +4 ; ajout des `sp.` génériques `Aria` et `Mespilus` côtoyant les 2 identifiées), **204 genres** (vs 203, +1 *Schinus*). 183 remarquables stable, indices `species-index.json` strictement préservés (compatible toute capture user antérieure). Compteurs Profil / Arboretum / splash mis à jour automatiquement par le pipeline.
- `README.md`, `CLAUDE.md` et trois commentaires Kotlin (`SpeciesIndex.kt`, `ProfileScreen.kt`) ré-alignés sur les nouveaux compteurs `217 264 / 784 / 204`. Commentaire pédagogique `tools/build_dataset.py` `≈ 782` → `≈ 784`. Historique CHANGELOG / ROADMAP figé, intact.
- Workflow CI release plus rapide (~1-2 min gagnées) : retrait des deux steps Python (setup + cache) et du step de régénération des assets.

### Privacy

- Inchangé : 100 % local, aucune télémétrie, aucun service tiers au runtime.

## [1.3.1] — 2026-05-13

Cycle court *Polissage* : retours utilisateurs accumulés depuis `v1.3.0` + passe de nettoyage des `.md` et des commentaires datés du code, pour que le code et la prose se relisent sans contexte interne. Six sprints, aucune casse de schéma, aucun changement d'architecture.

### Ajouté

- Easter egg radar (`ui/map/HuntPanel.kt`) : triple-tap sur le `RadarGlyph` toggle un mode persisté (nouveau `RadarObscureStore`, DataStore Preferences) qui remplace titre + qualification du `HuntTargetText` par « ??? ». Caché, non communiqué, distance live conservée.
- `SpeciesEntry.isActive` (`!unknownSpecies && pokedexNumber != null`) : filtre canonique pour Arboretum / fiche-espèce / fiche-genre (propagé à `SpeciesIndex.genreCount` / `capturedCountInGenre`, `ArboretumScreen`, `GenreDetailScreen`).
- Refactors quickfix detekt sans changement de comportement : extension `RemarquableInfo.isEmpty()` (`ComplexCondition` à 5 nullités → 1 appel), `object Routes` extrait dans `ui/Routes.kt` (`MatchingDeclarationName`), `ArbreDetailState` + `ArbreDetailActions` regroupant les 13 params de `ArbreDetailContent` + son jumeau privé `DiscoveredContent`, `GenreActions` (fichier dédié pour les 6 callbacks de `GenreDetailScreen`), `SpeciesActions` (fichier dédié pour les 6 callbacks de `SpeciesDetailScreen`, `celebrate` reste param direct), `ProgressionState(numerator, denominator)` (13 scalaires → 7 sur `ProfileScreen.ProgressionCard`).

### Modifié

- HuntPanel libellé : « Arbre remarquable **non capturé** le plus proche » (la cible filtre déjà les non-capturés, le texte rattrape la sémantique).
- `RemarquablesScreen` aligné sur `ArboretumScreen` : enum `LISTE/CATALOGUE` → `CATALOGUE/HISTORIQUE`, ordre des segmented inversé (Catalogue à gauche), default `CATALOGUE`, composable interne `ListeView` → `HistoriqueView`.
- Banque de splash tips : retrait `app.season_tint` + `app.season_scope` (aucune saisonnalité live à date), dédoublonnage `dataset.rank_5` ↔ `dataset.iconic.acer_platanoides` via garde `iconic_sk` dans la boucle rank de `tools/build_dataset.py`.
- `tools/build_dataset.py` capitalise `nv` à la source via `_capitalize_nv` (préfixe `x ` botanique préservé, ex. `x Chitalpa` intact) — `Quercus canariensis = "Chêne zéen"`, 0 nv à initiale minuscule hors préfixe.
- `SpeciesDetailScreen.catalogueTotal` migré sur `DatasetStats.totalEspecesIdentifiees` (source unique pour le compteur 782).
- `CLAUDE.md` 169 → 149 lignes : refonte ciblée des sections Architecture + Conventions avec anti-charte intro (squelette de packages au lieu d'inventaire file-par-file, 17 invariants 1-liner au lieu de 24 puces denses, détail dense renvoyé vers les commentaires de tête de fichier déjà en place).
- ~50 références chronologiques retirées (« S9 Lot B », « cycle Catalogue », « sprint 4bis »…) dans 14 fichiers Kotlin + `tools/build_dataset.py`. `CHANGELOG.md` intact.
- `MapViewModel.consumePending()` refactorée (8 returns → 3 via val nullables + double `if`). Baseline detekt 16 → 9 issues figées (effet de bord du nettoyage des KDoc denses + des refactors A/B).
- Profil : barre *Genres découverts* `X / 200` → `X / 203`, alignée sur le compteur Arboretum. `ProfileScreen` migré du couple `genres()` + `mapNotNull(genreOf)` vers le couple `allGenres()` + `genreHasAnyCapture` qu'utilise déjà `ArboretumScreen`. Effet de bord corrigé : une capture `(Genista|Vitex|Ziziphus) sp.` fait désormais monter le compteur (sémantique « touché le genre »). KDoc de `SpeciesIndex.genres()` / `allGenres()` clarifiée pour figer la sémantique.

### Corrigé

- Arboretum : 16 fiches `—` zombies (espèces identifiées sans `pokedexNumber`, count = 0 dans l'OpenData courant — `Eriolobus trilobata`, `Malus communis`, `Styphnolobium japonica`…) disparues entièrement via filtre canonique `SpeciesEntry.isActive`. Aucun user n'avait de capture en zombie ; le dénominateur backend `totalEspecesIdentifiees = 782` les excluait déjà.
- 4 tests JVM pré-existants réparés (`BadgeEvaluatorTest` × 2 + `SpeciesIndexTest` × 2) — fixtures `SpeciesEntry` pourvues d'un `pokedexNumber` pour passer le nouveau filtre `isActive`. Suite JVM entièrement verte, suite Python 87/87.

### Privacy

- Inchangé : 100 % local, aucune télémétrie, aucun service tiers au runtime.

## [1.3.0] — 2026-05-12

Cycle de polish *Réveil* : écrans de chargement et animations Compose. Six sprints — refresh + outil de revue des splash tips, fix du bug d'intro tips, fix d'un cold-start bloquant ~30 s, splash cold-start qui reste opaque jusqu'au rendu effectif des pins, animations clés rendues insensibles à l'échelle d'animation système, `FilterSplash` réécrit au look du splash principal. Aucune casse de schéma.

### Ajouté

- Outillage : `tools/build_tips_preview.py` → `docs/tips/index.html` — revue de tous les splash tips (placeholders rendus + gabarit brut affichés, verdict `RAS` / `à tuer` / `chute à réécrire` + commentaire libre persistés en `localStorage`, filtre texte, export copiable). Doc dans `tools/README.md`.
- Outillage : `SplashTipsTest` dans `tools/test_build_dataset.py` (ids uniques, `intro` présent, placeholders ⊆ set runtime).
- `ui/common/FrameClock.kt` : `rememberFrameMillis()`, `rememberFrameProgress(durationMs, easing)` (rampe one-shot 0→1 figée à 1f), `rememberFramePingPong(periodMs, easing)` (triangle 0→1→0) — modelés sur la boucle `withFrameNanos` du radar de chasse, insensibles à `MotionDurationScale`.

### Modifié

- `ColdStartSplash` : le voile reste **pleinement opaque jusqu'au rendu effectif des pins** (`awaitArbresRendered` poll `queryRenderedFeatures` sous `withTimeoutOrNull`) et non plus jusqu'au retour de `setArbresGeoJson` (qui parse / cluster en background, pins 1-3 s plus tard) — fin du moment « carte vide ». Plancher de durée minimale `COLD_SPLASH_MIN_MS = 2500` en cold-start fresh (0 au remount avec GeoJSON enrichi en cache). `finally { arbresPrets = true }` pour ne jamais rester coincé sous le voile.
- `FilterSplash` réécrit au look du `ColdStartSplash` : `SplashScaffold` privé extrait dans `MapOverlays.kt` (fond `colorScheme.primary`, hero platane `ic_launcher_foreground` `scale` + `rotationZ=sway`, fondu d'entrée, couronne de 7 mini-platanes flottants — tout le pilotage `withFrameNanos` mutualisé) ; à la place de « Filtrage de X… » + spinner, « Réveil des **{nom vernaculaire pluriel}** parisiens » (nv dans un span 20 sp SemiBold) + `CircularProgressIndicator` discret, sans zone de tips. Pluriel via `pluralizeHead` (`-eau`/`-eu` → `-eaux`/`-eux`, invariant en s/x/z) ; `splashSpeciesLabel` résolu depuis `filteredEntry` (filtre genre → `GenreInfo.nomFr`, filtre espèce → `displayNomCommun`). `FILTER_SPLASH_MIN_MS = 1000`.
- Animations clés repassées en pilotage `withFrameNanos` (insensibles à l'échelle d'animation système = 0) : fade-in + sway du `ColdStartSplash`, couronne `MiniArbreCrown`/`MiniArbreItem` (sway / drift / cascade par platane → fonction pure `miniArbrePhase` lue dans le `graphicsLayer`, la couronne ne recompose plus par frame), hero du `WelcomeScreen` (respiration gris↔vert), célébration 1re capture (`CelebrationHero`). Laissés en l'état (snap acceptable) : sortie du voile, micro-shift / pulse des FAB, chiffre de distance de la chasse, rotation des tips.
- Splash tips : banque rafraîchie (≈ 231 tips). Chiffres dataset réalignés post-1.1 / 1.2 (782 espèces identifiées, 930 entrées catalogue, 217 042 arbres). +15 tips d'une nouvelle catégorie `app` (saisons calendaires, badges binaires + familles « Familier des/du … », backup ZIP, mode chasse radar, fiches espèce & genre, barres de progression du Profil, logos d'arrondissement, catalogue, map-pulse) + 6 history/popculture + 5 player. 18 tips popculture supprimés (hors-sujet ou doublons), ≈ 30 réécritures. Placeholder `{captureCount}` retiré partout (`splash-tips-static.json`, `SUPPORTED_PLACEHOLDERS`, `SplashTipsController`, preview → 17 tips player concernés supprimés) ; le set runtime restant est `{speciesCount, remarquableCount, daysSinceFirst}`. Fixup accents / casse des noms communs CSV (`erable` → `Érable`).
- `ArbresNavHost.startDestination` passé en **constante** (`Routes.map()`) ; la redirection vers `WELCOME` est désormais portée par un `LaunchedEffect(onboardingDone)` — le graphe n'est plus reconstruit quand `onboardingDone` change.
- CI : actions GitHub bumpées sur le runtime Node 24 (`actions/checkout` v6, `actions/setup-java` v5, `actions/setup-python` v6, `actions/cache` v5, `actions/upload-artifact` v7, `gradle/actions/setup-gradle` v6, `softprops/action-gh-release` v3).

### Corrigé

- La séquence intro de 10 tips ne jouait pas au tout 1er lancement post-onboarding (le splash partait directement en mode aléatoire). Cause : `startDestination` dérivé de `onboardingDone` → reconstruction du graphe `NavHost` à chaque changement (`null → false → true` sur install frais) → `MapScreen` monté 3×, une instance transiente jouait l'intro **et** appelait `markSplashIntroSeen()` avant que l'instance stable ne lise le flag. Fix via la `startDestination` constante (cf. *Modifié*) ; invariants de `SplashTipsController` intacts (`.first()` figé, pas de `collectAsState` sur `splashIntroSeen`, keys minimales).
- Cold-start de ~30-37 s en intérieur (GPS froid) : `computeInitialCamera` faisait un `getCurrentLocation()` **bloquant** (timeout système ~30 s) sur le chemin critique avant `map.setStyle(...)`, et la caméra ne se recadrait jamais sur le 1er fix. Rendu non-bloquant (`LocationProvider.currentLocation.value ?: parisCamera()`, plus `suspend`) + recadrage caméra automatique au 1er fix GPS (`LaunchedEffect(mapRef)`, coupé si geste utilisateur / tap cluster / mode filtré / `pulseArbreId` / caméra mémorisée restaurée). Cold-start retombé à ~1 s en intérieur.
- `tools/test_build_dataset.py` : 2 attendus de test réalignés sur le pipeline courant (`UNKNOWN_ESPECE_FORMS` inclut `fleur n. sp.` / `fruit n. sp.` depuis le cycle Catalogue ; numérotation Pokédex par count décroissant). La suite repasse entièrement au vert.

### Privacy

- Inchangé : 100 % local, aucune télémétrie, aucun service tiers au runtime.

## [1.2.0] — 2026-05-12

Refonte de l'expression de la progression sous le codename *Progression*. Six sprints : le FAB ★ devient un mode chasse persistant ; Profil et Badges sont séparés conceptuellement (progression chiffrée en barres sur le Profil, badges désormais tous binaires) ; deux familles de badges dynamiques « Familier » émergent du dataset (un genre avec ≥ 7 espèces identifiées, les 20 arrondissements + 2 bois). En corollaire, le cycle « Endgame » disparaît comme cycle nommé — sa pièce maîtresse (maîtrise par arrondissement) est absorbée ici.

### Ajouté

- Map : **mode chasse Étoile** (`HuntPanel.kt`). Le tap ★ ouvre un panneau bas pleine largeur — radar animé, nom + qualification glosée du remarquable non découvert le plus proche, distance live rafraîchie toutes les 5 s en phase avec le balayage du radar (pulse au refresh), ✕ au même emplacement que le FAB ★. Cible **dynamique** recalculée à chaque tick ; fermeture auto à la sortie de l'écran ; FAB GPS / snackbars décalés au-dessus du panneau ; cas « tous découverts » dédié. Radar + pulse pilotés par `withFrameNanos` (insensibles à l'échelle d'animation système). Plus de snackbar éphémère « remarquable proche ».
- Badges : famille **« Familier des … »** — capturer toutes les espèces identifiées d'un genre. Un genre a un badge ssi il a ≥ `GENRE_FAMILIER_MIN_SPECIES` (= 7) espèces identifiées → **26 badges** (libellés au nom vernaculaire pluriel via `GenreInfo.nomFr`, jamais le binôme latin).
- Badges : famille **« Familier du … »** — capturer toutes les espèces recensées dans l'arrondissement (volontairement aspirationnel) → **22 badges** (20 arr. + 2 bois). Dénominateurs précalculés au build dans `assets/arr-species.json` (slug `ArrKey` → liste de `speciesIndex`, miroir Python `arr_key_slug` ↔ Kotlin `parseArrKey`) ; loader `ArrSpeciesIndex` ; couverture propagée par `effectivelyCapturedSpecies` (capturer un chêne couvre `Quercus sp.`).
- Profil : ligne **« X jours depuis ta première capture »** en tête (Fraunces, masquée à 0 capture) + carte **Progression** = jusqu'à **7 barres** Material 3 (`LinearProgressIndicator` + `X / Y · Z %`) : arbres déverrouillés, remarquables capturés, espèces capturées (sous-texte « + N indéterminées »), genres découverts, genres complétés, arrondissements visités (`/22`), arrondissements complétés. Une barre à `0 / N` est entièrement masquée — la liste se densifie avec la progression.
- Badges binaires neufs : `PREMIERE_CAPTURE` (lance la section Badges du Profil dès la 1re capture) ; symétriques démesure `BONSAI` (< 2 m) et `JEUNE_POUSSE` (< 10 cm de circonférence), seuils calés sur la distribution dataset ; `ESPECE_RARE` éclaté en 5 badges de rareté exacte — `Unique` (1 ind.), `Couple` (2), `Trinité` (3), `Quatuor` (4), `Quintette` (5).
- Badges : logos d'arrondissement = **chiffre romain** (I…XX) rendu en texte Fraunces dans le cercle (« Boulogne » / « Vincennes » pour les 2 bois) ; badges de genre = icône partagée `Icons.Outlined.Forest`. `BadgeDef.visual()` (`BadgeVisual.Vector` / `.Label`), cercle `BadgeIconCircle` extrait en composable partagé (`BadgesScreen` ↔ rangée « Derniers badges » du Profil).

### Modifié

- Badges : `BadgeState` aplati en `data class(def, unlockedAt)` — démantèlement de `BadgeState.Progressive`, `TierDef`, `BadgeTier`, `unlockProgressive`, `ProgressiveBadgeCard`. `BadgeEvaluator.evaluate(...)` ne fait plus que des `unlockBinaryOnce` et renvoie `Map<String, Long>` (id → ts de déblocage) ; `BadgeRepository` assemble le catalogue complet via `BadgeCatalog.full(speciesIndex, genreInfo, arrSpecies)` puis zippe. `unlockedAt` figé sur la capture déclenchante (balayage chronologique unique). Aucune migration Room (badges dérivés à la volée).
- `BadgeCatalog.ALL` = 10 binaires statiques ; le compteur du `BadgesScreen` se base sur `badgeRepo.catalog.size` (10 + 26 + 22 selon le dataset).
- Profil : `StatsCard` (tableau plat) remplacée par la ligne « X jours » + la carte Progression. Ligne « Captures totales » supprimée. Titre de section « Infos » (style `titleLarge`) inséré entre « Voir tous les badges » et « Comment jouer ».
- Map : icône / comportement du FAB ★ — popup éphémère → mode chasse persistant (`huntActive` en `remember` côté `MapScreen`).
- Build : `detekt {}` de `app/build.gradle.kts` câble enfin `baseline = file("$projectDir/detekt-baseline.xml")` (le fichier était committé mais ignoré) ; baseline régénérée post-S5. `./gradlew detekt` repasse au vert.

### Retiré

- Badges progressifs `Marcheur` / `Botaniste` / `Chasseur` + `MOSAIQUE_QUERCUS` (l'identité Quercus revient sous forme de badge binaire « Familier des chênes ») ; `TOURNEUR_DE_PARIS` / `TOUR_COMPLET` (redondants avec la barre arrondissements du Profil + les badges « Familier du Xe »).
- `BadgeEvaluator` : param `speciesIndex` sans consommateur, retiré (idem côté `BadgeRepository` avant réintroduction pour `BadgeCatalog.full`).
- `SpeciesDetailScreen` : param `onSpeciesClick` + val `capturedSpecies` morts depuis le déménagement des fiches `(G, sp.)` vers `GenreDetailScreen` (cycle Catalogue), supprimés + call site `ArbresNavHost`.

### Corrigé

- Detekt : les 3 findings réellement neufs corrigés plutôt que figés dans la baseline — vals/params morts de `SpeciesDetailScreen`, `frères` → `freres` dans `SpeciesIndex` (`VariableNaming`).

## [1.1.0] — 2026-05-11

Refonte du catalogue d'espèces sous le codename *Catalogue*. Dix sprints : nettoyage data amont (drops `Non spécifié`, normalisation `sp.`, fixups latins), cascade de noms vernaculaires français (Wikidata P1843 → Wikipedia frTitle filtré → redirects API → overrides éditoriaux → construction), fiches genre dédiées, Arboretum à 2 niveaux *Catalogue* / *Historique*, badge progressif *Mosaïque de chênes*. Refresh OpenData absorbé en passant (217 042 arbres, 183 remarquables). Fil rouge en clôture : 11 fixups de typos / synonymes désuets supplémentaires côté `SPECIES_FIXUPS` + marqueurs Prunus cultivars basculés en `sp.`.

### Ajouté

- Catalogue : 930 entrées dont 782 espèces identifiées et 132 entrées genre `(G, sp.)`, noms français uniques garantis par assert d'unicité au build, numérotation Pokédex `#N` stable sur les identifiées.
- Fiches genre dédiées (`GenreDetailScreen`, route `Routes.GENRE`), 203 genres couverts (200 avec espèces identifiées + 3 only-unknown : Genista, Vitex, Ziziphus). Asset `genre-info.json` avec section *« À Paris »* (count cumulé, hauteur / circonférence médianes, proportion du dataset, top arrondissements sur-représentés). Pipeline `compute_genre_info` côté `tools/build_dataset.py` + cache `tools/.wikipedia-cache/` ~200 articles Wikipedia FR.
- Arboretum : 2 niveaux de `SegmentedButton`. Niveau 1 *Catalogue* / *Historique* (= ex-Découverte). Niveau 2 sous *Catalogue* : *Par fréquence* (count Paris décroissant) / *Par genre* (groupé alphabétique). Headers de chapitre cliquables → fiche genre. HeaderCard affiche `N / 203 genres découverts`. Compteur principal `X / 782` (espèces identifiées seules).
- Auto-débloquage des fiches `(G, sp.)` : capture d'un `Tilia X` quelconque débloque la fiche `Tilia (espèce indéterminée)`. La galerie photos reste alimentée par les seules captures explicites de `sp.`.
- Carte filtrée par genre (fiche `(G, sp.)` → bouton « Voir sur la carte ») : extension de `MAP_FILTERED` à un set de `sk`. Affiche les pins gris des `sp.` à résoudre + les pins verts des espèces du genre déjà capturées.
- Badge progressif *Mosaïque de chênes* (3 / 5 / 10, paliers Bosquet / Chênaie / Forêt, exclut `Quercus sp.`). Catalogue passe à 9 badges (5 binaires + 4 progressifs, 25 paliers).
- Overrides éditoriaux `VERNACULAR_OVERRIDES` (Marronnier commun, Sophora du Japon, Merisier, Thuya géant, etc.) : ~30 noms français curés à la main pour les espèces les plus capturables.
- Rapport HTML self-contained `docs/dataset/index.html` pour validation visuelle du catalogue (sources `nv`, contrôles cas limites, catalogue des 202 genres triables).
- Sanity checks au build (raises) : espèce > 100 perdant sa page WP entre deux builds, `sk` existant disparaissant, entrée `Non spécifié` recevant des arbres (count > 0), `nv` non-unique ou redondant `{g} {e} ({g} {e})`. Warn pour fallback construit sur espèce > 1000 captures.

### Modifié

- Dataset OpenData refreshé : **217 042** arbres (vs 213 042), **183** remarquables (vs 169).
- `species-index.json` : nouveaux champs `nv` (nom vernaculaire unique), `n` (numéro Pokédex stable), `u: true` (flag `unknownSpecies`). Rétrocompat asset legacy maintenue côté `SpeciesIndex` (lecture tolérante).
- `dataset-stats.json` : nouveau champ `totalEspecesIdentifiees`.
- Arboretum : titre des cards = `nv`, sous-titre = binôme latin italique (si différent). Cards `unknownSpecies` visuellement distinctes, toujours en fin de catalogue, sans `#`.
- `BadgeEvaluator` : signature évoluée pour recevoir `SpeciesIndex`. Botaniste exclut désormais les `sp.` (intentionnel — les espèces indéterminées ne comptent pas comme nouvelle espèce identifiée).
- `SPECIES_FIXUPS` étendu : `Olea europea` → `europaea`, Styphnolobium japonicum, Eriobotrya japonica, Ligustrum vulgare, Ulmus parvifolia, Ulmus minor, Platanus x hispanica (S8) ; puis 9 ajouts en clôture (S10) — Sorbus padus → Prunus padus, Rhus verniciflua → Toxicodendron vernicifluum, Eriolobus trilobata → Malus trilobata, Populus canadensis → x canadensis, Sequoiadendron sempervirens → Sequoia sempervirens, Robinia ornus → Fraxinus ornus, Robinia pseudocamellia → Stewartia pseudocamellia (nouvelle entrée sk=929), Fagus purpurea → Fagus sylvatica, Malus communis → Malus domestica. 16 typos / synonymes désuets corrigés au build (~12 arbres OpenData rebindés).
- `UNKNOWN_ESPECE_FORMS` : ajout des marqueurs OpenData `Fleur n. sp.` et `Fruit n. sp.` (cultivars Prunus génériques décoratifs / fruitiers) — 48 arbres basculés vers l'entrée `Prunus sp.` au lieu de polluer le catalogue identifié.
- Cascade `nv` : filtre `frTitle != binôme` (récupère ~50 % des articles WP titrés scientifiquement) + étape Wikipedia redirects API (cache permanent `tools/.wikipedia-aliases-cache/`, ~80 % des cas restants type « Marronnier d'Inde », « Pin noir »).

### Retiré

- 811 arbres `Non spécifié` (4 formes : `sp.`, `n. sp.`, espece vide, `americana` aberrant) — drop dur côté pipeline. Plus de bruit sans valeur de jeu dans le catalogue.

### Corrigé

- Hotfix tri *Par fréquence* du catalogue : count décroissant (et non croissant).

## [1.0.2] — 2026-05-10

Profondeur et lisibilité après v1.0.1, sans casse de schéma. Six sprints atomiques sous le codename *Photos et progressivité* : re-capture + suppression de captures, refonte `PhotoLightbox` (bornes zoom/pan, swipe entre photos), refonte badges en multi-paliers visibles (catalogue 13 → 8, 22 paliers cumulés), saut vers l'arbre exact sur la carte (fly-to + pulse), galerie photos cliquable dans le sheet de détail arbre.

### Ajouté

- Sheet détail : bouton « Recapturer » dès qu'une espèce ou un remarquable est débloqué (modèle Room déjà N captures par arbre, pas de nouveau schéma).
- Galerie photos : long-press sur une vignette ou icône poubelle dans la lightbox plein-écran ouvrent un dialog de confirmation. Si c'est la dernière capture de l'espèce / du remarquable, le dialog prévient du re-verrouillage et la suppression renvoie sur la Map.
- Badges : 3 badges progressifs multi-paliers `Marcheur` (1/10/25/50/100/250 captures), `Botaniste` (1/10/25/50/100/200 espèces), `Chasseur` (1/5/10/25/50 remarquables). Card pleine largeur avec barre + jalons et score absolu (« 37 / 50 »). Catalogue passe de 13 à 8 badges (5 binaires + 3 progressifs, 22 paliers au total).
- Saut vers un arbre exact sur la carte depuis ses points de contact : bouton « Voir sur la carte » plein écran sur la fiche-remarquable, icône Map en haut à gauche de la `PhotoLightbox` (universelle, fiche-espèce comme fiche-remarquable). La nav passe par `Routes.map(arbreId)` (query param `pulseArbreId`), qui déclenche un fly-to ~600 ms à zoom 20 (très fort zoom — un seul pin à l'écran, aucun doute sur l'individu ciblé) et un pulse blanc 2 s sur la position. Pas d'ouverture du sheet : on tape l'arbre soi-même si on veut la fiche.
- Sheet détail arbre : galerie photos cliquable (`PhotoGallery` avec vignettes 120 dp et titre « Tes photos (N) ») en lieu et place du texte « N photo(s) de capture ». Click → `PhotoLightbox` plein écran (zoom/pan/swipe). Long-press → dialog suppression avec wording adaptatif (re-verrouillage si dernière capture de l'espèce ou du remarquable, recompose en `UnknownContent` via les Flows réactifs).

### Modifié

- `PhotoLightbox` : pinch-zoom toujours 1×→5× avec pan désormais clampé aux bords (calcul `boxSize × ratio bitmap × scale`), plus de photo qui s'évade en vignette dans un coin. Galerie ≥ 2 photos : navigation entre photos via `HorizontalPager` (swipe horizontal + chevrons `Outlined.ChevronLeft/Right` désactivés aux bornes), pager gelé dès `scale > 1f`. Détecteur custom `awaitEachGesture` qui ne consomme rien à 1 doigt + scale=1 (laisse passer le pager) et reset auto du zoom à la transition de page.
- Profil : « Derniers badges » liste maintenant les 3 derniers événements de déblocage (chaque palier de progressif compte indépendamment).
- Compteur global de l'écran Badges passe de « X / 13 » à « X / 22 » paliers — progression plus continue qu'un saut binaire.

### Retiré

- 8 badges binaires absorbés par les nouveaux progressifs : `FIRST_CAPTURE`, `PROMENADE`, `MARCHEUR`, `CENTURION` (→ `Marcheur`) ; `BOTANISTE_AMATEUR`, `BOTANISTE_CONFIRME` (→ `Botaniste`) ; `CHASSEUR_REMARQUABLES`, `LEGENDE` (→ `Chasseur`). Pas de migration Room (les badges sont calculés depuis les captures).

## [1.0.1] — 2026-05-09

Patch dette + UX. Cinq sprints atomiques sous le codename *Vérité & Friction*. Aucune casse de schéma, aucune nouvelle feature visible.

### Ajouté

- Map : pulse FAB GPS + snackbar « Localisation en cours… » pendant le gap permission → 1er fix (timeout 30 s avec warning « GPS indisponible — sors à découvert »).
- Map : reprise GPS automatique quand l'utilisateur réactive la localisation système (BroadcastReceiver `PROVIDERS_CHANGED_ACTION`).
- Map : ring orange autour des clusters contenant ≥ 1 remarquable capturé (mémorisation visuelle, indépendant du fill `discovered_count`).
- Détail arbre : haptique `LongPress` à l'ouverture du sheet.
- Capture : snackbar + tic discret à l'annulation de l'app caméra.
- Profil : compteurs « Espèces du Catalogue » `X / 907 (Y %)` et « Arbres déverrouillés » `X / 213 042 (Y %)`.
- Profil : section « Derniers badges » (row des 3 plus récents `unlockedAt`, masquée si vide).
- Profil : label texte + timeout 60 s sur la barre de progression export/import.

### Modifié

- Documentation : mention explicite des 528 fiches espèces enrichies (sur 907) dans le pitch ; précision « tuiles OpenStreetMap via OpenFreeMap, sans envoi de données personnelles » dans la section vie privée du README.
- Fiches remarquables enrichies (qualification, résumé, description, cultivar) accessibles après capture.
- Map : icône FAB ★ passe de la loupe à l'étoile (`Outlined.Star`, tint orange remarquable).
- Map : snackbar distance remarquable allongée à 5 s (helper `showSnackbarFor` partagé).
- Détail arbre : copy `UnknownContent` rappelle la mécanique de déverrouillage par espèce ; `CaptureAvailability.TooFar` affiche la distance courante (« Trop loin (X m / max 30 m). Rapproche-toi. »).
- Capture : haptique déplacée du post-INSERT vers le tap « Capturer » (perçu immédiat avant l'ouverture caméra).
- Empty state des écrans de découverte : `bodyMedium` (14 sp) → `bodyLarge` (16 sp).
- Profil : remplacement de la card unique « Première capture » par la section « Derniers badges ».

### Retiré

- UI saisonnalité (sélecteur de saison sur Arboretum / Remarquables / Profil, bandeau d'archive, ambiance saisonnière sur la carte). Le schéma DB et l'enum `Season` sont conservés pour le futur cycle Variantes.
- 2 badges saisonniers `RONDE_DES_SAISONS` et `ANNEE_COMPLETE` (catalogue passe de 15 à 13 badges).
- Bullets `welcome_bullet_*` du WelcomeScreen (déclarés dans `strings.xml` mais jamais rendus depuis longtemps).

### Corrigé

- Profil : la 1re capture posée la veille au soir s'affiche désormais « il y a 1 jour » au lieu d'« aujourd'hui » (bascule `LocalDate` + `ChronoUnit.DAYS.between` zone Europe/Paris, plus de fenêtre 24 h glissante).

## [1.0.0] — 2026-05-05

### Ajouté

- Carte plein écran de 213 042 arbres parisiens (clusters MapLibre, OpenFreeMap).
- Capture par proximité GPS (< 30 m) + photo locale.
- Arboretum à 907 espèces avec fiche enrichie (Wikipedia FR, stats Paris, mini-carte filtrée).
- Pokédex remarquables dédié (169 fiches, lien fiche PDF Ville de Paris).
- 15 badges en 6 catégories, évalués depuis les captures.
- Saisonnalité 4 saisons, mode archive read-only.
- Profil avec stats Global / Saison vive.
- Export / import ZIP local (Storage Access Framework, dédup idempotent).
- Onboarding + écran « Comment jouer » rejouable.
- Splash cold start avec tips informatifs rotatifs.
- Coloration progressive des clusters carte selon découvertes.

### Privacy

- 100 % local. Aucune télémétrie, aucun compte, aucun service tiers au runtime.
