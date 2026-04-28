# Roadmap

App perso, pas de calendrier engageant. Phases ordonnées du plus pragmatique au plus ambitieux. Tout est négociable.

## Phase 0 — Scaffold ✅

- [x] Squelette Gradle/Kotlin/Compose, single-Activity + NavHost
- [x] Écran carte (MapLibre) centré sur Paris, écran détail
- [x] Repo `Arbre` stub avec un échantillon en mémoire
- [x] Docs (README, CLAUDE.md, ROADMAP)

## Phase 1 — MVP « voir les arbres autour de moi »

Objectif : sortir une APK installable sur le téléphone, qui affiche la position et les arbres réels de Paris.

- [ ] Géoloc : permission runtime + bouton « me localiser » qui recentre la carte
- [ ] Fallback `LocationManager` natif testé sur GrapheneOS (sans Google Play Services)
- [ ] Schéma Room : table `arbre` avec index spatial (lat, lon) ou R*Tree
- [ ] Import du dataset OpenData au premier lancement (download + parsing CSV/GeoJSON streamé) ou pré-baked SQLite dans `assets/`
- [ ] Couche MapLibre `symbol` ou `circle` qui charge les arbres dans la bbox visible
- [ ] Tap sur un arbre → écran détail avec les vraies données
- [ ] Style de carte décent (Protomaps / MapTiler / tuiles self-host) en remplacement du `demotiles`

**Critère de fin de phase** : je marche dans le 5e, j'ouvre l'app, je vois des points sur la carte aux bons endroits.

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
