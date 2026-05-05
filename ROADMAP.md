# Roadmap

App perso, pas de calendrier engageant. Phases ordonnées du plus pragmatique au plus ambitieux. Tout est négociable. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé.

## Phases livrées

**Phase 0 — Scaffold ✅** : squelette Gradle/Kotlin/Compose, NavHost, MapLibre, Room, GeoJSON, icône, build/install GrapheneOS via ADB.

**Phase 1 — MVP « voir les arbres autour de moi » ✅** : 213 042 arbres réels de Paris affichés et tappables, style OpenFreeMap, géoloc native sans GMS, source GeoJSON clusterisée, hit-test à deux niveaux.

**Phase 1.5 — Polish carte ✅** : zoom auto + ModalBottomSheet, doc `docs/vision-jeu.md` posant la philosophie « Pokémon GO épuré ».

**Phase 2 — Capture et collection ✅** : capture photo + GPS + Room, MIGRATION_1_2 pour la table `capture`, Arboretum, découverte par espèce, pin orange remarquable capturé.

**Phase 2.5 — Profondeur Arboretum ✅** : cold start masqué (splash), fiche-espèce avec Wikipedia FR (528/907) et stats Paris, mini-carte filtrée, vue Pokédex, fiche enrichie remarquables (qualification + résumé + cultivar + lien fiche PDF Ville de Paris).

