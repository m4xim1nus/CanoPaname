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

## Hygiène projet (à traiter en parallèle, pas une phase)

- [x] Branche `claude/phase-4-roadmap-update-qSpGh` consolidée ✅ — merges successifs `claude/review-roadmap-planning-rrUHS`, `claude/phase-3-design-update-hKgD6`, `claude/go-sprint-h-foH26`. Conflit `Season.kt` résolu en faveur de Sprint I (sur-ensemble) ; conflits ROADMAP en faveur de la version review-roadmap (post-arbitrage) ; conflits MapScreen / ProfileScreen alignés sur la convention Outlined de Phase 3. Reste à merger dans `main` puis tagger `v0.2.0` quand validé device.
- [ ] Mettre à jour `CLAUDE.md` avec les conventions H/I/Phase 3/Phase 4 (`SeasonStore`, `ArbresColors` + `staticCompositionLocalOf`, Fraunces, `FilterSplash`, `BadgeEvaluator` + `BadgeCatalog`).
- [ ] Refondre `README.md` post-merge : statut réel, 2-3 captures d'écran, mention LICENSE.
- [ ] À la veille de la `v1.0.0` : générer keystore release, configurer signing hors-debug, pousser repo public sur GitHub, créer GitHub Release avec APK signé, exposer URL Obtainium.
