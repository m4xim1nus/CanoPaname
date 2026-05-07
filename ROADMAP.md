# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours — Vérité & Friction

Patch copy + UX + dette saison. **Zéro nouvelle feature, zéro casse de schéma.** Cible : ~3-5 jours cumulés.

- **Suppression UI saisons** (préparation cycle Variantes). Cacher `SeasonSelector` (Map, Arboretum, Remarquables, Profil), retirer `ArchiveBanner`, basculer Profil/Arboretum/Remarquables en mode global-only. **Garder** l'enum `Season`, le store `SeasonStore` et la colonne `CaptureEntity.season` côté schéma — réutilisés par Variantes. Retirer les 3 badges saisonniers du catalogue (devenus impossibles à débloquer).
- **Communication honnête** :
  - `README.md` : « 907 espèces » → « 907 espèces dont 528 avec fiches enrichies ».
  - `README.md` : retirer « saisonnalité réelle » (puisque l'UI saison disparaît).
  - `README.md` + `PRIVACY.md` : mention OpenStreetMap (« tuiles cartographiques OSM via OpenFreeMap, sans envoi de données personnelles »).
  - `CHANGELOG.md` `[1.0.0]` : préciser « fiches remarquables enrichies accessibles après capture ».
- **`UnknownContent` pédagogique** (`ArbreDetailScreen.kt`) : « Non capturé. Capture un arbre de cette espèce et tous les semblables se déverrouilleront. < 30 m. »
- **`CaptureAvailability.TooFar` distance affichée** : « Trop loin (X m / max 30 m). Rapproche-toi. »
- **Bullet remarquables Welcome** étoffé : « Les arbres remarquables (★) sont une chasse spéciale. Le bouton ★ en haut à droite trouve le plus proche. »
- **Feedback GPS post-permission** : snackbar « Localisation en cours… » + pulse FAB GPS pendant le gap 7-10 s avant 1er fix.
- **Réactivité loc système** : `BroadcastReceiver` sur `LocationManager.PROVIDERS_CHANGED_ACTION` pour réagir si l'utilisateur active la localisation après ouverture de l'app.
- **Bug date « aujourd'hui »** au Profil : un fix capturé hier affiche « aujourd'hui » au lieu de « il y a un jour ». Vérifier l'arrondi `Duration` vs jour calendaire (zone Europe/Paris).
- **Compteur global Profil** :
  - « X / 907 espèces (Y %) » + « Z / 213 042 arbres déverrouillés (W %) » sous les stats existantes.
  - Rend visible la progression et la nature démesurée du dataset.
- **Badges débloqués sur ProfileScreen** : remplacer la card unique « Première capture » par une rangée preview des N derniers badges débloqués (3-4 max), en plus du lien `AllBadgesEntry`.
- **Cluster contenant ★** : ring orange fin si `has_remarquable_count > 0` (cluster property additionnelle dans `MapLayers.kt`). Léger, pas de halo agressif.
- **Polish haptique & feedback** :
  - Haptique `LongPress` à l'ouverture du sheet `ArbreDetailContent`.
  - Déplacer haptique capture du post-INSERT vers le tap « Capturer » (synchronise tactile et action perçue).
  - Snackbar + `Tick` haptique à l'annulation caméra (`CaptureLauncher.kt:116-119`).
  - Label + timeout 60 s sur `LinearProgressIndicator` export/import (`ProfileScreen.kt:370`).
- **Détails iconographie & timing** :
  - FAB ★ : icône `Search` → `Star` (signal « quête » au lieu de « tool »).
  - Snackbar distance remarquable 3 s → 5 s.
  - `EmptyState` `bodyMedium` 14 sp → 16 sp (ProfileScreen, ArboretumScreen, BadgesScreen, RemarquablesScreen).

## Prochains cycles

### Photos — *next*

Photos multiples par espèce et par arbre individuel, visibles dans le modal détail (espèce + remarquable). Suppression d'une photo possible tant qu'il en reste ≥ 1 sur l'espèce. Backup `schemaVersion = 2` rétro-compatible lecture v1.

Profondeur Arboretum associée :
- Tranches de fréquence (« +10 000 », « 2 000-10 000 », « 1 000-2 000 », « 100-1 000 », « < 100 ») avec sticky headers, miroir des arrondissements pour les remarquables.
- Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche d'espèce non capturée (symétrise le radar ★).
- Restructuration des badges progressifs en barres + paliers visibles (1, 10, 25, 50, 100, 250…), en place des 4 marches abruptes actuelles.

Bonus carte : depuis la fiche d'un remarquable, bouton « Voir sur la carte » qui recentre, zoome et pulse 2 s sur le pin.

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_3_4`, backup `schemaVersion = 3`. Badges variantes émergent naturellement.

### Endgame

Cycle de rétention long terme à programmer après stabilisation Variantes :
- Maîtrise par arrondissement (carte chromatique vert/jaune/gris, badge « Maître du Xe »).
- Quêtes hebdomadaires locales, opt-in, sans notification push.
- Pré-affichage de la fiche remarquable enrichie même non capturé, avec bandeau « Pas encore découvert ».
- Fallback Wikipedia pour les 379 espèces sans fiche (« Famille X. Y individus à Paris. »).

## Cycles livrés post-1.0

*Vide — premier cycle post-1.0 en cours.*

## Historique pré-1.0

Archive figée des phases qui ont mené à v1.0.0 (2026-05-05). Détails verbeux dans `CHANGELOG.md` `[1.0.0]` et l'historique git.

- **Phases 0-2 — Scaffold → MVP carte → Capture** : squelette Gradle/Kotlin/Compose, MapLibre + 213 042 arbres clusterisés, géoloc native sans GMS, capture photo + GPS + Room (`MIGRATION_1_2` table `capture`), Arboretum, découverte par espèce.
- **Phases 2.5-7 — Profondeur & gameplay** : fiche-espèce Wikipedia FR (528/907) + stats Paris, mini-carte filtrée, fiche enrichie remarquables, ProfileScreen, saisonnalité 4 buckets calendaires, revue graphique (icône platane, palette `ArbresColors`, Fraunces SemiBold, splash animé), 15 badges en 6 catégories (`BadgeEvaluator` pur), backup ZIP via SAF dédup idempotent, onboarding `WelcomeScreen` + `OnboardingStore`, texture sensorielle (`ArbresMotion`, haptiques, climax capture).
- **Phases 8-10.5 — Pré-release & polish v1.0** : hygiène (tests `BadgeEvaluator` + `BackupImporter`, detekt baseline, extraction `MapLayers.kt`), 1re session device GrapheneOS et fix de 9 bugs, rebranding *Arbres* → *CanoPaname* (surface utilisateur), splash 2-passes, refonte iconographie remarquables, `PhotoLightbox`, renommage Pokédex → Catalogue, banque ~240 splash tips, coloration progressive des clusters carte.
- **Phase 11 — Audit pré-public** : refonte `README.md` v1.0, `CHANGELOG.md` Keep-a-Changelog, `PRIVACY.md`, `SECURITY.md`, `.github/release-template.md`, légal & attributions (OFL Fraunces, ODbL OpenData, CC BY-SA Wikipédia, `AboutScreen`).
- **Phase 12 — Hot fixes post-tests live** : tint hero Welcome, retours-ligne splash tips, célébration nouvelle espèce, `filterGeoJsonBySpecies` skip remarquables non capturés, bouton « Fiche espèce » conditionné, recompress JPEG long-edge 1600 / quality 85.
- **Phase 13 — Hardening, identité & passage public** :
  - **13A** code & assets durcis (manifest privacy, strip EXIF, `BackupImporter` anti-zip-bomb, ProGuard strip Logs, `MIGRATION_2_3` photoPath basename, schema 2.json, ~250 commentaires nettoyés).
  - **13B** repo renommé `Arbres` → `CanoPaname`, email projet dédié `canopaname@pm.me`, `git filter-repo` 40 commits, `gitleaks` 0 finding, wrapper Gradle committé.
  - **13C** keystore prod hors-machine, GitHub Actions `build.yml` + `release.yml`, naming APK Obtainium, `RELEASE.md` checklist, bump `versionCode = 10000` / `versionName = "1.0.0"`, repo public, tag `v1.0.0` pushé, [release publiée](https://github.com/m4xim1nus/CanoPaname/releases/tag/v1.0.0).
