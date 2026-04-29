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

## Phase 4 — Page Badges & succès dédiée

Entrée nav distincte du Profil (le Profil garde son badge unique « 1re capture » de Sprint H). Pas d'XP, pas de classement.

- [ ] **Catalogue de badges** — déclarations data class côté Kotlin, calculés à la volée à partir des captures (pas de table Room). Cible 12-15 badges, ni gadget ni pléthorique. Sélection proposée :
  - Découverte : « Premier pas » (déjà), « Promenade » (10 captures), « Marcheur » (50 captures), « Centurion » (100 captures).
  - Botanique : « Botaniste amateur » (50 espèces), « Botaniste confirmé » (200 espèces), « Espèce rare » (espèce avec < 100 individus à Paris).
  - Géographie : « Tourneur de Paris » (captures dans 10 arrondissements), « Tour complet » (les 20 arrondissements).
  - Remarquables : « Chasseur de remarquables » (10 remarquables), « Légende » (50 remarquables).
  - Saisons : « Ronde des saisons » (1 capture dans chaque saison), « Année complète » (1 capture chaque mois sur 12 mois roulants).
  - Démesure : « Géant » (arbre > 30 m de haut), « Vieux sage » (arbre > 4 m de circonférence).
- [ ] **Écran `BadgesScreen`** — grille de cards (icône + libellé + critère + date d'obtention si débloqué, silhouette grise sinon). Section « Débloqués » au-dessus, « À débloquer » en dessous.
- [ ] **Entrée nav** — soit bouton dédié dans le Profil, soit FAB depuis la carte (à décider à l'implémentation).

## Phase 5 — Export / import (backup local)

Le seul moyen de ne pas tout perdre lors d'un changement de téléphone ou d'une désinstallation. Single-player, partage manuel : fichier zip qu'on déplace soi-même.

- [ ] **Export** — bouton dans le Profil. Génère un `arbres-export-YYYYMMDD.zip` contenant :
  - `captures.json` : sérialisation de toutes les `CaptureEntity` (id, arbreId, speciesIndex, remarquable, timestamp, lat/lon device, photoFilename, season).
  - `photos/` : copie des fichiers JPEG sous leur nom UUID.
  - `meta.json` : versionCode/Name de l'app au moment de l'export, schémaVersion, count captures.
  Écriture via Storage Access Framework (`ACTION_CREATE_DOCUMENT`) — l'utilisateur choisit où enregistrer.
- [ ] **Import** — bouton dans le Profil. Lit un zip, valide le `meta.json`, copie les photos dans `getExternalFilesDir(null)/captures/`, INSERT les captures (skip silencieux si `arbreId+timestamp` déjà présent → idempotent).
- [ ] **Politique de fusion** : import additif uniquement, pas d'écrasement. Pas d'option « remplacer tout » au MVP — on peut désinstaller/réinstaller pour repartir vierge.

## Idées en vrac (non engageantes)

- **Lien Wikidata pour les espèces avec QID mais sans page FR** — pour les 175 résolues mais sans `frTitle`, afficher dans la fiche un lien `https://www.wikidata.org/wiki/{qid}` plutôt que le placeholder. 1 ligne UI, gain réel.
- **Search / filtres dans l'Arboretum** — barre de recherche (nom commun, binomial) + filtres (capturé / non, remarquable, par famille). Confort à 907 espèces.
- **Timeline des captures** — écran liste par date décroissante (photo + espèce + lieu).
- **Stats avancées Profil** — heatmap calendaire, top arrondissements parcourus, graphes par saison.

## Hygiène projet (à traiter en parallèle, pas une phase)

- [ ] Merge `claude/phase-3-design-update-hKgD6` puis `claude/go-sprint-h-foH26` dans `main` (résolution `Season.kt` : garder version Sprint I, sur-ensemble). Tag `v0.2.0`.
- [ ] Mettre à jour `CLAUDE.md` avec les conventions H/I/Phase 3 (`SeasonStore`, `ArbresColors` + `staticCompositionLocalOf`, Fraunces, `FilterSplash`).
- [ ] Refondre `README.md` post-merge : statut réel, 2-3 captures d'écran, mention LICENSE.
- [ ] À la veille de la `v1.0.0` : générer keystore release, configurer signing hors-debug, pousser repo public sur GitHub, créer GitHub Release avec APK signé, exposer URL Obtainium.
