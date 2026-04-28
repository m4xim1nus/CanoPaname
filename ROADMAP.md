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

### Sprint E — correctifs Phase 2.5 et confort Arboretum (prochaine étape)

Issus du test 2026-04-29 + items 2.5 restants.

- [ ] **🐛 Mini-carte embarquée bug la fiche-espèce** — symptôme : la mini-carte rendue inline dans `SpeciesDetailScreen` perturbe la fiche. **Fix retenu** : retirer l'embed, remplacer par un lien « Voir sur la carte » qui ouvre la grande carte (`MapScreen`) en mode filtré sur l'espèce (réutilise la logique pin-color existante, pas de nouveau composable map à maintenir). Suppression de `SpeciesMiniMap.kt`.
- [ ] **Fiche individuelle d'un arbre → fiche-espèce** — depuis `ArbreDetailContent` (le sheet du tap pin), bouton « En savoir plus sur l'espèce » qui ouvre `SpeciesDetailScreen`. Aussi : situer l'arbre parmi ses pairs (taille vs médiane de l'espèce, percentile de circonférence).
- [ ] **Petit effet « waouh » à la 1re capture d'une espèce** — au moment du tap Capturer qui débloque une nouvelle espèce, transition vers la fiche-espèce avec message de félicitations. Exclure la 2e capture d'une espèce déjà découverte. Couplé à la fiche-espèce.
- [ ] **Disposition Pokédex de l'Arboretum** — vue alternative en grille numérotée par speciesIndex (« annuaire »), toggle avec la vue actuelle (cards triées par capture). Cases vides pour les espèces non encore capturées (sans révéler leur identité).
- [ ] **Améliorer la couverture Wikipedia** — 204 espèces sans QID Wikidata + 175 avec QID mais sans page FR. Option : pass LLM offline (Claude Code) sur les rejetés pour mapper vers un binôme parent ou marquer « pas de page possible ». Cible : >700 / 907 avec summary.
- [ ] **Lien Wikidata pour les espèces sans page FR** — pour les 175 avec QID mais sans `frTitle`, afficher dans la fiche un lien `https://www.wikidata.org/wiki/{qid}` plutôt que le placeholder pur.

## Phase 3 — Revue graphique

- [ ] Revue d'ensemble des designs / aspects graphiques de l'app
- [ ] Splash screen animé à peu de frais (l'actuel est statique pendant ~30 s en cold start non-cache)
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
