# Roadmap

App perso, pas de calendrier engageant. Phases ordonnées du plus pragmatique au plus ambitieux. Tout est négociable. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé.

## Phase 0 — Scaffold ✅

Squelette Gradle/Kotlin/Compose, NavHost, MapLibre, Room, GeoJSON, icône, build/install GrapheneOS via ADB et Android Studio.

## Phase 1 — MVP « voir les arbres autour de moi » ✅

217 855 arbres réels de Paris affichés et tappables. Style OpenFreeMap, MapLibre 11.11.0, géoloc native (`LocationManager`, sans GMS), Room avec index `(latitude, longitude)`, source GeoJSON clusterisée, hit-test à deux niveaux.

## Phase 1.5 — Polish carte ✅

Sprint A (zoom auto + ModalBottomSheet) et Sprint B (`docs/vision-jeu.md` posant la philosophie « Pokémon GO épuré » : capture par proximité GPS + Pokédex/Arboretum, sans combats, raids, social, anti-cheat).

## Phase 2 — Capture et collection ✅

Sprint C (capture photo + GPS + Room, MIGRATION_1_2, Arboretum, découverte par espèce) et Sprint D (correctifs `Expression.match()`, sealed `CaptureAvailability`, insets FABs, pin orange remarquable capturé).

## Phase 2.5 — Profondeur Arboretum ✅

Cold start masqué (splash natif + overlay Compose, 40-50 s → maîtrisé), fiche-espèce dans l'Arboretum (Wikipedia FR 528/907, stats Paris, lien externe), Sprint E (drift GPS fixé, mini-carte → carte filtrée, fiche-arbre→espèce, célébration 1re capture, vue Pokédex), Sprint F (fiche enrichie remarquables : qualification + résumé + description + cultivar), Sprint G (Pokédex remarquables dédié, FAB ★ Liste + 🔍 Loupe, lien fiche PDF Ville de Paris 169/907).

## Sprint H — Profil et premier badge ✅

Branche `claude/go-sprint-h-foH26` (en attente de merge dans `main`).

`ProfileScreen.kt` accessible en TopStart de la carte. Stats : jours depuis 1re capture, # espèces capturées, # remarquables capturés. Section badges minimale avec « 1re capture » (silhouette grise tant que non débloqué).

## Sprint I — Saisonnalité ✅

Branche `claude/go-sprint-h-foH26` (en attente de merge dans `main`).

4 saisons calendaires fixes (`WINTER` / `SPRING` / `SUMMER` / `AUTUMN`), `SeasonStore` singleton, `SeasonSelector` pill compacte (TopAppBar Arboretum/Remarquables, Row TopStart carte), `ArchiveBanner` plein-écran en mode archive read-only, flows scopés saison sur Arboretum/Remarquables/carte, recoloration carte au switch, profil saisonnier (segmented Global / Saison vive). Captures s'accumulent dans le bucket de leur saison sur toutes les années — l'année prochaine, retour des progrès.

## Phase 3 — Revue graphique ✅

Branche `claude/phase-3-design-update-hKgD6` (en attente de merge dans `main`).

Icône launcher platane parisien + variante monochrome Themed Icons. Tokens couleur centralisés dans `theme/Color.kt` + `ArbresColors` (or, écorce, feuille…) via `staticCompositionLocalOf`. Fraunces SemiBold (~70 Ko OFL) sur display/headline/titleLarge. Splash cold-start animé pure Compose (sway sinusoïdal + cascade fade+scale + wordmark). `FilterSplash` dédié `MAP_FILTERED` (« Filtrage de {nom}… »). Iconographie homogène Outlined. Tinting saisonnier discret du surface (light + dark).

## Phase 4 — Page Badges & succès dédiée ✅

Entrée nav distincte du Profil (le Profil garde son badge unique « 1re capture » de Sprint H et expose un lien « Voir tous les badges → »). Pas d'XP, pas de classement.

