# TESTS.md — Protocole de tests manuels Phase 9

Cette session est la première grosse passe device depuis longtemps. La branche `claude/phase-8-dev-YGxnL` accumule **20 commits** au-dessus de `main` (Sprints H/I, Phases 3 → 8). Aucun de ces changements n'a été validé sur GrapheneOS — donc avant la release publique (Phase 10), on coche tout ici.

**Comment utiliser ce doc** : on déroule dans l'ordre. Chaque case cochée (`- [x]`) signifie « testé sur device, comportement OK ». Les bugs vont au fur et à mesure dans la section finale `## Bugs trouvés` — à transformer en hotfixes avant de fermer Phase 9.

**Device cible** : téléphone GrapheneOS du dev, branché USB, debug ADB autorisé.

---

## 0. Pré-requis machine

- [x] FF de la branche locale jusqu'au tip distant ✅ (la branche `claude/phase-8-dev-YGxnL` est déjà alignée — tip local = tip distant).
- [x] `JAVA_HOME=/opt/android-studio/jbr` exporté ✅ (utilisé pour toutes les invocations Gradle de la session 2026-05-03).
- [x] Téléphone branché, `adb devices` montre 1 device autorisé ✅ (Pixel 9a sous GrapheneOS, série `4B011JEBF13579`).
- [x] Wrapper Gradle présent ✅.

## 1. Build & tests headless (sans device) ✅

Tous les items de cette section ont été déroulés le 2026-05-03 (avant le smoke device). 5 bugs trouvés et corrigés en cours de route, listés sous **« Bugs Phase 9 — corrigés en build & tests headless »** dans `ROADMAP.md`.

