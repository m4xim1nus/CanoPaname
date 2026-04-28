# Roadmap

App perso, pas de calendrier engageant. Phases ordonnées du plus pragmatique au plus ambitieux. Tout est négociable.

## Phase 0 — Scaffold ✅

- [x] Squelette Gradle/Kotlin/Compose, single-Activity + NavHost
- [x] Écran carte (MapLibre) centré sur Paris, écran détail
- [x] Repo `Arbre` stub avec un échantillon en mémoire
- [x] Docs (README, CLAUDE.md, ROADMAP)
- [x] Icône adaptive (sapin sur fond vert)
- [x] Build & install validés sur GrapheneOS via ADB + Android Studio Panda4 (AGP 8.7.3)
- [x] Carte OpenFreeMap qui charge correctement, pan/zoom OK

## Phase 1 — MVP « voir les arbres autour de moi » ✅

Objectif : afficher la position de l'utilisateur et les arbres réels de Paris sur la carte. Pouvoir taper un arbre précis pour voir sa fiche.

- [x] Style de carte tous-zooms (OpenFreeMap)
- [x] Bump MapLibre Android 11.5.2 → 11.11.0 (16 KB page-size alignment côté `libmaplibre.so` — warning résiduel sur `libandroidx.graphics.path.so` à traiter via bump Compose si encore visible)
- [x] Géoloc : permission runtime + bouton « me localiser » qui recentre la carte
- [x] `LocationManager` natif uniquement (sans Google Play Services) — `play-services-location` retiré
- [x] Schéma Room : table `arbre` avec index composite `(latitude, longitude)`
- [x] Pré-baked SQLite + GeoJSON dans `app/src/main/assets/`, générés par `tools/build_dataset.py`
- [x] Couche MapLibre clusterisée : 3 layers (clusters / count / points individuels), source GeoJSON chargée en RAM puis `setGeoJson(jsonString)` pour clustering Supercluster global
- [x] Hit-test : tap cluster → `getClusterExpansionZoom` + zoom in ; tap point → fiche arbre
- [x] `LocationComponent` MapLibre avec halo bleu pulsant (pin position utilisateur)
- [x] Caméra préservée entre `MapScreen` et fiche détail (hissée dans `MapViewModel`)
- [x] Fiche arbre complète : nom commun (`libellefrancais`), taxonomie (genre/espèce/cultivar), hauteur, circonférence, adresse, badge ★ remarquable

**Critère de fin de phase atteint** : la carte montre 217 855 arbres réels de Paris, clusterisés à dezoom, ouvrables individuellement à zoom haut.

### Améliorations non prioritaires identifiées en fin de phase

- [ ] Au retour de la fiche détail, la carte recharge ses tiles (~1-2 s de latence visuelle). La position est bien préservée mais l'expérience n'est pas instantanée. Cause : `MapView` recréé à chaque entrée Compose ; le fix propre est de hisser le `MapView` au niveau Activity ou utiliser un fragment retainé. → traité en Phase 1.5 / Sprint A via passage de la fiche en `ModalBottomSheet`.
- [ ] Au démarrage de l'app, ouvrir directement zoomé sur la dernière position GPS connue (si permission accordée) plutôt que sur PARIS z13. Réduit le « voyage » initial du dezoom Paris vers le 5e. → traité en Phase 1.5 / Sprint A.

## Phase 1.5 — Polish carte (avant phase 2)

Décidé après phase 1 : consolider l'UX carte avant d'empiler la phase 2 (capture + collection) dessus. Inspiration produit pour la phase 2 validée : **Pokémon GO épuré** (capture par proximité GPS + Pokédex/Arboretum, sans combats, raids, social, anti-cheat). Plan d'exécution détaillé : `~/.claude/plans/planifions-la-suite-logical-horizon.md`.

- [x] **Sprint A** — zoom auto sur dernière position GPS connue au lancement + fiche détail en `ModalBottomSheet` (élimine la recharge tiles). Validé sur GrapheneOS.
- [x] **Sprint B** — note `docs/vision-jeu.md` arbitrant ce qu'on garde / jette de Pokémon GO, les spécificités arbre à exploiter, la nature de la capture (photo + GPS au tap) et la boucle de jeu côté UX (carte = hub, pas de radar). Livré 2026-04-28.
- [x] **Sprint C** — phase 2 livrée 2026-04-28 (validation device sur GrapheneOS faite). Plan détaillé dans `~/.claude/plans/swirling-frolicking-unicorn.md`.
- [x] **Sprint D** — correctifs critiques relevés au test du Sprint C livrés 2026-04-29 (cf. ci-dessous). Plan détaillé dans `~/.claude/plans/go-sprint-d-je-hidden-bonbon.md`.

## Phase 2 — « Capture » et collection ✅

Objectif : la dimension jeu commence ici.

- [x] Capture d'arbre : photo (caméra) + GPS device + Room (table `capture`, migration v1→v2). Pas d'EXIF, GPS lu au moment du tap Capturer.
- [x] Validation par proximité (< 30 m de la position OpenData) + GPS frais (< 30 s) au moment du tap.
- [x] Écran Arboretum : « X / Y espèces découvertes » + cards par espèce (count Paris, photos, 1re capture) + section remarquables individuelle.
- [x] Découverte par espèce : pins gris par défaut, expression MapLibre `case + match` reconstruite à chaque capture. Limite assumée : clusters restent verts (pas de progression à dezoom).
- [x] Remarquables traités à part : pin gris jusqu'à capture personnelle, bouton ★ overlay → snackbar « Plus proche remarquable non découvert : X m ».
- [ ] Notifications de proximité (« arbre remarquable à 80 m ») → écarté du MVP capture par `docs/vision-jeu.md` §5.5, à reprendre plus tard.

