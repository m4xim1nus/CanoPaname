# Roadmap

App perso, pas de calendrier engageant. Phases ordonnées du plus pragmatique au plus ambitieux. Tout est négociable.

## Phase 0 — Scaffold ✅

Squelette Gradle/Kotlin/Compose single-Activity + NavHost, écran carte (MapLibre) + écran détail stub, repo `Arbre` en mémoire, docs initiales (README, CLAUDE.md, ROADMAP), icône adaptive sapin, build/install validés sur GrapheneOS via ADB et Android Studio (AGP 8.7.3), carte OpenFreeMap qui charge.

## Phase 1 — MVP « voir les arbres autour de moi » ✅

Style OpenFreeMap tous-zooms, MapLibre Android 11.11.0 (16 KB page-size). Géoloc native runtime (`LocationManager`, sans Google Play Services). Schéma Room avec index `(latitude, longitude)`, dataset pré-baké via `tools/build_dataset.py` (SQLite + GeoJSON dans `app/src/main/assets/`). Couche MapLibre clusterisée (3 layers : clusters / count / points) avec source GeoJSON chargée en RAM puis `setGeoJson(jsonString)` pour clustering Supercluster global. Hit-test à deux niveaux (cluster → expansion zoom ; point → fiche). `LocationComponent` halo bleu pulsant. Caméra hissée dans `MapViewModel`. Fiche arbre complète : nom commun, taxonomie, hauteur, circonférence, adresse, badge ★ remarquable.

**Critère atteint** : 217 855 arbres réels de Paris affichés et tappables.

## Phase 1.5 — Polish carte ✅

Décidé après Phase 1 pour consolider l'UX avant Phase 2. Inspiration produit posée : **Pokémon GO épuré** (capture par proximité GPS + Pokédex/Arboretum, sans combats, raids, social, anti-cheat).

- **Sprint A** ✅ — zoom auto sur dernière position GPS connue au lancement + fiche détail en `ModalBottomSheet` (élimine la recharge tiles).
- **Sprint B** ✅ — note `docs/vision-jeu.md` arbitrant ce qu'on garde / jette de Pokémon GO, capture = photo + GPS au tap, carte = hub. Livré 2026-04-28.

## Phase 2 — Capture et collection ✅

La dimension jeu commence ici. Livré 2026-04-28, validé device.

- **Sprint C** ✅ — capture (photo caméra + GPS device + Room, table `capture` via `MIGRATION_1_2`), validation par proximité (< 30 m) et fraîcheur GPS (< 30 s) au moment du tap. Écran Arboretum « X / Y espèces » + cards par espèce + section remarquables. Découverte par espèce : pins gris par défaut, expression MapLibre `case + match` reconstruite à chaque capture (limite assumée : clusters restent verts au dezoom). Remarquables traités à part : pin gris jusqu'à capture personnelle, FAB ★ → snackbar « plus proche remarquable non découvert : X m ». Plan : `~/.claude/plans/swirling-frolicking-unicorn.md`.
- **Sprint D** ✅ 2026-04-29 — correctifs critiques du test Sprint C. (1) Bug `Expression.match()` MapLibre : default doit être en DERNIER position, sinon expression silencieusement rejetée — fix dans `buildDiscoveryExpression`. (2) Bouton Capturer désactivé sauf en `Ready` via sealed class `CaptureAvailability { Ready / NoGps / TooFar(meters) }`, label reflète la raison. (3) FABs `windowInsetsPadding(statusBars / navigationBars)`. (4) Bonus : pin orange `#FB8C00` pour remarquables capturés, vert pour normaux capturés.

## Phase 2.5 — Profondeur Arboretum ⏳

