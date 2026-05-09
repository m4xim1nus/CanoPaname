# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours — Photos et progressivité

Profondeur et lisibilité de l'expérience après v1.0.1, sans casse de schéma. On garde le modèle Room (1 capture = 1 photo, `arbreId` + `speciesIndex` portés par la row), on rend la re-capture explicite et on permet l'inverse : supprimer. En parallèle, refonte des badges progressifs en multi-paliers visibles, et deux items lisibilité (tranches Arboretum, voir-sur-carte remarquables).

Découpage en sprints (1 item BACKLOG = 1 sprint, atomiques) :

### Sprint 1 — Re-capture + suppression — livré 2026-05-09

CRUD complet sur les captures, sans casse de schéma. Sur un arbre dont l'espèce est débloquée, le bouton `Capturer` devient `Recapturer` (pipeline GPS+photo+INSERT inchangé, N captures par arbre supportées nativement). Suppression via icône poubelle dans `PhotoLightbox` + long-press dans `PhotoGallery` ; si c'est la dernière capture de l'espèce / du remarquable, dialog explicite annonce le re-verrouillage et la suppression renvoie sur la Map. Cascade automatique sur les Flows Room (`SELECT DISTINCT`, `BadgeEvaluator` pur, `applyDiscoveryColor` reactive). Ajouts ciblés : `CaptureDao.deleteById`, `CaptureRepository.deleteCapture`, `CaptureButton` factorisé dans `ArbreDetailScreen`, `DeleteCaptureDialog`, slot `onDeleteAt` sur `PhotoLightbox`, `combinedClickable` sur `PhotoGallery`, `onUnlockLost` câblé dans `ArbresNavHost` via `popBackStack(Routes.MAP, inclusive = false)`.

### Sprint 2 — PhotoLightbox : zoom borné, pan borné, navigation entre photos — livré 2026-05-09

Lightbox transformée en visionneuse propre. Pinch-zoom toujours 1×→5× mais le pan est désormais clampé aux bords (calcul `boxSize × ratio bitmap × scale`), plus de photo qui s'évade en vignette dans un coin. Galerie ≥ 2 photos : swipe horizontal entre photos via `HorizontalPager` (foundation), chevrons `Outlined.ChevronLeft/Right` `CenterStart/End` désactivés aux bornes ; pager gelé dès que `scale > 1f`. Le `Modifier.transformable` standard volait le drag 1-doigt à scale=1 et bloquait le swipe — remplacé par un détecteur custom `awaitEachGesture` qui ne consomme rien à 1 doigt + scale=1 (le pager prend), consomme le pinch multi-touch et le pan 1-doigt à scale > 1. Sous-composable interne `ZoomablePage` avec état `scale`/`offset`/`boxSize` recréé via `remember(file)` → reset auto à la transition de page. Signature publique de `PhotoLightbox` inchangée (callers `SpeciesDetailScreen` et `RemarquableDetailScreen` intacts) ; `onDeleteAt(idx)` passe maintenant `pagerState.currentPage`. `Alignment.TopStart` laissé libre pour Sprint 4.

### Sprint 3 — Refonte badges progressifs en multi-paliers visibles

Fusion de 6 badges en 3 multi-paliers : `Marcheur` 1/10/25/50/100/250, `Botaniste` 1/10/25/50/100/200, `Chasseur` 1/5/10/25/50. Catalogue passe de 13 → 10 badges. UI : barre de progression + jalons cliquables dans `BadgesScreen`. `BadgeEvaluator` reste pur, balayage chronologique unique.

### Sprint 4 — Sauter à un arbre sur la carte depuis ses points de contact

Mécanisme partagé : param `pulseArbreId` sur `Routes.MAP`, animation caméra (fly-to ~600 ms) et pulse 2 s sur le pin, zoom approprié au z16-17 selon densité. Deux points d'entrée :
- depuis `RemarquableDetailScreen` (action « Voir sur la carte »),
- depuis `PhotoLightbox` (icône « Voir sur la carte ») pour n'importe quel arbre capturé : la photo affichée porte un `arbreId`, on saute à cet individu précis. Particulièrement utile depuis la galerie d'une fiche-espèce où plusieurs captures de l'espèce coexistent.