**Sprint H — Profil et premier badge ✅** : `ProfileScreen` accessible TopStart de la carte. Stats (1re capture, # espèces, # remarquables) + badge unique « Première capture ».

**Sprint I — Saisonnalité ✅** : 4 saisons calendaires fixes, `SeasonStore` singleton en mémoire, pill `SeasonSelector`, mode archive read-only, captures bucketées par saison sur toutes les années.

**Phase 3 — Revue graphique ✅** : icône launcher platane parisien, tokens couleur centralisés (`ArbresColors`), Fraunces SemiBold sur display/headline, splash cold-start animé Compose pur, tinting saisonnier discret.

**Phase 4 — Page Badges & succès dédiée ✅** : 15 `BadgeDef` en 6 catégories (Découverte, Botanique, Géographie, Remarquables, Saisons, Démesure), évalués à la volée par `BadgeEvaluator` (fonction pure, pas de table Room). Écran dédié accédé depuis le Profil.

**Phase 5 — Export / import (backup local) ✅** : ZIP via Storage Access Framework (`captures.json` + `photos/UUID.jpg` + `meta.json`), fusion additive idempotente via dédup `(arbreId, timestamp)`, photos manquantes comptées sans bloquer.

**Phase 5.5 — Onboarding ✅** : `OnboardingStore` (DataStore Preferences), `WelcomeScreen` scrollable avec rationale GPS au bon moment, NavHost `startDestination` conditionnelle, route `WELCOME_REPLAY` rejouable depuis le Profil.

**Phase 6 — Hygiène projet ✅** : doc alignée (CLAUDE/README), signing release configurée (`local.properties` + fallback debug), `.gitignore` à jour pour les keystores.

**Phase 7 — Texture sensorielle ✅** : `ArbresMotion` codifié (tokens d'animation), transitions de saison animées, climax du moment de capture (halo + cœur + binomial flottant), haptiques, empty states designés, iconographie remarquable inspirée des plaques officielles, onboarding animé.

**Phase 8 — Hygiène pré-release ✅** : bump versionCode/Name, nettoyage deps mortes (moshi/okhttp), tests unitaires `BadgeEvaluator` + `BackupImporter` (34 cas), detekt baseline, extraction `MapLayers.kt` / `MapOverlays.kt` depuis `MapScreen.kt`.

**Phase 9 — Build et tests manuels device ✅** : 1re session device prolongée sur GrapheneOS, 9 bugs trouvés et fixés (asset DB v0 → fallback destructif, carte non-interactive via pointerInput vide, splash bloqué, etc.). Tests JVM verts, detekt clean, R8 release OK.

**Phase 10 — Polish v1.0 (rebranding + fixes UX) ✅** : rebranding « Arbres » → **CanoPaname** (surface utilisateur uniquement, package `app.arbre` intact), splash cold-start figé via stratégie 2-passes (carte vide d'abord, freeze parsing GeoJSON masqué), cohérence iconographique platane partout, helper `Season.preposition` (« au printemps » / « en été »).

**Phase 10.5 — Polish post-smoke 2026-05 ✅** : 9 sous-groupes A→I traitant 11+ remarques de la session device du 2026-05-04. Refonte iconographie remarquables (plaque chanfreinée → disque orange + platane crème, contrat couleur orange stabilisé), fiche remarquable enrichie + parcours croisé (boutons « Fiche remarquable » / « Fiche espèce » conditionnés), `PhotoLightbox` plein écran, renommage `Pokédex → Catalogue` + tris (Arboretum par count Paris décroissant, Remarquables groupé par arrondissement avec sticky headers), splash cascade de mini-platanes en boucle + empty state Profil refondu + rotation carte bloquée, conditionnement à la capture (info masquée tant que non gagnée), polish + GPS first-launch via bridge MapLibre → `LocationProvider`, banque ~240 tips informatifs rotatifs sur le splash, coloration progressive des clusters carte (3 buckets gris/vert clair/vert foncé via `clusterProperty discovered_count`), alignement compteur splash avec carte (213 042). Bump `versionCode → 9` / `versionName → "0.9.0"`.

**Phase 11 — Préparation de la release v1.0.0 (11A+11B) ✅** : sous-phases livrées du plan d'audit pré-public (cf. [docs/audit-pre-public.md](docs/audit-pre-public.md)).
- **11A documentation publique** : refonte `README.md` v1.0 (3 screenshots, install Obtainium, permissions, FAQ, attributions), `CHANGELOG.md` Keep-a-Changelog `[1.0.0]`, `PRIVACY.md` ~200 mots tutoiement, `SECURITY.md` projet perso, `.github/release-template.md`, épuration `CLAUDE.md` + `ROADMAP.md` (408 → 144 lignes).
- **11B légal & attributions** : `app/src/main/assets/licenses/Fraunces-OFL.txt` (OFL 1.1 intégral), `NOTICE.md` racine, `app/src/main/assets/databases/ODbL-NOTICE.txt` (les-arbres + arbresremarquablesparis), mention « Source : Wikipédia FR · CC BY-SA 4.0 » sous le summary `WikipediaBlock`, `Routes.ABOUT` + `AboutScreen` accessible depuis le Profil, copyright `LICENSE` étoffé.

> **Reste à valider/faire avant tag** : (1) check device que MapLibre affiche bien le bouton attribution OSM/OpenFreeMap (déplacé en validation Phase 12) ; (2) recherche manuelle « CanoPaname » sur https://bases-marques.inpi.fr/ (déplacée en pré-requis Phase 13C, juste avant le passage public).

## Phase 12 — Hot fixes post-tests live

> 5 retours UX/visuels remontés après les sessions live device de la Phase 11. Périmètre volontairement chirurgical : aucun changement d'architecture, aucune migration Room, aucun nouveau package. On reste sur `main` directement, vu la trivialité des changements.

- [ ] **12.1** WelcomeScreen — passer le tint du platane circulaire `HeroLogo` de `arbresColors.or` à `arbresColors.remarquableOrange` (cohérence visuelle avec les pins remarquables capturés).
- [ ] **12.2** Splash tips — insérer un retour à la ligne entre les phrases des tips rotatifs : pass éditorial sur `tools/splash-tips-static.json` + adaptation des templates dans `tools/build_dataset.py:write_splash_tips()`, régénération de `app/src/main/assets/splash-tips.json`. Bump `maxLines = 3` → `maxLines = 4` dans `MapOverlays.kt:227-234` (marge pour les tips à 3 phrases).
- [ ] **12.3** Célébration nouvelle espèce — remplacer `Icons.Outlined.Park` (sapin Material) par `R.drawable.ic_arbre_canonical` (platane canonique tintable) dans `CelebrationHero` (`SpeciesDetailScreen.kt:287-298`). Cleanup import `androidx.compose.material.icons.outlined.Park` si devenu inutilisé.
- [ ] **12.4** Carte filtrée espèce — masquer les arbres remarquables non capturés. Étendre `filterGeoJsonBySpecies` (MapLayers.kt:156-187) avec un param `capturedRemarquables: Set<Long>` ; skipper les features `remarquable=true && id !in capturedRemarquables`. Adapter le call site `MapScreen.kt:354`. **Non-régression** : la carte principale (mode non filtré) doit continuer à montrer tous les remarquables (gris si non capturé, orange si capturé).
- [ ] **12.5** Modal détail arbre — gater le bouton « Fiche espèce » sur la capture de l'espèce. Côté `MapScreen`, passer `onSpeciesClick = null` à `ArbreDetailContent` si `arbre.speciesIndex !in capturedSpecies`. Réplique du pattern `RemarquableDetailScreen.kt:77-78,129-131`. Concerne en pratique les remarquables capturés dont l'espèce n'est pas par ailleurs débloquée.
- [x] **12.6** Capture photos : recompress JPEG long-edge 1600 / quality 85 + rotation EXIF appliquée pixel-side, ~10 MB → ~500 KB par capture (×15-25). `CaptureLauncher.kt` + dépendance `androidx.exifinterface 1.3.7`. Validé device (Pixel 9a).

### Validation device Phase 12

- [ ] Smoke des 5 fixes ci-dessus (cf. plan d'implémentation, vérification end-to-end).
- [ ] **P0 hérité 11B** : confirmer que MapLibre affiche bien le bouton attribution OSM/OpenFreeMap (par défaut `uiSettings.isAttributionEnabled = true`). Si non visible, le forcer.

---

### 🛑 Cut « stop fix » — fin des tests live, gel de la surface code applicatif

> **Au moment où cette ligne est cochée** : les 5 fixes Phase 12 sont mergés sur `main`, le smoke device est OK. À partir d'ici, plus aucun commit qui touche du code applicatif sauf s'il vient de la checklist Phase 13 ci-dessous, ou exception bug critique.
>
> Rappel pour Claude Code : si on relance une session après ce point et qu'un nouveau fix UX/feature est demandé, **rappeler ce cut au user et demander confirmation** avant de toucher au code applicatif. Un fix de fond qui contourne le hardening 13A est un risque (ex. réintroduire `photoPath` absolu, sauter l'EXIF strip, oublier la migration).

- [ ] **🛑 Cut acté** : tests live et hot fixes Phase 12 terminés, on entre dans les sous-phases bloquantes.

---

## Phase 13 — Hardening, identité, release

> Plan détaillé issu de l'audit pré-public (équipe de 7 sous-agents — privacy, sécurité, hygiène repo, doc, légal, migration, release). Détail complet des findings, drafts et commandes : [docs/audit-pre-public.md](docs/audit-pre-public.md). Charge totale ~2,5 j sur ce qui reste. Cut retenu : **P0 + P1 + P2 triviaux**.

### Stratégie d'exécution & git

Les sous-phases sont **ordonnées chronologiquement** (pas par axe de l'audit). Logique : 13A, 13B, 13C sont *bloquantes* l'une de l'autre — elles touchent à la surface code, à l'historique git, et au tag final. Un fix UX qui arrive dans cette fenêtre risque de réintroduire un trou de sécurité ou de casser le rewrite history.

**Branches recommandées** : `phase-13/hardening`, `phase-13/identity`, `phase-13/release` — branches courtes mergées séquentiellement dans `main`.

### 13A — Hardening code & assets *(1 j, après le cut)*

#### Privacy & sécurité Manifest
- [ ] **P1** `android:allowBackup="false"` dans `AndroidManifest.xml:16` (cohérent avec promesse "tout reste local"). Vider ou supprimer `backup_rules.xml` / `data_extraction_rules.xml`.
- [ ] **P1** Ajouter `app/src/main/res/xml/network_security_config.xml` HTTPS-only + référencer via `android:networkSecurityConfig` dans `AndroidManifest.xml:14`.
- [ ] **P2** Supprimer `<uses-permission ACCESS_COARSE_LOCATION>` (FINE l'inclut déjà sur API 31+, COARSE inutile vu `MAX_DISTANCE_M = 30`).

#### Capture & backup
- [ ] **P1** Strip EXIF (GPS, Make, Model, Software, ImageUniqueId) après chaque `TakePicture()` dans `CaptureLauncher.kt:107-118` via `androidx.exifinterface`.
- [ ] **P1** Hardener `BackupImporter.kt:80-104` : cap `MAX_PHOTO_BYTES = 10 MB`, `MAX_TOTAL_BYTES = 500 MB`, `entryCount ≤ 10000`, magic bytes JPEG `FF D8 FF`, refus `\\` et `..` dans basename.
- [ ] **P2** Strip `Log.i/v/d` en release via `proguard-rules.pro` (`-assumenosideeffects class android.util.Log { ... }`).

#### Migration & forward-compat
- [ ] **P0** Retirer `fallbackToDestructiveMigration()` de `ArbreDatabase.kt:64` — sinon wipe silencieux des captures à toute future migration foireuse.
- [ ] **P1** `exportSchema = true` dans `@Database`, KSP arg `room.schemaLocation` dans `app/build.gradle.kts`, committer `app/schemas/app.arbre.data.ArbreDatabase/2.json`.
- [ ] **P1** Migrer `photoPath` en basename relatif (`{uuid}.jpg`) — `MIGRATION_2_3` + helper `Capture.resolvedFile(context)` + adapter `PhotoThumbnail.decodeFile`. Évite la casse au device transfer / multi-user / debug↔release.
- [ ] **P1** Test `MigrationTestHelper` : `app/src/androidTest/java/app/arbre/data/MigrationTest.kt` couvrant `MIGRATION_1_2` et `MIGRATION_2_3`.
- [ ] **P2** `tools/build_dataset.py` crash explicite si `species-index.json` absent **et** `arbres-paris.db` existe déjà avec rows (au lieu du `[warn]` actuel).

#### Code source — pass épuration commentaires
- [ ] **P1** Pass d'épuration des commentaires Kotlin (~0,5 j) : relire les fichiers non-triviaux (`MapScreen`, `MapLayers`, `BackupImporter`, `BadgeEvaluator`, `CaptureLauncher`, `ArbreDatabase`, `SplashTipsController`, `SpeciesDetailScreen`) et appliquer la règle « garder uniquement ce qui justifie un *pourquoi* non-évident — supprimer tout ce qui décrit *quoi* ». Les marqueurs de phase (`// Phase 10.5 sous-groupe F`, `// Sprint I`) et les explications de cycle de dev partent. Garder les commentaires sur les contraintes cachées (thread MapLibre, ordinal Season persisté, contrat de format GeoJSON, etc.).

### 13B — Identité & rewrite history *(0,5 j, après 13A consolidé sur main)*

- [ ] **P0** Backup git complet : `git clone --mirror . /tmp/arbre-app-backup-$(date +%Y%m%d).git`.
- [ ] **P0** Renommer le repo GitHub `m4xim1nus/Arbres` → `m4xim1nus/CanoPaname` (Settings → General → Rename, action manuelle web). GitHub installe une redirection 301 automatique depuis l'ancien nom. Mettre à jour le remote local : `git remote set-url origin git@github.com:m4xim1nus/CanoPaname.git`. À faire **avant** le rewrite filter-repo.
- [ ] **P0** Préalable acté côté GitHub : `canopaname@pm.me` ajouté + vérifié dans Settings → Emails ; "Keep my email addresses private" coché (filet de sécurité bloquant le push d'un commit avec l'ancien email pro).
- [ ] **P0** `git filter-repo --email-callback` pour remplacer `mlv@spirtech.com` par `canopaname@pm.me` (40 commits affectés). Aussi en local : `git config user.email canopaname@pm.me` pour les futurs commits.
- [ ] **P0** Vérification post-rewrite : `git log --all --pretty=format:'%ae' | sort -u` ne contient plus que `canopaname@pm.me` + `noreply@anthropic.com`.
- [ ] **P1** Étendre `.gitignore` : `tools/.essences-cache/`, `tools/.remarquables-cache/`, `__pycache__/`, `*.pyc`, `manual_tests/`, `.claude/`.
- [ ] **P1** Déplacer `manual_tests/` (3,9 Mo screenshots dev) hors repo (`~/dev/arbre-app-private/manual_tests/`).
- [ ] **P1** Committer le wrapper Gradle : `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.
- [ ] **P1** Supprimer `TESTS.md` du repo (archive locale).
- [ ] **P2** Aligner `settings.gradle.kts` : `rootProject.name = "canopaname"`.
- [ ] **P3** Validation : `gitleaks detect --source .` retourne 0 finding.

### 13C — Pipeline release & passage public *(1 j, après 13B)*

- [ ] **Pré-requis** Recherche manuelle « CanoPaname » sur https://bases-marques.inpi.fr/ (5 min, hérité Phase 11B item P3).
- [ ] **P0** Générer keystore release prod (`keytool -genkey -v -keystore arbres-release.jks ...` cf. CLAUDE.md). Conserver hors-repo + hors-machine (perte = plus jamais d'update).
- [ ] **P0** Créer 4 secrets GitHub : `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- [ ] **P0** Créer `.github/workflows/release.yml` avec décodage keystore + **guard anti-debug-signing** (apksigner CN check, fail si `CN=Android Debug`) + génération SHA256. Draft annexe G du rapport d'audit.
- [ ] **P1** Créer `.github/workflows/build.yml` avec `assembleDebug + test + detekt + lint` sur push/PR (stub assets gitignored). Draft annexe F du rapport d'audit.
- [ ] **P1** Naming APK Obtainium : `applicationVariants.all { outputFileName = "canopaname-v\${versionName}-\${buildType}.apk" }` dans `app/build.gradle.kts`.
- [ ] **P1** Créer `RELEASE.md` (procédure pré-requis + checklist 10 étapes). Draft annexe H du rapport d'audit.
- [ ] **P1** Bump `versionCode = 10000`, `versionName = "1.0.0"` (scheme `major*10000 + minor*100 + patch`).
- [ ] **P1** **Smoke test manuel APK release signé prod sur device GrapheneOS** : carte (MapLibre), capture (Room insert + EXIF strip), redémarrage app (Room read + DataStore), export+import ZIP. Si OK → bon pour tag.
- [ ] **P1** Bascule visibilité GitHub : Settings → Make public.
- [ ] **P1** Tag : `git tag v1.0.0 && git push origin v1.0.0`. Vérifier que le workflow release se déclenche, produit l'APK draft.
- [ ] **P1** Tester l'APK draft sur device fresh, vérifier signature `apksigner verify --print-certs`. Publier la Release.
- [ ] **P1** Configurer Obtainium : URL repo `https://github.com/m4xim1nus/CanoPaname`, regex APK `canopaname-v.*-release\.apk$`, version detection par tag.
- [ ] **P2** Squelette `fastlane/metadata/android/fr-FR/{title,short_description,full_description,changelogs/}.txt` (option F-Droid future).
- [ ] **P3** ABI splits : trancher **non**, garder universel (59 Mo OK pour family & friends, simplifie Obtainium).