- [x] **Build debug** ✅ — `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (77 Mo). Bug fix : import `androidx.compose.runtime.getValue` ajouté à `WelcomeScreen.kt` pour faire compiler le `by rememberLottieComposition(...)`.
- [x] **Tests JVM** ✅ — `./gradlew :app:testDebugUnitTest` → 34 cas verts (le ROADMAP annonçait 30, le décompte réel est 34). Bug fix #1 : ajout `testImplementation("org.json:json:20240303")` dans `app/build.gradle.kts` + `gradle/libs.versions.toml` (sans ça, `JSONObject(...)` levait à l'instanciation en JVM pure et tous les `BackupImporterTest` retournaient `Failure(CORRUPT_ZIP)`). Bug fix #2 : `BackupImporter` retournait `META_MISSING` au lieu de `CORRUPT_ZIP` sur un fichier non-zip — `ZipInputStream` accepte le garbage en silence ; ajout d'un compteur `entryCount` qui force `CORRUPT_ZIP` si zéro entry.
- [x] **detekt baseline** ✅ — `./gradlew :app:detektBaseline` génère `app/detekt-baseline.xml` (15 lignes : `MapScreen`, `ArbresNavHost`, `ProfileScreen`, `ArbreDetailScreen` — legacy connu). Commité.
- [x] **detekt run** ✅ — `./gradlew :app:detekt` → 0 issue (baseline absorbe le legacy).
- [x] **Lint Android** ✅ — 0 erreur, 65 warnings. Bug fix #1 : `BackupExporter.kt` utilisait `pkg.longVersionCode` (API 28) avec `minSdk = 26` → remplacé par `PackageInfoCompat.getLongVersionCode(pkg).toInt()` (`androidx.core.content.pm`, déjà tiré par `core-ktx`). Bug fix #2 : faux-positif Lint `ProduceStateDoesNotAssignValue` sur le `produceState` suspend de `BadgesScreen.kt` → `@Suppress` localisé (le pattern est idiomatique).
- [x] **Build release fallback** ✅ — `./gradlew assembleRelease` → APK 59 Mo, R8/ProGuard OK, signature v2 vérifiée (`apksigner verify` du SDK build-tools 36.1.0 — note : CLAUDE.md référence 35.0.0 mais ce build-tools n'est plus installé sur la machine du dev, à mettre à jour).

Si `:app:detekt` sort des findings non couverts par le baseline, **NE PAS** silence-fix — analyser case-par-case avec Claude.

---

## 2. Smoke device — cold start

- [ ] `./gradlew installDebug` réussit, app installée (icône platane visible dans le launcher).
- [ ] **Icône launcher** ressemble à un platane parisien (pas le robot Android par défaut). Phase 3.
- [ ] **Icône monochrome** : activer Themed Icons dans les options launcher GrapheneOS → l'icône bascule sur la variante monochrome. Phase 3.
- [ ] **Cold start** : tap sur l'icône → splash natif (fond vert + logo) → splash Compose custom (sway sinusoïdal + cascade fade+scale + LinearProgressIndicator or, texte « Réveil des 213 042 arbres parisiens ») → carte. Pas de flicker entre les deux splashes. Phase 3 + Phase 7 motion.
- [ ] **Durée splash custom** : ~2-3 s avant que la carte n'apparaisse, le temps que les 213k pins se chargent.

Si le splash custom ne s'affiche pas et qu'on voit directement la carte vide grise, vérifier les logs : `adb logcat -s MapScreen:I` doit afficher `MapView init`, `Style prêt`, `GeoJSON disponible`, `Layers posées`.

---

## 3. Onboarding (1er lancement) — Phase 5.5 + Phase 7 Lottie

**Pour rejouer ce flow**, désinstaller l'app (`adb uninstall app.arbre.debug`) puis réinstaller.

- [ ] **WelcomeScreen** s'affiche en premier (pas la carte). Phase 5.5.
- [ ] **Animation Lottie** (canopée d'arbre qui scale + bascule gris → vert → gris, boucle 4 s). Phase 7.
- [ ] **Caption Fraunces SemiBold** unique en-dessous de l'animation (pas les anciennes 4 BulletCard).
- [ ] **Hero logo** : platane Outlined dans cercle `feuilleSombre`, ourlet or.
- [ ] **Note privacy** « Tout reste sur ton téléphone » visible.
- [ ] **Bouton « Commencer »** présent.
- [ ] Tap « Commencer » → **dialog permission GPS Android** s'affiche.
- [ ] **Refuser** la permission → on arrive quand même sur la carte (pas de blocage).
- [ ] **Re-désinstaller**, ré-installer, re-tester → cette fois **accepter** la permission → on arrive sur la carte avec le pin user déjà actif.
- [ ] **Re-lancer l'app** (sans désinstall) → on arrive **direct sur la carte**, pas de re-WelcomeScreen.
- [ ] **Replay onboarding** : ouvrir Profil → tap card « Comment jouer ? » → re-affiche WelcomeScreen, mais bouton **« Fermer »** au lieu de « Commencer ». Tap → retour Profil sans rien re-déclencher.

---

## 4. Carte principale — régression Phase 1 + saisonnalité Sprint I + motion Phase 7

### 4.1 Affichage

- [ ] **213 042 pins** parisiens visibles à z13 sur Paris (clustering vert). Au dezoom max, le cluster unique doit afficher exactement ce count (matche le « Réveil des 213 042 » du splash).
- [ ] **Dezoom à z11** : clusters seulement, pas de grille de pins (régression GeoJSON tiling).
- [ ] **Zoom à z16** : pins individuels gris (non-capturés).
- [ ] **Tap sur cluster** : la carte zoome (animation) sur la zone du cluster.
- [ ] **Tap sur pin individuel** : `ModalBottomSheet` détail s'ouvre (Sprint A régression).

### 4.2 Hub navigation

- [ ] **TopStart** : FAB Profil (icône Person) + SeasonSelector pill compacte juste à droite. Sprint H + Sprint I.
- [ ] **TopEnd** : FAB plaque remarquable « R » (Phase 7) + FAB Arboretum (livre).
- [ ] **BottomStart** : FAB loupe (« plus proche remarquable non capturé »).
- [ ] **BottomEnd** : FAB GPS (Me localiser).
- [ ] FAB plaque **N'est PAS tinté** : la bichromie verte/crème de la plaque doit être visible (pas un aplat gris). Phase 7.

### 4.3 Géolocalisation

- [ ] FAB GPS (BottomEnd) → centre la caméra sur la position user, zoom 16.
- [ ] Pin user (cercle bleu pulsé) visible sur la carte.
- [ ] FAB loupe (BottomStart) → snackbar « Plus proche remarquable non découvert : X m ».
- [ ] Si tous remarquables découverts : snackbar « Tous les remarquables sont découverts ».
- [ ] Si GPS désactivé : snackbar « Position indisponible ».

### 4.4 Saisonnalité (Sprint I)

- [ ] **SeasonSelector** affiche la saison vive (printemps fin avril 2026 → "Printemps").
- [ ] Tap sur une autre saison (ex. "Hiver") → la pill bascule **avec un Crossfade** (pas un swap brutal). Phase 7.
- [ ] Bascule en saison ≠ vive → **ArchiveBanner** plein écran TopCenter apparaît : « Hiver — figé ». Sprint I.
- [ ] Les FABs descendent sous l'ArchiveBanner.
- [ ] FAB loupe disparaît en mode archive (pas de sens de chercher hors-saison-vive).
- [ ] **Recoloration carte** : les pins capturés en hiver passent en vert (et seulement ceux-là). En saison vive, tous les capturés sont verts.

### 4.5 Saison animée (Phase 7)

- [ ] **SeasonAmbience** : 16 particules saisonnières descendent sur la carte (flocons en hiver, feuilles ocre en automne, pétales en printemps, etc.). Animation continue, fade in/out 1.8 s au switch.
- [ ] **Theme `surface`/`background`** anime au switch saison (tinting très subtil — printemps vert pâle, automne ocre crème, hiver bleu-gris pâle, été vert dense pâle).

---

## 5. Capture — régression Phase 2 + climax Phase 7 + haptiques

### 5.1 Pré-requis : trouver un arbre proche

- [ ] Aller dans un parc / une rue. Zoomer à z16. Identifier un pin gris à < 30 m de soi (le bouton Capturer sera disponible).

### 5.2 Capture nominale

- [ ] Tap sur un pin → fiche s'ouvre (sheet bottom).
- [ ] Si arbre **non découvert** : la fiche montre la silhouette générique grise + le bouton « Capturer ». Sprint C.
- [ ] Si trop loin : message d'unavailability dans la fiche, bouton désactivé.
- [ ] Tap **Capturer** → permission CAMERA demandée si 1re fois.
- [ ] Caméra Android s'ouvre, on prend la photo, on valide.
- [ ] **Haptique** : vibration courte au moment de l'INSERT (post-photo). Phase 7.
- [ ] **Climax visuel sur la carte** : halo qui s'étend (8→48 px) + cœur scale 1×→1.5×→1× sur 300 ms. Phase 7.
- [ ] **Si 1re espèce capturée** : le binomial latin (Fraunces italique) **flotte 800 ms** au-dessus du point puis fade. Phase 7.

### 5.3 Vérifications post-capture

- [ ] Le pin **passe au vert** sur la carte (il y a en réalité un Flow combine qui met à jour la `circleColor` expression — le changement doit être quasi-instantané).
- [ ] Re-tap sur le pin : la fiche montre maintenant les **photos capturées** (galerie) + médianes hauteur/circ.
- [ ] **Re-capturer le même arbre** : le climax visuel reproduit le halo + cœur, **mais pas le binomial flotté** (l'espèce n'est plus 1re).

### 5.4 Mode archive : capture désactivée (Sprint I)

- [ ] Basculer la saison sur une saison ≠ vive (ArchiveBanner visible).
- [ ] Tap sur un pin → la fiche s'ouvre mais le bouton **Capturer est désactivé** (`CaptureAvailability.Archived`).

### 5.5 Cas d'erreur GPS

- [ ] Désactiver le GPS device, ouvrir une fiche, tenter Capturer → message « GPS indisponible » ou « Tu es trop loin de cet arbre ».
- [ ] Réactiver, attendre un fix, retenter → OK.

### 5.6 Persistance après kill app

- [ ] Capturer un arbre.
- [ ] **Kill l'app** depuis la liste des récents Android.
- [ ] Relancer → le pin est toujours vert. Le profil compte la capture. La fiche montre la photo.

---

## 6. Arboretum (Pokédex global) — régression Phase 2.5 + saisonnalité Sprint I

- [ ] FAB Arboretum (TopEnd, livre) → écran Arboretum.
- [ ] **Header** : « X / 907 espèces » (compteur basé sur `DatasetStats`).
- [ ] **SeasonSelector** dans la TopAppBar : permet de scoper la liste à une saison.
- [ ] Si saison ≠ vive : **ArchiveBanner** affiché en haut.
- [ ] **Cards par espèce capturée** : photo + nom commun + binomial Fraunces + count Paris + 1re capture date.
- [ ] **Section Remarquables** distincte en bas : liste des remarquables capturés (Sprint G).
- [ ] **Empty state** (Phase 7) si aucune espèce capturée dans la saison sélectionnée : illustration custom (silhouettes douces palette feuilleSombre/or/ecorce) + Fraunces + corps Ecorce 70 % opacity. Vérifier qu'il s'affiche bien (passer en saison vide).
- [ ] Tap sur une card d'espèce → navigation vers fiche-espèce (`Routes.SPECIES`).

---

## 7. Fiche-espèce — Phase 2.5 + Phase 7 climax

### 7.1 Contenu de base (Phase 2.5)

- [ ] **IdentityBlock** : nom commun + binomial latin (Fraunces titleLarge).
- [ ] **PhotoGallery** : LazyRow des photos user pour cette espèce.
- [ ] **WikipediaBlock** : résumé FR (si présent dans `species-info.json`) + lien externe Wikipedia.
- [ ] **EssencePdfBlock** : si l'espèce a un PDF Ville de Paris, lien vers le PDF (Sprint G).
- [ ] **StatsBlock** : count Paris, hauteur médiane, circ médiane, top arrondissements.
- [ ] **ShowOnMapButton** (FAB) : navigue vers `MAP_FILTERED` pour cette espèce.

### 7.2 Mode filtré sur la carte

- [ ] Tap « Voir sur la carte » → `FilterSplash` (overlay bref « Filtrage de … ») → carte ne montre que les pins de cette espèce.
- [ ] **FilterBanner** TopStart : bouton retour ← + nom de l'espèce + « X arbres dans Paris ».
- [ ] Pas de FABs Profil / Saison / Remarquables en mode filtré (UI épurée).
- [ ] Tap retour → retour fiche-espèce.

### 7.3 Climax 1re capture (Phase 7)

- [ ] Capturer une espèce **jamais vue** sur la carte.
- [ ] Sur la fiche-espèce s'ouvre automatiquement (route `species/{sk}?celebrate=true`) avec **CelebrationHero** :
  - Cascade fade+scale en 4 paliers sur 1800 ms.
  - Silhouette `Park` grand format.
  - Fond tinté `feuilleSombre`.
  - Binomial italique.
  - Label de confirmation (« Espèce ajoutée à ton arboretum » ou similaire).
- [ ] Re-naviguer vers cette même fiche-espèce sans paramètre `celebrate` → **pas de CelebrationHero** (juste le contenu standard).

---

## 8. Remarquables (Pokédex dédié) — Sprint G + plaque Phase 7

- [ ] FAB plaque (TopEnd) → écran Remarquables.
- [ ] **2 FABs** dans cet écran : ★ Liste (toggle) + 🔍 Loupe (plus proche non-découvert).
- [ ] Liste : cards par remarquable capturé, plaque « R » en visuel (pas une étoile).
- [ ] **Empty state** (Phase 7) si 0 remarquable capturé : illustration custom.
- [ ] Tap sur card → fiche détail remarquable (Sprint F + G) : qualification + résumé + description + cultivar + lien PDF Ville de Paris.

---

## 9. Profil — Sprint H + Phase 5 backup + Phase 7 empty state

- [ ] FAB Profil (TopStart) → ProfileScreen.

### 9.1 Stats

- [ ] **Segmented Global / Saison vive** en haut : permet de filtrer les stats par saison.
- [ ] **StatsCard** : 1re capture (date), # espèces, # remarquables, total captures.
- [ ] Stats Global : compte tous les buckets cumulés.
- [ ] Stats Saison vive : compte uniquement la saison courante (pas une saison archivée).

### 9.2 Empty state pré-1re-capture (Phase 7)

- [ ] Si **0 capture** au compteur : `EmptyState` profil avec illustration custom + texte d'invitation. À tester en désinstallant + réinstallant + skip onboarding sans capturer.

### 9.3 Section Badges (Sprint H)

- [ ] **Card unique « Première capture »** : silhouette grise tant que pas débloqué, dorée + date sinon.
- [ ] **Card « Voir tous les badges → »** sous la précédente : navigue vers `Routes.BADGES`.

### 9.4 Sauvegarde (Phase 5)

- [ ] **Card « Sauvegarde »** avec 2 boutons : Export, Import.
- [ ] Tap **Export** → SAF "Enregistrer sous" s'ouvre (MIME `application/zip`).
- [ ] Choisir un dossier + nom (proposé par défaut : `arbres-export-yyyyMMdd.zip`) → snackbar « Export terminé : N captures ».
- [ ] Vérifier le zip : il contient `meta.json`, `captures.json`, `photos/*.jpg`. (`adb pull` ou ouvrir via gestionnaire de fichiers.)
- [ ] Tap **Import** → SAF "Ouvrir" filtré sur `application/zip` + `application/octet-stream`. Sélectionner le zip exporté.
- [ ] Snackbar : « Import OK : 0 nouvelle, N déjà présentes » (idempotence).
- [ ] **Test photo manquante** : éditer le zip exporté, supprimer une photo de `photos/`, ré-importer → snackbar « N captures, 1 photo manquante ». La capture doit être présente dans le profil.
- [ ] **Test schema futur** : éditer `meta.json` du zip, mettre `schemaVersion: 99`, ré-importer → snackbar refus dur (« Format trop récent » ou similaire).

### 9.5 « Comment jouer ? » (Phase 5.5)

- [ ] **Card HowToPlayEntry** sous Sauvegarde, icône `HelpOutline`.
- [ ] Tap → WelcomeScreen replay (mode readOnly avec « Fermer »).

---

## 10. Badges — Phase 4 + empty state Phase 7

- [ ] Profil → « Voir tous les badges » → `BadgesScreen`.
- [ ] **Header full-width** : « X / 15 débloqués » (compteur basé sur `BadgeCatalog.ALL.size`).
- [ ] **Section « Débloqués »** (titre full-width) : cards `tertiaryContainer`, icône Outlined, libellé, critère, date d'obtention.
- [ ] **Section « À débloquer »** : cards `surfaceVariant`, silhouette Lock grise, libellé + critère.
- [ ] **Empty state** (Phase 7) si 0 débloqué : illustration custom dans la grille (span full-width au lieu de la section « Débloqués »).
- [ ] Vérifier les **15 badges** présents dans 6 catégories : Découverte (4), Botanique (3), Géographie (2), Remarquables (2), Saisons (2), Démesure (2).
- [ ] **Test débloquage en live** : capturer un arbre, ré-ouvrir BadgesScreen → « Première capture » a basculé en débloqué.

---

## 11. Saisonnalité (transverse) — Sprint I

À faire après quelques captures dans la saison vive.

- [ ] **Switch saison sur la carte** : pins capturés dans la saison sélectionnée passent en vert, les autres restent gris.
- [ ] **Switch saison sur l'Arboretum** : la liste se filtre.
- [ ] **Switch saison sur le Profil** : segmented "Saison vive" met à jour les compteurs.
- [ ] **Persistance ?** : kill app, relance → la saison est **réinitialisée à la saison vive** (`SeasonStore` est en mémoire seulement, c'est volontaire).
- [ ] **Saison stockée dans la capture** : capturer en mode saison vive (printemps), passer en mode archive été, re-capturer ne devrait pas être possible (capture désactivée). Bon — pas testable strictement, juste vérifier que `CaptureEntity.season` est bien la saison courante au moment de la capture.

---

## 12. Identité visuelle — Phase 3

À regarder en survolant l'app dans son ensemble.

- [ ] **Fraunces SemiBold** sur tous les titres `displayMedium`, `headlineLarge`, `titleLarge` (Profil header, fiche-espèce binomial, Welcome titre, Arboretum header, etc.). Le reste de la typo reste sur la pile Material 3 par défaut.
- [ ] **Tokens couleur** : pas de couleur Material 3 « bleue » par défaut visible. Palette verte/brune (or, écorce, feuille).
- [ ] **Iconographie Outlined** homogène : Person, MyLocation, Search, MenuBook, HelpOutline, Lock, etc. (pas de mix Filled/Outlined).
- [ ] **Tinting saisonnier** discret du `surface` : observable en passant d'une saison à l'autre, le fond des cards bouge légèrement.
- [ ] **Splash animé** au cold start (cf. § 2).

---

## 13. Empty states — Phase 7

Récapitulatif des 4 empty states à vérifier (déjà mentionnés mais centralisés ici) :

- [ ] **Arboretum** vide (saison sans capture) : illustration `illus_empty_arboretum.xml`.
- [ ] **Badges** zéro débloqué : illustration `illus_empty_badges.xml` en span full-width.
- [ ] **Remarquables** zéro capturé : illustration `illus_empty_remarquables.xml`.
- [ ] **Profil** pré-1re-capture : illustration `illus_empty_profile.xml`.

Chacun doit montrer la même esthétique : silhouettes douces, palette `feuilleSombre`/`or`/`ecorce`, Fraunces SemiBold pour le titre, corps Ecorce 70 % opacity.

---

## 14. Build release signé (Phase 6)

À faire **après** que tout le reste passe sur debug.

- [ ] Générer un keystore de test (à conserver hors-repo) :
  ```
  keytool -genkey -v -keystore arbres-release.jks -keyalg RSA -keysize 2048 \
          -validity 10000 -alias arbres
  ```
- [ ] Renseigner `local.properties` (jamais committé, déjà dans `.gitignore`) :
  ```
  RELEASE_STORE_FILE=arbres-release.jks
  RELEASE_STORE_PASSWORD=...
  RELEASE_KEY_ALIAS=arbres
  RELEASE_KEY_PASSWORD=...
  ```
- [ ] `./gradlew assembleRelease` → APK signé prod dans `app/build/outputs/apk/release/app-release.apk`.
- [ ] Vérifier la signature :
  ```
  $ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose \
      app/build/outputs/apk/release/app-release.apk
  ```
- [ ] Désinstaller l'APK debug, installer l'APK release (`adb install -r app-release.apk`).
- [ ] **Smoke release** : onboarding + 1 capture + ouvrir Profil/Arboretum/Badges. Vérifier que ProGuard/R8 minify n'a rien cassé (notamment Room, MapLibre, Compose, Lottie, org.json).

Si on observe un crash en release qui n'existait pas en debug, c'est probablement une keep rule manquante dans `app/proguard-rules.pro`. Logs : `adb logcat -s AndroidRuntime:E`.

---

## 15. Tests JVM — Phase 8

Déjà partiellement couverts en § 1, mais on confirme ici les attendus précis :

### 15.1 BadgeEvaluator (23 cas)

- [ ] `parseArrondissement` : 1er, 11e, 20e, hors-Paris, range > 20.
- [ ] `yearMonthOf` : zone Europe/Paris + rollover UTC↔Paris.
- [ ] `hasTwelveConsecutiveMonths` : 12 pile, year boundary, gap.
- [ ] 0 capture → 15 verrouillés.
- [ ] 1 capture → `first_capture` débloqué.
- [ ] 10 captures → `promenade` débloqué, timestamp figé sur la 10ème.
- [ ] Geant > 30 m / Vieux sage > 400 cm.
- [ ] Espèce rare (`count < 100`).
- [ ] Remarquables ne comptent pas pour les espèces.
- [ ] 10 / 20 arrondissements distincts.
- [ ] Hors-Paris ignorés.
- [ ] 4 saisons / 12 mois consécutifs.
- [ ] Dédup remarquables sur `arbreId`.
- [ ] `unlockedAt` figé.

Run : `./gradlew :app:testDebugUnitTest --tests "app.arbre.data.BadgeEvaluatorTest"`.

### 15.2 BackupImporter (7 cas)

- [ ] Roundtrip 2 captures.
- [ ] Idempotence 2nd import → `imported=0, skipped=N`.
- [ ] Photo manquante → `photosMissing` incrémenté, capture insérée.
- [ ] `schemaVersion = 99` → `SCHEMA_TOO_NEW`.
- [ ] Zip corrompu → `CORRUPT_ZIP`.
- [ ] `meta.json` absent → `META_MISSING`.
- [ ] `captures.json` absent → `CAPTURES_MISSING`.

Run : `./gradlew :app:testDebugUnitTest --tests "app.arbre.backup.BackupImporterTest"`.

---

## 16. Vérifications transverses

- [ ] **versionCode = 7 / versionName = "0.7.0"** dans l'APK installé. Vérif :
  ```
  $ANDROID_HOME/build-tools/35.0.0/aapt dump badging \
      app/build/outputs/apk/debug/app-debug.apk | grep version
  ```
- [ ] **Pas de crash GrapheneOS sans GMS** : MapLibre tombe correctement sur `LocationManager` natif (pas de `play-services-location`). Phase 1 régression.
- [ ] **APK size raisonnable** : < 25 Mo en debug, < 15 Mo en release minifié (gain depuis Phase 8 : ~1 Mo retiré de moshi/okhttp).
- [ ] **Onboarding Lottie** ne ralentit pas le 1er rendu sensiblement (lottie-compose 6.4.0).
- [ ] **Pas de Google Play Services** dans les deps (`./gradlew :app:dependencies | grep -i play-services` → vide).

---

## 17. Ce que Claude peut faire à tes côtés

Pendant cette session de tests, je peux exécuter en parallèle :

- **Builds** : `./gradlew assembleDebug`, `installDebug`, `test`, `lint`, `detekt`, `assembleRelease`. Je relaie les erreurs et propose des fix.
- **ADB** : `adb logcat -s MapScreen:I CaptureLauncher:I BackupImporter:E AndroidRuntime:E` pour suivre les logs pendant que tu cliques. `adb shell pm list packages | grep arbre` pour vérifier l'install. `adb shell dumpsys package app.arbre.debug | grep versionName`.
- **Génération de zips de test** pour BackupImporter : je peux fabriquer à la main un zip avec un `meta.json` corrompu / une photo manquante / un schemaVersion futur, le pousser via `adb push`, et tu testes l'import.
- **Inspect Room DB** : `adb shell run-as app.arbre.debug ls databases/`, puis `sqlite3` pour confirmer les schémas / contenus (ex. la migration `MIGRATION_1_2` a bien créé la table `capture` avec colonne `season`).
- **Vérifier les rapports detekt / lint** : lire `app/build/reports/detekt/detekt.html` / `app/build/reports/lint-results-debug.html`.
- **Analyser un crash** : tu me donnes les ~30 lignes de stack `adb logcat`, je localise le coupable et propose un patch.
- **Mesurer le cold start** : suivre les logs `MapScreen:I` qui timent `MapView init`, `Style prêt`, `GeoJSON disponible`, `Layers posées` (instrumenté Phase 2.5).
- **Bumper versionCode/Name** quand tu fermes la phase, et préparer le commit Phase 9 (cocher la ROADMAP, transformer les bugs en items concrets).

Si quelque chose te bloque pendant le test, **demande-moi avant de bricoler** — c'est plus rapide que de débugger en aveugle.

---

## 18. Bugs trouvés (à remplir pendant la session)

Format : `[Section §X.Y] Description courte. Stack/log si pertinent. → action proposée.`

### Session 2026-05-03 — 1er smoke device (4 bugs critiques fixés en cours)

- [x] **[§2 cold start]** Crash au 1er lancement, app meurt après 2 s. Logcat : `SQLiteException: no such table: capture (code 1 SQLITE_ERROR)` puis `file unlinked while open: arbres-paris.db`. → Asset DB shippé en `user_version = 0` (script Python ne settait pas le pragma), Room cible v2 sans migration `0→2` → `fallbackToDestructiveMigration` re-copie l'asset (toujours v0) en boucle, migration `1→2` jamais exécutée, table `capture` jamais créée. Fix : `PRAGMA user_version = 1` ajouté à `tools/build_dataset.py` + asset existant patché en place.
- [x] **[§4 carte]** Carte non-interactive : zoom/pan/tap bloqués, FAB « centrer sur ma position » fonctionne (preuve que la carte sous-jacente est OK, c'est un overlay qui swallow les touches). → 2 sites : `SeasonAmbience.kt` et `CaptureCelebrationOverlay.kt` attachaient `pointerInput(Unit) {}` censé être un no-op « laisse passer les events ». Faux — un `pointerInput {}` vide INTERCEPTE les events sans les forwarder. Fix : `pointerInput` retiré des deux Canvas overlay.
- [x] **[§4 carte]** Sélecteur de saison sur la carte n'a pas de raison d'être (la carte n'est pas un écran de stats, et Profil/Arboretum/Remarquables ont déjà chacun leur sélecteur). → Retrait du `SeasonSelector` + `ArchiveBanner` + branche `isArchive` de `MapScreen`. La carte affiche toujours `Season.current()`.
- [x] **[§2 splash]** Splash bloqué après une optim ratée (régression introduite en cours de session) — `GeoJsonSource(json)` appelé sur `Dispatchers.Default` jetait `CalledFromWorkerThreadException` immédiatement (MapLibre exige UI thread sur sources). Exception attrapée par `catch (Throwable)`, `arbresPrets` jamais setté, splash affiché indéfiniment. → Revert : `addArbresLayers(style, json)` sur Main comme avant. Le freeze ~700 ms du parsing 32 Mo est toléré.

### Restant — non-bloquant, basculé en Phase 10 (polish v1.0)

- [ ] **[§2 splash]** Splash apparaît mais aucune animation visible (sway, intro fade, progress bar). Cause : `Skipped 184 frames!` au cold start sature le main thread pile pendant la fenêtre de visibilité du splash → Choreographer ne tick pas. Voir Phase 10 ROADMAP pour pistes de fix.
- [ ] **[§3 onboarding]** Sapin présent sur WelcomeScreen alors que le reste de l'app utilise un platane. 2-3 silhouettes différentes coexistent dans les écrans de chargement / hero. → Audit drawables + dédup en Phase 10.
- [ ] **[transverse]** Nom d'app « Arbres » → renommer en **CanoPaname** partout (strings, README, GitHub, OS). Décidé en fin de Phase 9, traité en Phase 10.
- [ ] **[transverse]** « en printemps » → « au printemps » (préposition correcte). 4 sites identifiés (`RemarquablesScreen`, `ArboretumScreen` ×2, `ProfileScreen`). Helper `Season.preposition()` à introduire en Phase 10.

À transformer en commits hotfix en fin de session, à pousser sur la branche avant de fermer Phase 9 et bumper en `0.8.0`. Phase 9 fermée 2026-05-03 sur les 4 bugs critiques fixés ; le reste passe en Phase 10.