- [x] **Catalogue de 15 badges** ✅ — déclarés en `BadgeDef` (id, label, description, `BadgeCategory`) dans `data/Badge.kt`, regroupés en 6 catégories (Découverte, Botanique, Géographie, Remarquables, Saisons, Démesure). Pas de table Room : tout est dérivé des captures à la volée par `BadgeEvaluator`.
- [x] **`BadgeEvaluator` pure** ✅ — fonction `evaluate(captures, arbresById, speciesInfo): List<BadgeState>`. Balayage chronologique unique (O(n×b), n captures = quelques centaines en pratique, b = 15) qui maintient des accumulateurs (espèces vues, arrondissements, saisons, YearMonths, count, remarquables). Le timestamp de la capture qui fait basculer le critère est figé comme `unlockedAt`. « Année complète » = recherche d'une fenêtre 12 mois consécutifs dans les YearMonths capturées (zone Europe/Paris). Arrondissement parsé depuis `Arbre.adresse` via regex `, (\d+)(er|e)$` cohérente avec `tools/build_dataset.py:normalize_arr`.
- [x] **Batch fetch arbres** ✅ — `ArbreDao.arbresParIds(ids)` + `ArbreRepository.arbresParIds(ids): Map<Long, Arbre>`. Évite N requêtes pour les badges qui dépendent des caractéristiques (Géant > 30 m, Vieux sage > 400 cm, arrondissements, espèce rare via `SpeciesInfo.stats.count < 100`).
- [x] **Écran `BadgesScreen`** ✅ — route `Routes.BADGES`, accédée depuis le Profil via card `AllBadgesEntry`. Layout : un seul `LazyVerticalGrid` 3 colonnes avec items spans full-width pour l'en-tête (« X / 15 débloqués ») et les titres de section (« Débloqués » / « À débloquer »). Cards homogènes : icône Outlined par badge (mapping `BadgeDef.icon()` dans `ui/badges/BadgeIcons.kt`), libellé + critère toujours visibles, date d'obtention en bas si débloqué, silhouette `Lock` grise sinon. Couleurs cohérentes avec ProfileScreen : `tertiaryContainer` débloqué / `surfaceVariant` verrouillé.

## Phase 5 — Export / import (backup local) ✅

Le seul moyen de ne pas tout perdre lors d'un changement de téléphone ou d'une désinstallation. Single-player, partage manuel : fichier zip qu'on déplace soi-même. Implémentation dans `app.arbre.backup` (BackupExporter, BackupImporter, BackupModels, BackupFilename) — sérialisation `org.json` (cohérent avec les autres datasets, pas de nouvelle dep). UI dans `ProfileScreen` section « Sauvegarde ».

