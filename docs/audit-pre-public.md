# Audit pré-public — CanoPaname v1.0

> Audit conduit fin Phase 10.5, en préparation du **double passage public** (repo `arbre-app` ouvert sur GitHub + tag `v1.0.0` distribué via GitHub Releases pour Obtainium).
>
> Périmètre : risques liés au passage public (privacy, sécurité, réputation, légal, faisabilité release, pérennité données). Hors périmètre : UX, features, perfs (déjà couverts par les phases précédentes).
>
> Méthode : 7 sous-agents spécialisés, lancés en parallèle, read-only. Chacun a produit un rapport structuré avec items priorisés `P0 / P1 / P2 / P3` et effort `S / M / L`. Ce document est la consolidation.
>
> Calibrage validé en amont : strictness **pragmatique family & friends** (le hardening "stricte distribution publique" — cert pinning, chiffrement ZIP, responsible disclosure formel — est marqué P3).

---

## 1. Synthèse — verdict et risques majeurs

| Axe | Verdict | Risques saillants |
|---|---|---|
| Privacy utilisateur | hardening manquant | EXIF photos non strippé, `allowBackup=true` (Cloud Backup Google), pas d'écran "Confidentialité" in-app, pas de PRIVACY.md |
| Sécurité technique | propre mais 2 trous | pas de `network_security_config.xml`, `BackupImporter` ne valide ni taille ni magic bytes des photos importées (zip-bomb / fichier renommé) |
| Hygiène repo & identité | bonne, **1 risque P0** | email pro `mlv@spirtech.com` exposé sur 40 commits, à réécrire via `git filter-repo` |
| Documentation public | très pauvre | README périmé ("v0.6 privée"), pas de CHANGELOG, pas de PRIVACY.md, pas d'écran "À propos" in-app, screenshots à sélectionner |
| Légal & licences | **3 P0 obligations** | OFL Fraunces non embarqué (violation OFL §2), pas d'attribution ODbL OpenData Paris (§4.3), pas d'attribution CC BY-SA Wikipedia |
| Migration & forward-compat | **1 P0 data-loss** | `fallbackToDestructiveMigration()` actif → wipe silencieux des captures user à toute migration foireuse ; `photoPath` absolu casse le restore device-transfer ; `exportSchema=false` |
| Release pipeline | tout à construire | pas de CI Actions, pas de RELEASE.md, naming APK plat, **pas de garde anti-debug-signing en CI** (risque APK debug-signed publié silencieusement) |

**Charge totale Phase 11** : ~3-4 jours de travail effectif, sans incompressible. Aucun blocage architectural.

---

## 2. Items consolidés P0 / P1 / P2 / P3

### P0 — Bloquants absolus avant repo public ou tag v1.0.0

