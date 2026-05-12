# Procédure de release

CanoPaname est distribué via [Obtainium](https://obtainium.imranr.dev/) qui pull directement les GitHub Releases du repo. La signature de l'APK est stable d'une version à l'autre — la perte du keystore prod casserait le canal de mise à jour pour tous les utilisateurs.

## Pré-requis (une fois)

1. **Générer le keystore prod** (le `.jks` ne doit jamais entrer dans le repo) :
   ```bash
   keytool -genkey -v -keystore canopaname-release.jks -keyalg RSA -keysize 2048 \
           -validity 10000 -alias canopaname
   ```
2. **Encoder en base64** pour le stocker en secret CI :
   ```bash
   base64 -w0 canopaname-release.jks > keystore.b64
   ```
3. **Créer 4 secrets GitHub** (Settings → Secrets and variables → Actions) :
   - `RELEASE_KEYSTORE_BASE64` — contenu de `keystore.b64`
   - `RELEASE_STORE_PASSWORD` — mot de passe du keystore
   - `RELEASE_KEY_ALIAS` — alias (ex: `canopaname`)
   - `RELEASE_KEY_PASSWORD` — mot de passe de l'alias
4. **Sauvegarder le keystore hors-machine** (USB chiffré, gestionnaire de mots de passe avec attachements, etc.). Perte = plus jamais d'update sans uninstall pour les utilisateurs.

Pour les builds release locaux, renseigner `local.properties` (gitignored) :
```properties
RELEASE_STORE_FILE=/chemin/absolu/vers/canopaname-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=canopaname
RELEASE_KEY_PASSWORD=...
```

## Checklist release

1. [ ] Vérifier `main` propre, CI `build.yml` verte sur le dernier commit.
2. [ ] Bump `versionCode` et `versionName` dans `app/build.gradle.kts`. Scheme : `versionCode = major*10000 + minor*1000 + patch` (ex: v1.0.0 → 10000, v1.0.1 → 10001, v1.1.0 → 11000, v1.2.0 → 12000, v2.0.0 → 20000).
3. [ ] Mettre à jour `CHANGELOG.md` (nouvelle section `[X.Y.Z] - YYYY-MM-DD`).
4. [ ] Smoke test local APK signé prod : `./gradlew assembleRelease` puis `adb install -r app/build/outputs/apk/release/canopaname-v*-release.apk`. Vérifier carte, capture, redémarrage app, export+import ZIP.
5. [ ] Commit `chore: release vX.Y.Z`, push.
6. [ ] Tag : `git tag vX.Y.Z && git push origin vX.Y.Z`.
7. [ ] Surveiller `.github/workflows/release.yml` (Actions tab ou `gh run watch`).
8. [ ] Tester l'APK draft attaché à la Release sur device fresh (`apksigner verify --print-certs` doit montrer le bon CN, pas `Android Debug`).
9. [ ] Publier la Release (depuis draft → Publish dans l'UI GitHub).

## Vérifier la signature d'un APK

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
    canopaname-v1.0.0-release.apk
```

Le SHA-256 du certificat doit correspondre à celui figé dans `.github/release-template.md`. Une discordance = clé compromise ou substituée → ne pas installer.