## Sprint D — correctifs critiques Sprint C ✅

Issus du test sur GrapheneOS de fin de Sprint C. Livrés 2026-04-29.

- [x] **🐛 Pins ne basculent pas verts à la capture** — root cause : ordre des arguments de `Expression.match()` MapLibre inversé dans `buildDiscoveryExpression`. La spec attend `[input, label1, out1, …, default]` avec default en DERNIER ; le code plaçait `PIN_GREY` en 2e position, donc l'expression était silencieusement rejetée. Fix : append `literal(PIN_GREY)` aux stops puis `match(get("sk"), *stops.toTypedArray())`.
- [x] **Bouton « Capturer » désactivé / message clair quand inutilisable** — sealed class `CaptureAvailability { Ready / NoGps / TooFar(meters) }` calculée à l'ouverture du sheet via `LaunchedEffect`. Le bouton est désactivé sauf en `Ready` et son label reflète la raison (« Active le GPS », « Trop loin (X m) »).
- [x] **FABs ★ et Arboretum descendus** — `windowInsetsPadding(WindowInsets.statusBars)` sur le Row TopEnd, `windowInsetsPadding(WindowInsets.navigationBars)` sur le FAB GPS BottomEnd.
- [x] **Bonus : pin orange pour remarquables capturés** — nouvelle constante `PIN_ORANGE = "#FB8C00"` (Material Orange 600) dans la branche remarquable du `match`. Vert reste pour les normaux capturés. Lisible d'un coup d'œil sur la carte.

## Phase 2.5 — Profondeur Arboretum et performance

À traiter après Sprint D, avant Phase 3.

- [ ] **Cold start sub-10 s** — actuellement 40-50 s entre lancement et carte+arbres affichés. Causes probables : parse du GeoJSON 32 Mo en RAM (synchrone), init MapLibre, fetch des tuiles OpenFreeMap. Pistes : splash écran avec progress, parse asynchrone, voir si PMTiles / format binaire raccourcit l'asset load, lazy-add des layers arbres une fois la map prête.
- [ ] **Fiche-espèce dans l'Arboretum** — tap sur une card d'espèce ouvre une page dédiée avec : (a) infos génériques Wikipedia/Wikidata (nom, étymologie, description), (b) stats parisiennes (proportion du dataset, hauteur médiane, circonférence médiane, arrondissements où l'espèce est sur-représentée), (c) mini-carte avec uniquement cette espèce pinnée. **Premier endroit où l'app appelle un service externe** (Wikipedia API), à arbitrer en démarrage de phase.
- [ ] **Effet « waouh » à la capture d'une espèce** — couplé à la fiche-espèce ci-dessus. Au moment du tap Capturer qui débloque une nouvelle espèce : transition animée vers la fiche-espèce, animation pin gris → vert, peut-être un son court. Exclure la 1re capture d'une espèce déjà découverte (pas de waouh à la 2e photo de la même espèce).
- [ ] **Disposition Pokédex de l'Arboretum** — vue alternative en grille numérotée par index d'espèce (« annuaire »), avec toggle entre la vue actuelle (cards triées par capture) et la vue Pokédex (grille triée par numéro). Cases vides pour les espèces non encore capturées (sans révéler leur identité).

## Phase 3 — Saisonnalité et profondeur

- [ ] 4 « formes » saisonnières par espèce → la même espèce capturée en hiver et en mai compte comme 2 entrées (la colonne `season` est déjà sur la table `capture`, sch.-ready)
- [ ] Quêtes géographiques (« le tour des marronniers du Luxembourg », « les 10 plus vieux arbres du 5e »)
- [ ] **Page Badges & succès dédiée** — entrée nav distincte de l'Arboretum. Badges narratifs (« Tu as visité 100 arbres > 100 ans », « Les 10 remarquables du 5e », « 1re capture en hiver »). Pas d'XP, pas de classement.
- [ ] Mode parrainage : suivre un arbre, alerte si chantier signalé par la Ville
- [ ] Fiches naturalistes (feuille / écorce / fruit) en complément des données OpenData

## Phase 3 — Saisonnalité et profondeur

- [ ] 4 « formes » saisonnières par espèce → la même espèce capturée en hiver et en mai compte comme 2 entrées
- [ ] Quêtes géographiques (« le tour des marronniers du Luxembourg », « les 10 plus vieux arbres du 5e »)
- [ ] Mode parrainage : suivre un arbre, alerte si chantier signalé par la Ville
- [ ] Fiches naturalistes (feuille / écorce / fruit) en complément des données OpenData

## Phase 4 — Famille et amis

- [ ] Export / import JSON de l'arboretum (partage manuel — pas de backend)
- [ ] Mode « lobby » local (Bluetooth ou QR code) pour partager une chasse
- [ ] Éventuellement : un petit serveur partagé pour la famille, mais pas avant que le solo soit excellent

## Idées en vrac

- Reconnaissance d'espèce via PlantNet API quand la photo est ambigüe
- Open Tree Map (Boston, NYC) pour étendre hors Paris une fois le moteur stable
- Mode « patrimoine » : superposer les vieilles photos IGN avec les arbres actuels
- Gamification douce : badges narratifs (« Tu as visité 100 arbres > 100 ans »), pas de XP / classement