| # | Source | Item | Preuve | Effort |
|---|---|---|---|---|
| 1 | Repo F1 | **Réécrire l'historique git** — remplacer `mlv@spirtech.com` par `m4xim1nus@users.noreply.github.com` via `git filter-repo` (40 commits) | `git log --all --pretty=format:'%ae' \| sort -u` | M |
| 1bis | — | **Renommer le repo GitHub** `Arbres` → `CanoPaname` (action web Settings → Rename ; redirect 301 auto ; à faire avant le filter-repo) | manuel UI GitHub | XS |
| 2 | Légal F1 | **Embarquer OFL.txt** pour Fraunces dans `app/src/main/assets/licenses/Fraunces-OFL.txt` | `ls app/src/main/res/font/` (pas d'OFL) | S |
| 3 | Légal F2 | **Attribution ODbL OpenData Paris** — `NOTICE.md` racine + écran in-app + `assets/databases/ODbL-NOTICE.txt` | grep "ville de paris" UI = 0 hit | S |
| 4 | Légal F3 | **Attribution OSM/OpenFreeMap visible** sur la carte (vérifier `uiSettings.isAttributionEnabled` MapLibre, sinon forcer) | inspection `MapScreen.kt` | S |
| 5 | Légal F4 | **Attribution CC BY-SA Wikipedia** — ligne sous le summary dans `WikipediaBlock` (`SpeciesDetailScreen.kt:435-475`) avec lien CC BY-SA 4.0 | code visible | S |
| 6 | Migration F1 | **Retirer `fallbackToDestructiveMigration()`** dans `ArbreDatabase.kt:64` — sinon wipe silencieux des captures à la 1re migration foireuse | code visible | XS |
| 7 | Doc F1 | **Refondre README** pour public — statut v1.0, screenshots, install Obtainium, permissions, Privacy, FAQ, attributions | `README.md:1-60` | M |
| 8 | Doc F2 | **Créer CHANGELOG.md** Keep-a-Changelog avec `[1.0.0]` = synthèse phases livrées | absent | S |
| 9 | Doc F3 | **Créer PRIVACY.md** ~200 mots à la racine | absent | S |
| 10 | Release F2 | **Workflow `.github/workflows/release.yml`** avec décodage keystore + **guard anti-debug-signing** (apksigner CN check) | absent | S |
| 11 | Release F5 | **Créer secrets GitHub** : `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` | manuel GitHub UI | S |

### P1 — Must-fix avant tag v1.0.0

| # | Source | Item | Effort |
|---|---|---|---|
| P1-01 | Privacy F1 | Strip EXIF (GPS, Make, Model, Software) après chaque `TakePicture()` dans `CaptureLauncher.kt` via `androidx.exifinterface` | S |
| P1-02 | Privacy F2 / Sécu F3 / Migration F8 | `android:allowBackup="false"` dans `AndroidManifest.xml` (cohérent avec promesse "tout reste local" du WelcomeScreen) | S |
| P1-03 | Sécu F1 | `network_security_config.xml` HTTPS-only, référencé dans `AndroidManifest.xml:14` | S |
| P1-04 | Sécu F2 | Hardener `BackupImporter.kt` — cap par-photo 10 Mo, total 500 Mo, entryCount 10 k, magic bytes JPEG `FF D8 FF`, refus `\\` et `..` dans basename | S |
| P1-05 | Repo F2 | Déplacer `manual_tests/` hors repo (3,9 Mo screenshots dev) avant push public | S |
| P1-06 | Repo F3 | Étendre `.gitignore` : `tools/.essences-cache/`, `tools/.remarquables-cache/`, `__pycache__/`, `*.pyc`, `manual_tests/`, `.claude/` | XS |
| P1-07 | Repo F4 | Committer le wrapper Gradle (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`) | XS |
| P1-08 | Repo F7 | Supprimer `TESTS.md` du repo (archiver localement) | XS |
| P1-09 | Repo F7 | Épurer `CLAUDE.md` (garder le nom — auto-load Claude Code, dev continue assisté) : supprimer `## Setup (déjà fait sur cette machine)`, neutraliser les marqueurs « Phase 10.5 sous-groupe X », ajouter préambule 2 lignes | M |
| P1-09b | (nouveau) | Pass d'épuration des commentaires Kotlin sur fichiers non-triviaux (MapScreen, MapLayers, BackupImporter, BadgeEvaluator, CaptureLauncher, ArbreDatabase, SplashTipsController, SpeciesDetailScreen) : virer marqueurs de phase et explications de cycle de dev, garder uniquement les *pourquoi* non-évidents | M |
| P1-10 | Repo F7 | Épurer `ROADMAP.md` (`Idées en vrac` → archive locale, condenser Phase 10.5 et Bugs Phase 9, 308 → ~80 lignes) | S |
| P1-11 | Doc F6 | Ajouter `Routes.ABOUT` + `AboutScreen` Compose accessible depuis `ProfileScreen.kt` (versions, attributions, licences tierces, lien repo) | M |
| P1-12 | Doc F7 | `.github/release-template.md` (highlights + permissions + checksum + lien Obtainium) | S |
| P1-13 | Légal F6 | Compléter `NOTICE.md` avec libs Apache-2.0 (AndroidX/Compose/Kotlin/Room/DataStore), MapLibre BSD-2, org.json | S |
| P1-14 | Migration F2 | Migrer `photoPath` en basename relatif (`{uuid}.jpg`) — `MIGRATION_2_3` + helper `Capture.resolvedFile(context)` + adapter `PhotoThumbnail.decodeFile` | M |
| P1-15 | Migration F3 | `exportSchema = true` dans `@Database`, KSP arg `room.schemaLocation`, committer `app/schemas/app.arbre.data.ArbreDatabase/2.json` | S |
| P1-16 | Migration F4 | Test instrumenté `app/src/androidTest/java/app/arbre/data/MigrationTest.kt` avec `MigrationTestHelper` couvrant `MIGRATION_1_2` (et `MIGRATION_2_3` si fait) | M |
| P1-17 | Release F1 | Naming APK Obtainium-friendly via `applicationVariants.all { outputFileName = "canopaname-v\${versionName}-\${buildType}.apk" }` | S |
| P1-18 | Release F3 | Créer `RELEASE.md` (procédure pré-requis + checklist 10 étapes) | S |
| P1-19 | Release F4 | SHA256 généré et uploadé en asset release dans `release.yml` | XS |
| P1-20 | Release F10 / Migration F9 | Bump `versionCode = 10000`, `versionName = "1.0.0"` au moment du tag (scheme `major*10000 + minor*100 + patch`) | XS |
| P1-21 | Release F9 | **Smoke test manuel APK release signé prod** sur device avant tag v1.0.0 (carte, capture, export+import ZIP, badges) | M |
| P1-22 | Release F2 | `.github/workflows/build.yml` — `assembleDebug` + `test` + `detekt` + `lint` sur PR, avec stub assets gitignored | S |

### P2 — Nice-to-have avant ou peu après v1.0.0

| # | Source | Item | Effort |
|---|---|---|---|
| P2-01 | Privacy F5 | Supprimer `<uses-permission ACCESS_COARSE_LOCATION>` (FINE l'inclut sur API 31+, COARSE inutile vu MAX_DISTANCE_M=30) | XS |
| P2-02 | Privacy F6 | Strip `Log.i/v/d` en release via `proguard-rules.pro` `-assumenosideeffects` | XS |
| P2-03 | Sécu F4 | Smoke release après bumps R8/proguard (couvert par P1-21) | — |
| P2-04 | Repo F8 | `rootProject.name = "canopaname"` dans `settings.gradle.kts` (cosmétique, package `app.arbre` reste pour stabilité `applicationId`) | XS |
| P2-05 | Repo F9 | Ajouter `.claude/` au `.gitignore` | XS |
| P2-06 | Doc F4 | `SECURITY.md` minimaliste ~8 lignes (contact + scope perso) | XS |
| P2-07 | Doc F5 | Note "no PR externe" en bas de README (pas de CONTRIBUTING.md séparé) | XS |
| P2-08 | Doc F2 | Sélectionner 3-4 screenshots `manual_tests/20260504/` → `docs/screenshots/` (vérifier coords GPS visibles ne pointent pas le domicile) | S |
| P2-09 | Légal F5 | Étoffer copyright `LICENSE` : `Copyright (c) 2026 m4xim1nus (https://github.com/m4xim1nus)` | XS |
| P2-10 | Légal F9 | `PRIVACY.md` minimaliste (couvert par P0 #9) — pré-requis F-Droid | — |
| P2-11 | Migration F5 | `tools/build_dataset.py` crash si `species-index.json` absent et DB existe (au lieu du `[warn]` actuel) | XS |
| P2-12 | Release F6 | Squelette `fastlane/metadata/android/fr-FR/{title,short_description,full_description,changelogs/}.txt` (option F-Droid future) | S |

### P3 — Si distribution s'élargit hors family & friends

| # | Source | Item |
|---|---|---|
| P3-01 | Privacy F7 | Chiffrement ZIP par passphrase (AES-GCM) — over-engineered pour la cible |
| P3-02 | Sécu F5 | Whitelist `Uri.scheme in {https,http}` avant `startActivity` dans `SpeciesDetailScreen.kt` (défense en profondeur) |
| P3-03 | Migration F6 | Commentaire `// ORDINAL PERSISTÉ` dans `Season.kt` |
| P3-04 | Migration F7 | Note de contrat "path versionné" dans `BackupModels.kt` |
| P3-05 | Release F7 | ABI splits — **trancher non**, garder universel |
| P3-06 | Release F8 | Reproducible builds — aspirationnel |
| P3-07 | Légal F8 | Recherche manuelle INPI bases-marques.inpi.fr "CanoPaname" (5 min) |

---

## 3. Plan d'exécution Phase 11 — sous-phases ordonnées chronologiquement

> **Important** : l'ordre ci-dessous est **temporel**, pas thématique. Les sous-phases A et B sont parallélisables avec une dernière journée de tests live device ; un **🛑 cut "stop fix"** matérialise la fin des fix de fond avant que les sous-phases bloquantes (C, D, E) ne touchent à la surface code, à l'historique git et au tag.
>
> **Branches** : `main` toujours mergeable. `live-test/v1-fixes` pour les petits fix issus des tests, mergés au fil de l'eau. `phase-11/docs`, `phase-11/legal`, `phase-11/hardening`, `phase-11/identity` pour les sous-phases.

### Phase 11A — Documentation publique parallélisable *(0,5 j, en parallèle des tests live)*
**Objectif** : tous les `.md` user-facing prêts. Aucun conflit possible avec d'éventuels fix UX.

1. Refonte `README.md` (P0 #7). Squelette en annexe.
2. `CHANGELOG.md` Keep-a-Changelog avec `[1.0.0]` (P0 #8). Draft en annexe.
3. `PRIVACY.md` ~200 mots tutoiement (P0 #9). Draft en annexe.
4. Épurer `CLAUDE.md` (garder le nom pour auto-load Claude Code) + préambule 2 lignes (P1-09).
5. Épuration `ROADMAP.md` (P1-10).
6. Sélection screenshots vers `docs/screenshots/` (P2-08).
7. `SECURITY.md` ~8 lignes (P2-06).
8. `.github/release-template.md` (P1-12). Draft en annexe.

### Phase 11B — Légal & attributions parallélisable *(0,5 j, en parallèle des tests live)*
**Objectif** : toutes les obligations OFL / ODbL / CC BY-SA / BSD-2 satisfaites. Ces ajouts (NOTICE.md, OFL.txt, AboutScreen) n'entrent pas en conflit avec des fix UX.

1. `app/src/main/assets/licenses/Fraunces-OFL.txt` (P0 #2).
2. `app/src/main/assets/databases/ODbL-NOTICE.txt` (P0 #3).
3. `NOTICE.md` racine (P0 #3 + P1-13). Draft en annexe.
4. `Routes.ABOUT` + `AboutScreen` Compose (P1-11) avec attribution complète (Ville de Paris ODbL, OSM, OpenFreeMap, Fraunces OFL, MapLibre BSD-2, Wikipedia CC BY-SA, AndroidX/Compose/Kotlin Apache-2.0).
5. `WikipediaBlock` : ajouter ligne "Source : Wikipedia FR · CC BY-SA 4.0" (P0 #5).
6. Vérifier `uiSettings.isAttributionEnabled = true` sur `MapScreen` (P0 #4) — par défaut `true` mais à confirmer device.
7. (Optionnel P3) recherche INPI "CanoPaname" (P3-07).

---

### 🛑 Cut "stop fix" — fin des tests live, gel de la surface code applicatif

À l'issue des tests live device : tous les petits fix mergés dans `main`, plus aucun changement applicatif sauf items Phase 11 ou bugs critiques. Le but : éviter qu'un fix UX qui arrive après le hardening 11C ne réintroduise un trou de sécurité (ex. `photoPath` absolu, oubli EXIF strip, oubli migration).

---

### Phase 11C — Hardening code & assets *(1 j, après le cut)*
**Objectif** : tous les correctifs sécurité + migration + pass commentaires sur le code Kotlin/Manifest/Gradle.

1. Privacy / sécurité manifeste : `allowBackup=false` (P1-02), supprimer `ACCESS_COARSE_LOCATION` (P2-01), `network_security_config.xml` HTTPS-only (P1-03).
2. Strip EXIF dans `CaptureLauncher.kt` (P1-01).
3. Hardening `BackupImporter.kt` (P1-04).
4. Strip logs ProGuard (P2-02).
5. Migration code :
   - Retirer `fallbackToDestructiveMigration()` (P0 #6).
   - `exportSchema = true` + KSP arg + commit `schemas/2.json` (P1-15).
   - `MIGRATION_2_3` + helper `Capture.resolvedFile()` (P1-14).
   - `MigrationTest.kt` androidTest (P1-16).
6. **Pass d'épuration des commentaires Kotlin** sur fichiers non-triviaux (P1-09b). Volontairement après le hardening pour ne pas masquer un commentaire utile sur du code qui va changer.

### Phase 11D — Identité & rewrite history *(0,5 j, après 11C consolidé sur main)*
**Objectif** : que le repo, à l'instant t où il devient public, ne contienne ni email pro ni artefacts dev privés, et porte le bon nom.

1. Backup git complet (`git clone --mirror` vers `/tmp`).
2. **Rename GitHub** `Arbres` vers `CanoPaname` (action UI web), puis `git remote set-url origin git@github.com:m4xim1nus/CanoPaname.git`. Redirect 301 auto sur l'ancien nom.
3. `git filter-repo --email-callback` sur `mlv@spirtech.com` vers alias `m4xim1nus@users.noreply.github.com`.
4. Vérification post-rewrite : `git log --all --pretty=format:'%ae' | sort -u`.
5. Étendre `.gitignore` (P1-06).
6. Déplacer `manual_tests/` hors repo (P1-05).
7. Supprimer `TESTS.md` (P1-08).
8. Committer wrapper Gradle (P1-07).
9. `rootProject.name = "canopaname"` (P2-04).
10. `gitleaks detect --source .` ceinture-bretelles.

**Sortie** : repo prêt à être basculé public côté contenu, mais encore privé.

### Phase 11E — Pipeline release & passage public *(1 j)*
**Objectif** : un tag git produit un APK signé prod, vérifié, publié.

1. Génération keystore release prod (`keytool -genkey ... arbres-release.jks`), conservation hors-repo + hors-machine.
2. Encodage base64 → secrets GitHub `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (P0 #11).
3. `applicationVariants` naming APK Obtainium (P1-17).
4. `.github/workflows/build.yml` (P1-22). Draft en annexe.
5. `.github/workflows/release.yml` avec décodage keystore + **guard anti-debug-signing** + SHA256 (P0 #10, P1-19). Draft en annexe.
6. `RELEASE.md` procédure (P1-18). Draft en annexe.
7. Bump `versionCode = 10000`, `versionName = "1.0.0"` (P1-20).
8. **Smoke test manuel APK release signé** sur device GrapheneOS (P1-21).
9. Bascule visibilité GitHub : Settings → Make public.
10. Commit final + tag `v1.0.0` + push tag → workflow `release.yml` se déclenche → APK + SHA256 en draft Release.
11. Test draft : install via APK direct sur device fresh, vérifier signature `apksigner verify --print-certs`.
12. Publier la Release.
13. Tester Obtainium pour configurer `URL repo + regex APK : canopaname-v.*-release\.apk$`.

---

## 4. Annexes — drafts à reprendre tels quels

### A. Commandes git filter-repo

```bash
# Pré-requis : pipx install git-filter-repo

# 1. Backup
git clone --mirror /home/max/dev/arbre-app /tmp/arbre-app-backup-$(date +%Y%m%d).git

# 2. Travail depuis clone frais (recommandé filter-repo)
git clone /home/max/dev/arbre-app /tmp/arbre-app-rewrite
cd /tmp/arbre-app-rewrite

# 3. Réécriture
git filter-repo --email-callback '
  if email == b"mlv@spirtech.com":
    return b"m4xim1nus@users.noreply.github.com"
  return email
'

# 4. Vérification
git log --all --pretty=format:'%ae' | sort -u
# Attendu : m4xim1nus@users.noreply.github.com + noreply@anthropic.com

# 5. Force-push après reconnexion remote (repo encore privé à ce stade)
git remote add public git@github.com:m4xim1nus/canopaname.git
git push public --all
git push public --tags
```

### B. NOTICE.md (draft complet)

```
# CanoPaname — Third-Party Notices

CanoPaname is licensed under the MIT License (see LICENSE).
This application incorporates third-party works under separate licenses.

## Data

### Trees of Paris
The embedded tree dataset is derived from:
- "Les arbres" — © Ville de Paris, OpenData Paris
  https://opendata.paris.fr/explore/dataset/les-arbres/
- "Les arbres remarquables" — © Ville de Paris, OpenData Paris
  https://opendata.paris.fr/explore/dataset/arbresremarquablesparis/

Both datasets are licensed under the Open Database License (ODbL) v1.0:
https://opendatacommons.org/licenses/odbl/1-0/

The bundled SQLite database (assets/databases/arbres-paris.db) is a
Derivative Database under ODbL §4.4 and remains under ODbL. The source
CSVs are publicly available at the URLs above; the build script is
tools/build_dataset.py.

CanoPaname is an independent project and is not affiliated with the
Ville de Paris.

### Map tiles
- OpenFreeMap (https://openfreemap.org) — MIT, attribution recommended
- OpenStreetMap data — © OpenStreetMap contributors, ODbL
  https://www.openstreetmap.org/copyright

### Species information
Species summaries are extracted from the French Wikipedia (REST summary
API), contributed by Wikipedia authors, licensed under CC BY-SA 4.0:
https://creativecommons.org/licenses/by-sa/4.0/

## Fonts

### Fraunces SemiBold
Copyright 2020 The Fraunces Project Authors
(https://github.com/undercasetype/Fraunces) with Reserved Font Name
"Fraunces". Designed by Phaedra Charles and Flavia Zimbardi
(Undercase Type).
Licensed under the SIL Open Font License v1.1.
Full text: assets/licenses/Fraunces-OFL.txt

## Libraries

- MapLibre Native Android SDK 11.11.0 — BSD-2-Clause
  © MapLibre contributors — https://github.com/maplibre/maplibre-native
- AndroidX, Jetpack Compose, Material 3 — Apache-2.0 — © Google LLC
- Kotlin / kotlinx.coroutines — Apache-2.0 — © JetBrains s.r.o.
- Room — Apache-2.0 — © Google LLC
- DataStore Preferences — Apache-2.0 — © Google LLC
- org.json — JSON License — © 2002 JSON.org
```

### C. PRIVACY.md (draft complet)

```
# Confidentialité

CanoPaname est une application 100 % locale. Tout ce que tu fais — captures,
photos, progression, badges — reste sur ton téléphone.

## Ce qui est stocké, et où
- **Captures** (espèce, position, date) : base SQLite privée à l'app.
- **Photos** : `Android/data/app.arbre/files/captures/`, supprimées à la
  désinstallation.
- **Préférences** : DataStore privé (onboarding vu, intro splash vue).

## Ce qui n'est jamais fait
- Aucun envoi vers un serveur, aucune télémétrie, aucun analytics.
- Aucun compte, aucune authentification.
- Aucun service Google requis (pensé pour GrapheneOS).

## Connexions réseau
- **Tuiles cartographiques** : `tiles.openfreemap.org` (OpenStreetMap, sans
  identifiant). C'est le seul appel réseau au runtime.
- Le dataset des arbres et les fiches Wikipedia FR sont **embarqués dans
  l'APK** au build, pas téléchargés.

## Permissions Android demandées
- **Position fine** : mesurer la distance à un arbre (< 30 m) pour autoriser
  la capture. Jamais envoyée.
- **Caméra** : photographier l'arbre capturé.
- **Vibrer** : retour haptique court à la capture.

## Sauvegarde
Tu peux exporter toutes tes captures en ZIP via Profil → Sauvegarde. Le
fichier vit sur ton stockage choisi via Storage Access Framework. Le ZIP
n'est pas chiffré — choisis un cloud chiffré (Cryptomator, Tresorit) si tu
veux protéger ce backup.

## Contact
Question ou bug : ouvre une issue sur le repo GitHub.
```

### D. README — squelette des sections

```
# CanoPaname

> Pokédex botanique des arbres de Paris. App Android, single-player, 100 % local.

[Screenshot carte]  [Screenshot capture]  [Screenshot Arboretum]

## Ce que c'est
[3 lignes : 213 042 arbres, 907 espèces, 169 remarquables, captures GPS+photo, saisons, badges. Family & friends.]

## Installation (Android 8.0+)
- **Via Obtainium** (recommandé) : ajouter ce repo `https://github.com/<owner>/canopaname` dans Obtainium, choisir « GitHub ».
- **APK direct** : Releases → v1.0.0 → `canopaname-v1.0.0-release.apk`.
- **Vérifier la signature** : `apksigner verify --print-certs canopaname-v1.0.0-release.apk` — fingerprint SHA-256 publié dans la Release.

## Permissions
- **Position (fine)** : mesurer les < 30 m d'un arbre (capture). Jamais envoyée.
- **Caméra** : photographier l'arbre capturé. Photo stockée localement.
- **Vibrer** : retour haptique à la capture.

## Données et vie privée
Tout reste sur ton téléphone. Pas de cloud, pas de compte, pas de tracker. Détails dans [PRIVACY.md](PRIVACY.md).

## FAQ
- *Pourquoi pas le Play Store ?* — Pas de Google Play Services requis. GrapheneOS first.
- *Mes captures, je peux les exporter ?* — Profil → Sauvegarde → Exporter (ZIP).
- *Le dataset bouge ?* — Mis à jour aux releases majeures (re-cuit dans l'APK).

## Attributions
- Données arbres : Ville de Paris, OpenData (licence ODbL).
- Cartographie : OpenFreeMap, OpenStreetMap contributors (ODbL).
- Résumés d'espèces : Wikipedia FR (CC BY-SA 4.0).
- Police Fraunces : Undercase Type (OFL 1.1).
- Bibliothèques : MapLibre (BSD-2), Compose / Material 3 (Apache 2.0).
Détails dans [NOTICE.md](NOTICE.md).

## Licence et contributions
[MIT](LICENSE). Repo public pour transparence et Obtainium ; pas de PR externe acceptée à ce stade (app perso family & friends).

## Développement
Voir [DEVELOPING.md](DEVELOPING.md).
```

### E. CHANGELOG.md (draft entrée [1.0.0])

```
# Changelog
Format basé sur Keep-a-Changelog. Versions semver.

## [1.0.0] — 2026-XX-XX
### Ajouté
- Carte plein écran de 213 042 arbres parisiens (clusters MapLibre, OpenFreeMap).
- Capture par proximité GPS (< 30 m) + photo locale.
- Arboretum à 907 espèces avec fiche enrichie (Wikipedia FR, stats Paris, mini-carte filtrée).
- Pokédex remarquables dédié (169 fiches, lien fiche PDF Ville de Paris).
- 15 badges en 6 catégories, évalués depuis les captures.
- Saisonnalité 4 saisons, mode archive read-only.
- Profil avec stats Global / Saison vive.
- Export / import ZIP local (Storage Access Framework, dédup idempotent).
- Onboarding + écran « Comment jouer » rejouable.
- Splash cold start avec tips informatifs rotatifs.
- Coloration progressive des clusters carte selon découvertes.

### Privacy
- 100 % local. Aucune télémétrie, aucun compte, aucun service tiers au runtime.
```

### F. .github/workflows/build.yml (draft)

```yaml
name: Build & Test
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v3
      - name: Stub asset DB & GeoJSON (gitignored)
        run: |
          mkdir -p app/src/main/assets/databases
          touch app/src/main/assets/databases/arbres-paris.db
          touch app/src/main/assets/arbres-paris.geojson
      - run: ./gradlew assembleDebug test detekt lint --stacktrace
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: reports
          path: |
            app/build/reports/
            app/build/outputs/apk/debug/*.apk
          retention-days: 7
```

### G. .github/workflows/release.yml (draft avec guard anti-debug-signing)

```yaml
name: Release
on:
  push:
    tags: ['v*']

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v3

      - name: Decode keystore
        env:
          RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
        run: |
          echo "$RELEASE_KEYSTORE_BASE64" | base64 -d > arbres-release.jks
          test -s arbres-release.jks

      - name: Write local.properties
        env:
          STORE_PWD: ${{ secrets.RELEASE_STORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
          KEY_PWD: ${{ secrets.RELEASE_KEY_PASSWORD }}
        run: |
          {
            echo "RELEASE_STORE_FILE=arbres-release.jks"
            echo "RELEASE_STORE_PASSWORD=$STORE_PWD"
            echo "RELEASE_KEY_ALIAS=$KEY_ALIAS"
            echo "RELEASE_KEY_PASSWORD=$KEY_PWD"
          } > local.properties

      - name: Build dataset assets
        run: |
          python3 -m pip install --user requests
          python3 tools/build_dataset.py

      - run: ./gradlew assembleRelease --stacktrace

      - name: Verify signature is prod (not debug)
        run: |
          APK=$(ls app/build/outputs/apk/release/canopaname-v*-release.apk)
          $ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs "$APK" | tee certs.txt
          if grep -q "CN=Android Debug" certs.txt; then
            echo "::error::APK debug-signed, secrets non chargés"
            exit 1
          fi

      - name: Compute SHA256
        run: |
          cd app/build/outputs/apk/release
          for f in canopaname-v*-release.apk; do
            sha256sum "$f" > "$f.sha256"
          done

      - name: Cleanup secrets
        if: always()
        run: rm -f arbres-release.jks local.properties

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/outputs/apk/release/canopaname-v*-release.apk
            app/build/outputs/apk/release/canopaname-v*-release.apk.sha256
          draft: true
          generate_release_notes: true
          fail_on_unmatched_files: true
          body_path: .github/release-template.md
```

### H. RELEASE.md (draft)

```markdown
# Procédure de release

## Pré-requis (une fois)
1. Générer keystore prod (cf. DEVELOPING.md "Build release signé").
2. Encoder base64 : `base64 -w0 arbres-release.jks > keystore.b64`
3. Créer 4 secrets GitHub :
   - `RELEASE_KEYSTORE_BASE64` (contenu de `keystore.b64`)
   - `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
4. Sauvegarder le keystore hors-repo et hors-machine (perte = plus jamais d'update).

## Checklist release
1. [ ] Vérifier `main` propre, CI verte.
2. [ ] Bump `versionCode` et `versionName` dans `app/build.gradle.kts`. Scheme : `versionCode = major*10000 + minor*100 + patch`.
3. [ ] Mettre à jour `CHANGELOG.md` (section `[X.Y.Z] - YYYY-MM-DD`).
4. [ ] Commit `chore: release vX.Y.Z`, push.
5. [ ] Tag : `git tag vX.Y.Z && git push origin vX.Y.Z`.
6. [ ] Surveiller `.github/workflows/release.yml`.
7. [ ] Tester l'APK draft sur device.
8. [ ] Publier la Release.
9. [ ] Vérifier qu'Obtainium détecte la nouvelle version.
```

### I. .github/release-template.md

```markdown
## CanoPaname vX.Y.Z

[Copier l'entrée correspondante du CHANGELOG.md]

### Installation
- **Obtainium** : ce repo est suivi automatiquement, l'app se mettra à jour.
- **APK direct** : `canopaname-vX.Y.Z-release.apk` ci-dessous.

### Vérification de signature
SHA-256 du certificat de signature attendu :
```
<fingerprint à figer à v1.0.0>
```
Vérifier : `apksigner verify --print-certs canopaname-vX.Y.Z-release.apk`

### Compatibilité
Android 8.0+ (API 26). Pensé pour GrapheneOS, fonctionne sans Google Play Services.
```

### J. Pattern Obtainium

- **URL repo** : `https://github.com/<user>/canopaname`
- **Source** : `GitHub`
- **Regex APK filter** : `canopaname-v.*-release\.apk$`
- **Version detection** : `Standard Version Detection` (par tag)

---

## 5. Ce qui n'est PAS fait dans cet audit

Pour calibrer les attentes :

- **Pas d'audit UX/features/perfs** — couvert par phases précédentes (notamment Phase 9 et Phase 10.5).
- **Pas d'audit accessibilité** — pas dans le scope passage public, à considérer post v1 si demande des family & friends.
- **Pas de revue ligne par ligne du code Kotlin** — l'audit Sécurité a échantillonné les zones sensibles (BackupImporter, FileProvider, Manifest), pas l'intégralité du code applicatif.
- **Pas de pentest réseau effectif** — analyse statique uniquement (HTTPS, endpoints).
- **Pas de scan de vulnérabilités automatisé** — `gitleaks` recommandé en commande ceinture-bretelles avant publication, pas exécuté pendant l'audit.

---

## 6. Estimation effort total

| Sous-phase | Charge | Cumul | Track |
|---|---|---|---|
| 11A Documentation publique | 0,5 j | 0,5 j | parallèle tests live |
| 11B Légal & attributions | 0,5 j | 1 j | parallèle tests live |
| 🛑 Cut "stop fix" | — | — | — |
| 11C Hardening code (incl. pass commentaires Kotlin) | 1 j | 2 j | bloquant |
| 11D Identité & rewrite history | 0,5 j | 2,5 j | bloquant |
| 11E Pipeline release & passage public | 1 j | 3,5 j | bloquant |

**Note charge** : 11A + 11B en parallèle des tests live = ~0,5 j wall-clock chacun mais peuvent s'étaler sur plusieurs jours. Après le cut, 11C → 11D → 11E sont séquentielles, ~2,5 j d'affilée.

**Total : ~4 jours de travail effectif** réparti à la convenance, sans dépendance externe (sauf temps de provision des secrets GitHub UI et rename repo). Aucun blocage architectural identifié.

**Cut Phase 11 retenu** : tous les **P0 + P1** (33 items) + une sélection des **P2 triviaux** (XS/S, marginalement coûteux, gain de cohérence net : `ACCESS_COARSE_LOCATION`, `rootProject.name`, `SECURITY.md`, screenshots, copyright LICENSE, étendre `.gitignore`/`.claude/`, build_dataset.py crash). Les P2 lourds (FAQ extensive, fastlane F-Droid skeleton) et tous les P3 sont renvoyés à v1.0.1 ou v1.1.0 selon humeur. Calibrage : "v1.0 dont on n'a pas honte à 6 mois", pas "v1.0 expédiée".