## Prochains cycles

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_4_5`, backup `schemaVersion = 3`. Badges variantes émergent naturellement.

**Nettoyage catalogue espèces** (analyse à date dans `docs/analyse-especes.md`, datée du 2026-05-08). Les décisions ci-dessous sont **à figer en début de cycle** ; le doc liste les chiffres et les options. Traitement amont durable côté `tools/build_dataset.py` pour survivre aux refresh OpenData sans intervention manuelle :

- Sort des entrées `Non spécifié` (677 arbres, 3 entrées) : drop dur côté script ou affichage gris non-cliquable ?
- Sort des entrées `sp.` / `n. sp.` (8 793 arbres, 4,1 % du dataset) : tag `unknownSpecies` + regroupement sous le `nc` parent dans Arboretum, ou espèce à part entière ?
- Table `SPECIES_FIXUPS` pour les coquilles OpenData (`Olea europea` → `europaea`, à enrichir au fil de l'eau).
- Extraction du nom vernaculaire FR : Wikidata `P1843` (propre) vs regex sur le summary Wikipedia (~85 %) vs les deux ?
- Compteur Arboretum à deux niveaux (`X / 221 noms communs` + `Y / 907 espèces`) ?
- Carte filtrée par nom commun (set de `sk` fusionnés en expression `match` MapLibre).
- Sanity checks au build dataset (espèce > 100 perd sa page WP entre 2 builds, `sk` existant disparaît, nouveau genre `Non spécifié` avec count > 50).
- Tranches de fréquence Arboretum (sticky headers `+10 000` / `2 000-10 000` / `1 000-2 000` / `100-1 000` / `< 100` sur l'onglet LISTE) — décalé du cycle Photos parce qu'il est plus cohérent de figer les tranches **après** le nettoyage du catalogue d'espèces (drop des `Non spécifié`, sort des `sp.`, regroupements `nc`).

Pistes gameplay associées (à arbitrer en début de cycle, refusables) :
- Mini-quiz d'identification entre espèces partageant le même `nc` (ex. `Quercus robur` vs `Q. petraea`) — réutilise les summaries Wikipedia déjà bakés.
- Badges « Inspecteur » (capturer N arbres `sp.`) et « Mosaïque de chênes » (10 espèces sous le même `nc`).
- Affichage du nom vernaculaire FR sur la fiche-espèce.

### Endgame

Cycle de rétention long terme à programmer après stabilisation Variantes :
- Maîtrise par arrondissement (carte chromatique vert/jaune/gris, badge « Maître du Xe »).
- Quêtes hebdomadaires locales, opt-in, sans notification push.
- Pré-affichage de la fiche remarquable enrichie même non capturé, avec bandeau « Pas encore découvert ».
- Fallback Wikipedia pour les 379 espèces sans fiche (« Famille X. Y individus à Paris. »).

## Cycles livrés post-1.0

### Vérité & Friction — `1.0.1` (2026-05-09)

Patch dette + UX, zéro casse de schéma. Retrait UI saisons (schéma `Season` conservé pour Variantes), 2 badges saisonniers retirés (catalogue 15 → 13). Cinq sprints atomiques : suppression UI saisons + badges, alignement README/PRIVACY/CHANGELOG, refonte copy in-app (`UnknownContent`, `TooFar`, `EmptyState`), refonte Profil (compteurs catalogue + arbres déverrouillés, row 3 derniers badges, fix bug date « aujourd'hui »), Map UX (FAB ★ étoile, pulse GPS post-permission, ring orange clusters remarquables, reprise GPS auto, haptiques sheet & capture). Détails dans `CHANGELOG.md` `[1.0.1]`.

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
