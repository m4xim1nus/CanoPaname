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
- [ ] **Sprint B** — note `docs/vision-jeu.md` arbitrant ce qu'on garde / jette de Pokémon GO et les spécificités arbre à exploiter.
- [ ] **Sprint C** — bascule sur Phase 2 ci-dessous, planifiée finement à ce moment-là.

## Phase 2 — « Capture » et collection

Objectif : la dimension jeu commence ici.

- [ ] Capture d'arbre : photo (caméra) + GPS + EXIF, stockée localement
- [ ] Validation par proximité (par ex. < 30 m de la position OpenData de l'arbre)
- [ ] Écran « Arboretum » : liste des espèces capturées, % de complétion
- [ ] Mise en avant des arbres remarquables (icône ★, filtre dédié)
- [ ] Notifications de proximité (« arbre remarquable à 80 m »)

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
