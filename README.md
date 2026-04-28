# Arbres

App Android pour partir à la chasse aux arbres remarquables de Paris, collectionner les espèces, et redécouvrir la ville par sa canopée.

Inspirée de Pokémon GO et Space Invaders, mais avec de **vrais** arbres : ceux du dataset [OpenData Paris « les-arbres »](https://opendata.paris.fr/explore/dataset/les-arbres/) (~210 000 arbres géolocalisés, dont ~200 « remarquables »).

> **Statut** : MVP en construction. Usage personnel + family & friends. Pas de version publique prévue à court terme.

## Pourquoi

- Les arbres sont déjà là, déjà géolocalisés, déjà documentés (espèce, hauteur, circonférence, date de plantation, statut remarquable).
- Pas besoin de licence IP, pas besoin de serveur de spawning, pas besoin de Google Play Services.
- Spécificité jolie vs. Pokémon : la **saisonnalité réelle** (un platane en mai ≠ en novembre) multiplie naturellement le contenu.

## Pile technique

Kotlin + Jetpack Compose + Material 3, MapLibre Native, Room, Gradle Kotlin DSL.
Cible Android 8.0+ (API 26), pensé pour tourner sur GrapheneOS sans Google Play Services.

## Démarrage rapide

Pré-requis : JDK 17+, Android Studio (Koala ou plus récent) ou Gradle ≥ 8.10 + Android SDK (API 35).

```bash
# Bootstrap du wrapper Gradle (une seule fois)
gradle wrapper

# Build et install sur appareil branché en ADB
./gradlew installDebug
```

Voir [`CLAUDE.md`](CLAUDE.md) pour les commandes détaillées et la structure du code, et [`ROADMAP.md`](ROADMAP.md) pour le plan de route.

## Données

Source : OpenData Ville de Paris, licence ODbL.
- `les-arbres` — arbres du domaine public (~210 k entrées, mis à jour ~hebdo).
- `arbresremarquablesparis` — sous-ensemble labellisé « remarquable » (~200 entrées).

Les fichiers téléchargés ne sont pas committés (`.gitignore`).

## Licence

À définir — projet privé pour l'instant.
