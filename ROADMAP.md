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

**Phase 12 — Hot fixes post-tests live ✅** : 6 fixes UX/visuels remontés des sessions live device — tint hero `WelcomeScreen` `or` → `remarquableOrange`, retours-ligne `\n` dans splash tips + `maxLines 4`, célébration nouvelle espèce `Icons.Park` → `ic_arbre_canonical`, `filterGeoJsonBySpecies` skip remarquables non capturés (carte principale intacte), bouton « Fiche espèce » conditionné `sk in capturedSpecies` dans modal détail, recompress JPEG long-edge 1600 / quality 85 + rotation EXIF pixel-side (~10 MB → ~500 KB).

**Phase 13A — Hardening code & assets ✅** : 8 sous-phases C1→C8 livrées en cascade. C1 manifest privacy (`allowBackup=false`, `network_security_config.xml` HTTPS-only, suppression `ACCESS_COARSE_LOCATION`). C2 strip EXIF post-capture. C3 hardening `BackupImporter` contre zip bombs et path traversal (caps 10/500 Mo, 10000 entries, magic bytes JPEG, refus `..`/`\\`). C4 strip `Log.v/d/i` en release via ProGuard. C5 crash explicite `build_dataset.py` si `species-index.json` manquant. C6 retrait `fallbackToDestructiveMigration` + `exportSchema=true` + schema 2.json committé. C7 `photoPath` absolu → basename + `MIGRATION_2_3` + helper `Capture.resolvedFile(context)`. C8 pass d'épuration commentaires Kotlin (~250 commentaires retirés sur 8 fichiers non-triviaux).

**Phase 13B — Identité & rewrite history ✅** : repo renommé `m4xim1nus/Arbres` → `m4xim1nus/CanoPaname`, email projet dédié `canopaname@pm.me` ajouté/vérifié sur GitHub (« Keep emails private » coché), `git filter-repo` réécrivant 40 commits (`mlv@spirtech.com` → `canopaname@pm.me`), backup mirror `/tmp/arbre-app-backup-20260505.git`, `.gitignore` étendu (caches Python, `manual_tests/`, `.claude/`), `manual_tests/` déplacé hors repo, `TESTS.md` retiré, wrapper Gradle committé, `rootProject.name = "canopaname"`, User-Agent `build_dataset.py` aligné, `gitleaks` 0 finding.

**Phase 13C — Pipeline release & passage public ✅** : keystore prod `canopaname-release.jks` généré hors-machine (cert `CN=CanoPaname, O=CanoPaname, C=FR`, SHA-256 figé `a683f199…da3334`), 4 secrets GitHub (`RELEASE_KEYSTORE_BASE64` + 3 passwords/alias), `.github/workflows/build.yml` (assembleDebug + test + detekt + lint sur push/PR) et `release.yml` (décodage keystore, cache `tools/.wikipedia-cache/`, guard anti-debug-signing, SHA-256 attaché, Release draft auto sur tag `v*`), naming APK Obtainium (`canopaname-v${versionName}-${buildType}.apk`), `RELEASE.md` checklist, squelette fastlane fr-FR, ABI splits tranché non. Bump `versionCode = 10000` / `versionName = "1.0.0"`. Smoke device validé sur GrapheneOS, repo basculé public, tag `v1.0.0` pushé, release publiée (https://github.com/m4xim1nus/CanoPaname/releases/tag/v1.0.0), Obtainium détecte et installe v1.0.0 via le regex `canopaname-v.*-release\.apk$`. Plan d'audit source : [docs/audit-pre-public.md](docs/audit-pre-public.md).