- **Cold start masqué et raccourci** ✅ 2026-04-29 — était à 40-50 s d'écran blanc. Triple traitement : (1) `ArbresApp.arbresGeoJsonAsync` (`Deferred<String>` sur `Dispatchers.IO`) déclenché dans `onCreate`, en vol pendant init MapLibre + Compose + setStyle réseau ; (2) splash natif via `androidx.core:core-splashscreen` 1.0.1 (`installSplashScreen()` avant `super.onCreate()`) ; (3) splash overlay Compose dans `MapScreen` qui couvre la carte tant que les layers ne sont pas posées (`arbresPrets`), fadeOut 350 ms. Logs de timing ajoutés. PMTiles et lazy-add écartés (effort démesuré pour app perso).

- **Fiche-espèce dans l'Arboretum** ✅ 2026-04-29 — route `species/{sk}` ouverte par tap sur la card. Contenu : identité (nom commun + binomial italique), galerie des captures utilisateur, summary Wikipedia FR + lien externe, stats Paris (count, proportion, médianes hauteur/circ, top 3 arr en absolu + en sur-représentation), mini-carte filtrée. Tout pré-calculé à build-time via `tools/build_dataset.py` → `assets/species-info.json`. Pas d'image Wikipedia (les photos user font l'illustration). Pipeline data : Wikidata SPARQL batched (P225, VALUES de 50) pour résoudre `(genre, espece) → (qid, fr_wiki_title)`, puis fetch summary REST sur le titre canonique. **528 / 907 espèces** avec summary FR, **703 / 907** résolues (QID) ; convergence en une passe, pas de cascade 429. Cache `tools/.wikidata-cache/{sk}.json` (hit / `{qid, noFr}` / `{miss: true}`). Plan : `~/.claude/plans/quiet-petting-river.md`.

### Sprint E — correctifs Phase 2.5 et confort Arboretum ✅ 2026-04-29

Issus du test 2026-04-29 + items 2.5 restants.

- [x] **🐛 Drift de position pour le calcul de distance aux arbres** ✅ 2026-04-29 — symptôme : le pin Location MapLibre était juste, mais `captureAvailability` (et le check dans `runCapture`) utilisait une position décalée de 30 à 200 m, basculant le bouton en `TooFar` alors qu'on était devant l'arbre. **Cause racine** : `LocationProvider.currentOrLastKnown` faisait du one-shot, et le check d'âge basé sur `loc.time` laissait passer un fix « jeune par timestamp » mais spatialement figé (l'OS rafraîchit `loc.time` sans nouveau fix réel quand l'app n'est pas souscrite aux updates). **Fix retenu** : `LocationProvider` souscrit en continu via `LocationManager.requestLocationUpdates` (GPS + NETWORK, intervalle 2 s, distance 1 m), expose un `StateFlow<Location?>` ; `isBetterFix` empêche NETWORK d'écraser un GPS plus précis (sinon yo-yo). Check d'âge migré sur `elapsedRealtimeNanos` (monotonique). Start/stop branchés sur le `DisposableEffect` de `MapScreen` + restart après obtention de la permission. `currentOrLastKnown` conservé en fallback bootstrap (caméra initiale, première seconde TTFF).
- [x] **🐛 Mini-carte embarquée bug la fiche-espèce** ✅ 2026-04-29 — `SpeciesMiniMap.kt` supprimé, remplacé par un bouton « Voir sur la carte » dans `SpeciesDetailScreen` qui navigue vers une nouvelle route `MAP_FILTERED` (destination distincte de MAP pour avoir un MapViewModel propre + caméra Paris z11.5). Top-bar `FilterBanner` (nom de l'espèce + count Paris + back) à la place des FABs ★/Arboretum. **Implémentation du filtre** : 1re tentative (filtre côté layer ou source non clusterisée) crashait MapLibre en `std::bad_alloc` côté natif au z11.5 — le moteur tente de tiler 217k features brutes. **Fix retenu** : pré-filtrer le GeoJSON côté Kotlin (`filterGeoJsonBySpecies` dans `MapScreen.kt`) avant de créer la source. Scan linéaire + StringBuilder, exploite la régularité de la sortie de `tools/build_dataset.py` (`json.dumps(separators=(",", ":"))`, ordre des clés stable, `sk` toujours dernier dans `properties`) pour tester `endsWith("\"sk\":N}}")` sans parser le JSON. Source filtrée garde le clustering (corpus max ~38k pour Platanus, bien moins pour la plupart). Calcul sur `Dispatchers.Default`, log de timing pour diagnostiquer une régression. Hit-test cluster + point inchangé.
- [x] **Fiche individuelle d'un arbre → fiche-espèce** ✅ 2026-04-29 — bouton `FilledTonalButton` « En savoir plus sur l'espèce » dans `DiscoveredContent` (sheet du tap pin), n'apparaît que si `isDiscovered` et `sk != null`. Closes le sheet (`viewModel.closeDetail()`) puis nav vers `Routes.species(sk)`. Bonus livré : à côté de hauteur/circ, comparaison textuelle vs médiane de l'espèce (« médiane 12 (au-dessus) ») via `SpeciesInfo.stats.medianHeightM/medianCircCm` lus dans `MapScreen` et passés en arg ; pas de percentile (la distribution complète n'est pas embarquée, seules les médianes le sont).
- [x] **Petit effet « waouh » à la 1re capture d'une espèce** ✅ 2026-04-29 — `rememberCaptureController` lit un snapshot `captureRepo.capturedSpeciesIndices().first()` AVANT l'`insertCapture` (sinon la nouvelle ligne pollue le set), puis fire `onFirstSpeciesCapture(sk)` ssi `!remarquable && sk !in previouslyCaptured`. Nav vers `Routes.species(sk, celebrate = true)` ; query param `celebrate` (Bool, défaut false) sur la route. `SpeciesDetailScreen` rend un `CelebrationBanner` (Card tertiaryContainer + ★ + texte) au-dessus de l'IdentityBlock quand `celebrate=true`. Volontairement sobre, pas d'animation pétaradante.
- [x] **Disposition Pokédex de l'Arboretum** ✅ 2026-04-29 — `SingleChoiceSegmentedButtonRow` (Liste / Pokédex) sous le HeaderCard, état mémorisé via `rememberSaveable` (survit l'aller-retour fiche-espèce). Vue Pokédex : `LazyVerticalGrid` 3 colonnes par speciesIndex croissant, chaque cellule = numéro `#%03d` + photo (PhotoThumbnail sampleSize=4) ou silhouette « ? » + binôme ou « ??? ». Cliquable seulement pour les espèces capturées. Méthode `SpeciesIndex.entries()` ajoutée pour l'enumération ordonnée stable. Pas de section remarquables séparée — l'annuaire reflète l'inventaire des espèces tout court.

### Sprint F — fiche enrichie pour les arbres remarquables

Profondeur narrative pour les ~200 arbres remarquables de Paris (vs le millier de fiches espèces génériques de Sprint E). Même philosophie pré-bake que `species-info.json` : tout dans un asset JSON, zéro appel runtime.

- [ ] **Pipeline `tools/build_remarquables.py` (ou extension de `build_dataset.py`)** :
  1. **Source primaire OpenData** : ingérer `arbresremarquablesparis` ([opendata.paris.fr](https://opendata.paris.fr/explore/dataset/arbresremarquablesparis/)) — joindre par `idbase` au dataset général. À inspecter en premier : ce dataset a souvent des champs riches (date de plantation, variété, **complément d'observations / anecdote**, raison du classement). Si suffisant, on s'arrête là.
  2. **Source secondaire (si OpenData incomplet)** : agent Sonnet par arbre via Claude API (~200 appels max, raisonnable pour app perso, cache disque dans `tools/.remarquables-cache/{idbase}.json` pour idempotence). Prompt structuré : nom + binôme + adresse + dimensions → recherche web (WebSearch), résume en 3-5 phrases factuelles le caractère remarquable (âge, histoire, particularité botanique, événement associé). Refus explicite si rien trouvé (vaut mieux silence que hallucination).
  3. Sortie : `assets/remarquables-info.json` indexé par `idbase` (`{ idbase: { source: "opendata"|"llm"|"both", description: "...", sources: ["url1", ...] } }`).
- [ ] **Repository + helper** : `RemarquableInfoRepository` chargé une fois dans `ArbresApp` (~200 entrées, qq ko, pas besoin de Room).
- [ ] **UI dans `ArbreDetailContent`** : si `arbre.remarquable && isDiscovered`, section dédiée « Pourquoi cet arbre est remarquable » avec la description et les liens sources. Style visuel distinct (bordure dorée ?) pour marquer le caractère exceptionnel.
- [ ] **Ouvert** : si on garde l'appel LLM, faut-il ajouter une attribution discrète « résumé généré » ? Probablement oui, pour la transparence honnête.

## Phase 3 — Revue graphique

- [ ] Revue d'ensemble des designs / aspects graphiques de l'app
- [ ] Splash screen animé à peu de frais (l'actuel est statique pendant ~30 s en cold start non-cache)
- [ ] **Splash dédié pour la carte filtrée espèce** — actuellement `MapScreen` réutilise le `ColdStartSplash` (« Réveil des 217 855 arbres parisiens… ») même en mode `MAP_FILTERED`, ce qui est incohérent (on ne charge pas tout Paris, juste une espèce, et le pré-filtre Kotlin prend < 1 s). Variante : copy explicite « Filtrage de l'espèce X… » + spinner court, ou simplement un fond neutre sans la phrase d'attente.
- [ ] Palette / typographie / iconographie cohérente entre les écrans

## Phase 4 — Saisonnalité, quêtes, succès

- [ ] **4 formes saisonnières par espèce** — hiver / printemps / été / automne ; même espèce capturée à deux saisons compte pour 2 entrées (la colonne `season` est déjà sur `capture`, schema-ready).
- [ ] **Quêtes géographiques** — « le tour des marronniers du Luxembourg », « les 10 plus grands arbres du 5e », etc.
- [ ] **Page Badges & succès dédiée** — entrée nav distincte de l'Arboretum. Badges narratifs (« 100 arbres > 100 ans visités », « les 10 remarquables du 5e », « 1re capture en hiver »). Pas d'XP, pas de classement.
- [ ] **Fiches naturalistes** — feuille / écorce / fruit en complément des données OpenData.
- [ ] **Notifications de proximité** (« arbre remarquable à 80 m ») — écarté du MVP capture par `docs/vision-jeu.md` §5.5, à reprendre ici.

## Phase 5 — Famille et amis

- [ ] Export / import JSON de l'arboretum (partage manuel — pas de backend)
- [ ] Mode « lobby » local (Bluetooth ou QR code) pour partager une chasse
- [ ] Éventuellement : petit serveur partagé pour la famille, mais pas avant que le solo soit excellent

## Idées en vrac

- Reconnaissance d'espèce via PlantNet API quand la photo est ambigüe
- Open Tree Map (Boston, NYC) pour étendre hors Paris une fois le moteur stable
- Mode « patrimoine » : superposer les vieilles photos IGN avec les arbres actuels
- **Améliorer la couverture Wikipedia** — 204 espèces sans QID Wikidata + 175 avec QID mais sans page FR. Option : pass LLM offline (Claude Code) sur les rejetés pour mapper vers un binôme parent ou marquer « pas de page possible ». Cible : >700 / 907 avec summary.
- **Lien Wikidata pour les espèces sans page FR** — pour les 175 avec QID mais sans `frTitle`, afficher dans la fiche un lien `https://www.wikidata.org/wiki/{qid}` plutôt que le placeholder pur.