- [x] **Export** ✅ — bouton dans le Profil. Génère `arbres-export-yyyyMMdd.zip` contenant :
  - `captures.json` : sérialisation des captures (sans `id` Room autoincrement, qui n'a pas de sens cross-device).
  - `photos/` : copie des fichiers JPEG sous leur nom UUID.
  - `meta.json` : versionCode/Name de l'app, `schemaVersion = 1`, count captures, `exportedAt`.
  Écriture via Storage Access Framework (`ACTION_CREATE_DOCUMENT` MIME `application/zip`) — l'utilisateur choisit où enregistrer.
- [x] **Import** ✅ — bouton dans le Profil. `ACTION_OPEN_DOCUMENT` filtré sur `application/zip` + `application/octet-stream` (certains file managers exposent .zip différemment). Valide `meta.json`, refus dur si `schemaVersion > CURRENT_SCHEMA_VERSION`. Copie les photos dans `getExternalFilesDir(null)/captures/`, INSERT les captures non-dupliquées via `CaptureDao.captureExists(arbreId, timestamp)`. Photo absente du zip → capture insérée quand même + compteur `photosMissing` remonté.
- [x] **Politique de fusion** ✅ — additif uniquement. Idempotence via dédup `(arbreId, timestamp)` : ré-importer le même zip ne crée pas de doublon. Pas de transaction Room enveloppante volontairement (un import partiel laisse les captures déjà ingérées, dédup garantit la reprise).

## Phase 5.5 — Onboarding ✅

Constat : un nouvel utilisateur arrivait sur une carte 100 % grise sans explication, et la permission GPS n'était demandée qu'au tap du FAB GPS. Corrigé via un `WelcomeScreen` minimal, marker DataStore et nouveau rationale GPS.

- [x] **`OnboardingStore`** ✅ — DataStore Preferences (un seul booléen `done`). Première ouverture (flag absente) → `false`. `markDone()` appelé une fois par le NavHost après que l'utilisateur clique « Commencer ». Singleton dans `ArbresApp`, helper `rememberOnboardingStore()` dans `RepositoryProvider`.
- [x] **`WelcomeScreen`** ✅ — un seul écran scrollable. Hero (logo platane Outlined dans cercle `feuilleSombre` + or), titre Fraunces SemiBold, accroche, 4 BulletCards (commencent gris, capture < 30 m, capture déverrouille genre, remarquables = chasse spéciale), note privacy (« tout reste sur ton téléphone »), bouton « Commencer ». Au clic : check `ACCESS_FINE_LOCATION`, `RequestPermission()` si besoin, `onContinue()` quel que soit le résultat (granted ou denied — l'utilisateur retentera via le FAB GPS de la carte).
- [x] **NavHost startDestination conditionnelle** ✅ — `rememberOnboardingStore().onboardingDone.collectAsState(initial = null)` ; `WELCOME` si false, `MAP` sinon. `null` (transitoire DataStore) → fallback `MAP`, le splash overlay du MapScreen couvre déjà l'écran. Routes ajoutées : `WELCOME`, `WELCOME_REPLAY`.
- [x] **« Comment jouer » dans le Profil** ✅ — `HowToPlayEntry` Card cliquable (icône `HelpOutline`) sous le bloc Badges. Navigue vers `WELCOME_REPLAY` (mode `readOnly = true` avec bouton « Fermer » au lieu de « Commencer »). Utile pour family & friends qui prennent le téléphone.
- [x] **Rationale GPS réécrit** ✅ — `permission_location_rationale` passe de « L'app a besoin de votre position pour afficher les arbres autour de vous. » à « Active la position pour capturer les arbres autour de toi (moins de 30 m). Tout reste sur ton téléphone. » Cohérent avec le ton tutoiement du WelcomeScreen.

## Idées en vrac (non engageantes)

- **Lien Wikidata pour les espèces avec QID mais sans page FR** — pour les 175 résolues mais sans `frTitle`, afficher dans la fiche un lien `https://www.wikidata.org/wiki/{qid}` plutôt que le placeholder. 1 ligne UI, gain réel.
- **Search / filtres dans l'Arboretum** — barre de recherche (nom commun, binomial) + filtres (capturé / non, remarquable, par famille). Confort à 907 espèces.
- **Timeline des captures** — écran liste par date décroissante (photo + espèce + lieu).
- **Stats avancées Profil** — heatmap calendaire, top arrondissements parcourus, graphes par saison.
- **Recadrage du pitch « Pokémon GO »** — la métaphore promet du social/compétitif que le scope single-player ne tient pas. Reformuler en « carnet de bord naturaliste gamifié » dans README + accroche WelcomeScreen, aligner la copy de l'onboarding et le ton des célébrations. Décision d'identité produit, pas de code.
- **Calendrier 12 cases pour le badge « Année complète »** — rendre la progression visible passivement dans le Profil (case par mois, ✓ si une capture existe dans le bucket YearMonth). Sans notif système, juste un état affiché. Capitalise sur le seul critère qui force la rétention long-terme.
- **Refactor du hub navigation carte** — aujourd'hui 3 FABs (GPS, ★, Arboretum) + Profil TopStart + SeasonSelector + ArchiveBanner. Capacité presque saturée. Avant la prochaine grosse feature, prévoir une bottom bar à 3 entrées (Carte / Arboretum / Profil) ou un drawer, plutôt que d'empiler un 4e bouton. À faire à froid, pas en panique.
- **Sound design opt-in** — l'app est aujourd'hui muette partout. 3-4 field recordings courts (~500 ms) pour capture confirmée (froissement de feuilles), badge unlock (craquement de bois), switch de saison (soupir de vent). Banque CC0 freesound.org, ~50 Ko OGG embarqués. Toggle dans le Profil, opt-in par défaut pour rester compatible avec le ton sobre. Élève de « app » vers « expérience sensorielle » sans rompre la charte privacy.
- **Traitement des photos utilisateur** — les JPEG bruts capturés en conditions réelles (pluie, contre-jour, flou de marche) contaminent l'esthétique soignée. Trois pistes : cadre polaroid subtil (bordure crème + ombre douce) qui assume l'imperfection, vignette légère ~10 % qui adoucit les bords brûlés, filtre saisonnier discret (hiver bleuté, été verdâtre). Probablement la piste polaroid seule. Affecte `PhotoThumbnail` + galeries de la fiche-espèce.
- **Accessibilité daltonien sur la couche découverte** — la distinction découvert/non-découvert repose sur gris vs vert, quasi-identiques pour ~8 % des hommes (deutéranopie/protanopie). Ajouter un 2nd signal : halo autour des points découverts, pattern, ou différentiel de saturation/luminosité plus marqué. À tester avec simulateur daltonisme (Android dev options). Affecte `buildDiscoveryExpression` dans `MapScreen`.

## Phase 6 — Hygiène projet ✅

Branche `claude/phase-6-merge-ZEOpJ`. Aligne la doc et prépare l'infra de signing release sans rien committer de sensible (le repo reste privé).

- [x] Branche `claude/phase-4-roadmap-update-qSpGh` consolidée ✅ — merges successifs `claude/review-roadmap-planning-rrUHS`, `claude/phase-3-design-update-hKgD6`, `claude/go-sprint-h-foH26`. Conflit `Season.kt` résolu en faveur de Sprint I (sur-ensemble) ; conflits ROADMAP en faveur de la version review-roadmap (post-arbitrage) ; conflits MapScreen / ProfileScreen alignés sur la convention Outlined de Phase 3.
- [x] **CLAUDE.md à jour** ✅ — Stack : ajout DataStore Preferences + Fraunces. Architecture : ajout des sections `data/` étendue (Season + SeasonStore en mémoire, OnboardingStore DataStore, Badge + BadgeEvaluator pure, RepositoryProvider, RemarquableInfo), bloc `backup/` (BackupExporter / Importer / Models / Filename, dédup additive, schemaVersion=1), bloc `ui/` (WelcomeScreen + WELCOME_REPLAY, MapScreen + FilterSplash + SeasonSelector + ArchiveBanner, RemarquablesScreen, ProfileScreen segmented + Sauvegarde, BadgesScreen). Conventions : nav conditionnelle, saisonnalité non persistée, backup additif, ajout de badges, routes, tokens couleur via `MaterialTheme.arbresColors`. Section Setup : note JDK 21 préservée. Section Commandes : ajout sous-section *Build release signé* (keytool + local.properties + apksigner verify). Section *Quand tu travailles ici* : règles autour de Season ordinal, BadgeCatalog.ALL.size, BackupModels.CURRENT_SCHEMA_VERSION, déclaration de routes.
- [x] **README.md refondu** ✅ — statut « v0.6 (phases 0 → 5.5 livrées) », nouvelle section *Ce que ça fait aujourd'hui* (8 features livrées), pile technique précisée (MapLibre 11.11.0, DataStore, ~907 espèces), section *Build release signé* en pointer vers CLAUDE.md, mention LICENSE MIT. Pas de captures d'écran (à ajouter quand validé device).
- [x] **Signing release configurée** ✅ — `app/build.gradle.kts` lit `local.properties` (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) et bascule `signingConfigs.create("release")` quand le keystore est trouvé. Fallback `signingConfigs.getByName("debug")` sinon — `assembleRelease` reste fonctionnel pour CI / clones frais sans manipuler de clés. Le `.gitignore` excluait déjà `*.jks`, `*.keystore` et `local.properties` — aucune modif nécessaire.

## Phase 7 — Texture sensorielle (motion, célébration, identité)

Capitaliser sur l'identité visuelle posée en Phase 3 (Fraunces, `ArbresColors`, Outlined homogène, splash custom, tinting saisonnier) en étendant le vocabulaire vers le motion, le haptique et les états vides. Pas de nouvelles features fonctionnelles — uniquement de la profondeur sensorielle sur les gestes existants.

- [ ] **Transitions de saison animées** — aujourd'hui le tint surface change instantanément au tap du `SeasonSelector`. Cross-fade ~600 ms du tint (déjà tinté côté `theme/`, juste à animer via `animateColorAsState`) + couche ambiante éphémère 2-3 s au switch (particules saisonnières flottant sur la carte : flocon → pétale de cerisier → feuille verte → feuille cuivrée, Compose `Canvas` + `LaunchedEffect` ou Lottie). Le `SeasonSelector` lui-même morphologique via `Crossfade` plutôt que swap brutal d'icône.
- [ ] **Motion language codifié `ArbresMotion`** — `staticCompositionLocalOf` similaire à `ArbresColors`, exposant durées (`micro = 150ms`, `short = 300ms`, `medium = 600ms`, `long = 1200ms`) + 2-3 easings nommés (« sway » pour les éléments naturels, « snap » pour les confirmations). Wrapper Material 3 motion tokens. Refactor du splash sway, du `FilterSplash` et de la célébration 1re capture pour tirer dessus — empêche que chaque écran réinvente sa durée/easing.
- [ ] **Climax du moment de capture** — le retour de l'intent caméra déclenche aujourd'hui une INSERT Room + une recoloration de point, sans cérémonie. C'est *le* climax émotionnel de l'app, à honorer sur deux sites distincts :
  - **Sur la carte** (point ~8 px, silhouette absente) : ripple subtil depuis le tap (Compose natif) → halo qui s'étend + crossfade gris → vert sur le point lui-même (~300 ms, scale 1× → 1.5× → 1×). Lecture immédiate à zoom courant. Pas de coloriage progressif par le bas — illisible à cette taille. Si 1re d'une espèce, nom binomial en Fraunces qui apparaît brièvement au-dessus du point, hold 800 ms, fade.
  - **Sur la `SpeciesDetailScreen`** (route déjà déclenchée avec `celebrate=true` à la 1re capture) : la silhouette espèce, beaucoup plus grande, supporte une animation contemplative riche. Pistes : cascade fade+scale par feuilles (chaque feuille apparaît avec délai séquentiel), ou spread de couleur centré qui rayonne depuis le tronc. Cohérent avec la grammaire du splash cold-start (déjà cascade fade+scale + sway sinusoïdal). Bloom contemplatif, pas explosion Pokémon GO.
- [ ] **Haptiques** — `LocalHapticFeedback` Compose. Capture confirmée = `LongPress`, badge unlock = pattern custom via `Vibrator`. ~30 min de boulot, à grouper avec le climax capture pour synchroniser visuel + touch.
- [ ] **Empty states designés** — Arboretum 0 capture, Badges 0 débloqué, Remarquables 0 capturé, Profil pré-1re-capture. Illustration vectorielle custom + copy d'invitation Fraunces (« Ta première espèce capturée s'inscrira ici. »). Ce sont les *premiers écrans* post-onboarding — doivent être à la hauteur du splash custom.
- [ ] **Iconographie remarquable inspirée des plaques officielles Paris** — l'étoile actuelle (★) est générique. Les arbres remarquables de Paris ont des plaques métalliques vertes spécifiques (vert sombre, lettrage condensé, coin chanfreiné). Reproduire cette esthétique comme marqueur visuel pour les remarquables capturés (point sur la carte + icône `RemarquablesScreen` + détail). Ancrage hyper-local fort.
- [ ] **Onboarding animé** — remplacer les 4 BulletCards textuelles du `WelcomeScreen` par une boucle animée 3-4 s (Lottie : silhouette grise d'arbre → personnage qui s'approche → tap → silhouette qui se colorie en feuilles → repeat). Lottie Compose (`airbnb/lottie-android`, ~50 Ko). Texte restant = légende, pas explication.

## Phase 8 — Préparation de la release v1.0.0

- [ ] À la veille de la `v1.0.0` : générer keystore release prod, pousser repo public sur GitHub, créer GitHub Release avec APK signé, exposer URL Obtainium. La machinerie côté `build.gradle.kts` est prête (Phase 6) — ne reste plus qu'à provisionner le keystore et le rendre public.
