# Arbres

App Android pour partir à la chasse aux arbres remarquables de Paris, collectionner les espèces, et redécouvrir la ville par sa canopée.

Inspirée de Pokémon GO et Space Invaders, mais avec de **vrais** arbres : ceux du dataset [OpenData Paris « les-arbres »](https://opendata.paris.fr/explore/dataset/les-arbres/) (~210 000 arbres géolocalisés, dont ~200 « remarquables »).

> **Statut** : version 0.6 (phases 0 → 5.5 livrées). Usage personnel + family & friends. Repo privé, pas de version publique prévue à court terme.

## Ce que ça fait aujourd'hui

- **Carte** plein écran : 217 855 arbres parisiens en clusters MapLibre, style OpenFreeMap, géoloc native sans Google Play Services.
- **Capture** par proximité GPS (< 30 m), photo prise depuis l'app, stockée localement. Pin gris → vert quand l'espèce est découverte ; orange pour les remarquables capturés.
- **Arboretum** : Pokédex à 907 espèces. Cards par espèce avec count Paris, photos perso, première capture. Fiche enrichie (résumé Wikipedia FR, stats Paris, mini-carte filtrée).
- **Pokédex remarquables** dédié, avec qualification + résumé + cultivar + lien fiche PDF Ville de Paris (169/907).
- **Saisonnalité** : 4 saisons calendaires fixes, pill `SeasonSelector`, mode archive read-only avec bandeau plein-écran. Les captures s'accumulent dans le bucket de leur saison sur toutes les années.
- **Profil** : stats (1re capture, # espèces, # remarquables, total captures), segmented Global / Saison vive.
- **Badges & succès** : 15 badges en 6 catégories (Découverte, Botanique, Géographie, Remarquables, Saisons, Démesure), évalués à la volée depuis les captures, pas de table dédiée.
- **Backup local** : export ZIP via SAF (captures + photos + métadonnées), import idempotent (dédup `(arbreId, timestamp)`). Le seul moyen de survivre à un changement de téléphone.
- **Onboarding** : un écran d'accueil scrollable avec rationale GPS expliqué au bon moment ; rejouable depuis le Profil (« Comment jouer »).

## Pourquoi

- Les arbres sont déjà là, déjà géolocalisés, déjà documentés (espèce, hauteur, circonférence, date de plantation, statut remarquable).
- Pas besoin de licence IP, pas besoin de serveur de spawning, pas besoin de Google Play Services.
- Spécificité jolie vs. Pokémon : la **saisonnalité réelle** (un platane en mai ≠ en novembre) multiplie naturellement le contenu.

## Pile technique

Kotlin + Jetpack Compose + Material 3, MapLibre Native Android 11.11.0, Room v2 (~907 espèces, dataset pré-cuit dans `assets/`), DataStore Preferences, Gradle Kotlin DSL + version catalog. Police Fraunces SemiBold sur les niveaux display/headline. Stockage **strictement local** : pas de cloud, pas d'auth, pas de service tiers au runtime. Cible Android 8.0+ (API 26), pensé pour tourner sur GrapheneOS sans Google Play Services.

## Démarrage rapide

Pré-requis : Android Studio (Koala ou plus récent) ou Gradle ≥ 8.10 + Android SDK (API 35) + JDK 21 (le JDK 21 bundlé dans Android Studio sous `/opt/android-studio/jbr` fait l'affaire — cf. [`CLAUDE.md`](CLAUDE.md) pour la note JDK 25 incompatible).

```bash
# Bootstrap du wrapper Gradle (une seule fois)
gradle wrapper

# Build et install sur appareil branché en ADB
JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug
```

Voir [`CLAUDE.md`](CLAUDE.md) pour les commandes détaillées, la structure du code et les conventions ; [`ROADMAP.md`](ROADMAP.md) pour l'historique des phases.

## Build release signé

Le release build cherche un keystore référencé dans `local.properties` (jamais committé). Sans clé renseignée, l'APK release est signé avec la clé debug — utile pour smoke-tester `isMinifyEnabled`. Procédure complète dans [`CLAUDE.md`](CLAUDE.md) section *Build release signé*.

## Données

Source : OpenData Ville de Paris, licence ODbL.
- `les-arbres` — arbres du domaine public (~210 k entrées, mis à jour ~hebdo).
- `arbresremarquablesparis` — sous-ensemble labellisé « remarquable » (~200 entrées).

Les fichiers téléchargés ne sont pas committés (`.gitignore`). Le pipeline de génération des assets (Room DB + GeoJSON + JSON espèces + cache Wikipedia FR) vit dans `tools/build_dataset.py`.

## Licence

[MIT](LICENSE).
