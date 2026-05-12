# Roadmap

App perso, pas de calendrier engageant. Single-player, stockage local strict — pas de cloud, pas de multi-device synchronisé. Ce doc est le **plan opérationnel** : pour la vérité release voir `CHANGELOG.md`, pour les idées non planifiées voir `BACKLOG.md`. Process décrit dans `CLAUDE.md` (section *Workflow & docs*).

## Cycle en cours

### Réveil — écrans de chargement & animations

Cycle de polish ciblé sur le cold-start et les animations Compose. Cinq motivations :
le point BACKLOG « animations neutralisées si échelle d'animation système = 0 » (le radar du
mode chasse est déjà corrigé via `withFrameNanos`, le reste pas) ; le `FilterSplash` qui dit
encore « Filtrage de X… » + spinner au lieu du ton « Réveil des … » du splash principal ; un
bug — la séquence intro de 10 tips ne joue pas au tout 1er lancement, le splash part direct en
aléatoire ; le splash cold-start qui disparaît avant que les pins soient peints (instant « carte
sans arbre ») ; et la banque de ~242 tips qui date d'avant 1.1.0/1.2.0 (chiffres dataset périmés,
aucune phrase sur genres / badges binaires + familles / chasse / fiches espèce-genre / barres de
progression / logos d'arrondissement).

Décisions de cadrage : animations résistantes à scale=0 → seulement les 2-3 qui comptent
(couronne mini-platanes + fades du `ColdStartSplash`, hero `WelcomeScreen`, célébration 1re
capture) repassées en pilotage `withFrameNanos` ; allongement du splash → flip `arbresPrets`
après `setArbresGeoJson` (pins peints) + plancher de durée min (~2,5 s) ; `FilterSplash` → texte
au même ton, **pas** d'anim platanes ; bug intro → repro sur device avec logcat DEBUG puis fix
sans casser les invariants documentés de `SplashTipsController` ; tips → refresh des valeurs +
outil HTML de revue livré tôt + intégration des retours + nouveaux tips post-1.0. *Bonus repéré :
le tint saisonnier du `surface` a déjà disparu du thème — rien à killer, mais la ligne `CLAUDE.md`
qui le mentionne est périmée → à retirer en clôture.*

Sprints :

- **S1 — Tips : refresh valeurs + outil HTML de revue.** Régénérer `assets/splash-tips.json`
  via `python3 tools/build_dataset.py` (chiffres dataset à jour). Créer `tools/build_tips_preview.py`
  → `docs/tips/index.html` (patron : `build_report.py` → `docs/dataset/index.html`) : tous les
  tips groupés par catégorie, placeholders rendus, et pour chaque tip un verdict `RAS` / `à tuer`
  / `chute à réécrire` + commentaire libre + bouton « Exporter mon avis » → bloc texte copiable.
  Tout client-side. Livré tôt → débloque la revue async pendant S2-S5.
- **S2 — Bug : intro tips non jouée au 1er lancement.** Instrumentation `Log.d` temporaire
  (`SplashTipsController`, flip `arbresPrets`), build debug, séance logcat avec moi sur un fresh
  install, diagnostic (course `markDone()`↔nav, mount transient du fallback `MAP` du `NavHost`,
  ordre des `LaunchedEffect`…), fix sans toucher aux invariants (`.first()` figé, pas de
  `collectAsState` sur `splashIntroSeen`, keys minimales, `markSplashIntroSeen()` post-onboarding
  seulement), instrumentation retirée.
- **S3 — Allongement du splash cold-start.** `MapScreen.kt` : ne plus flipper `arbresPrets`
  après les layers vides mais après `setArbresGeoJson` (pins peints) ; + plancher de durée min
  (~2,5 s depuis le mount, constante nommée). Path filtré : flip après `addArbresLayers` + petit
  plancher (~1 s). Path « cache enrichi » (retour Profil → Map) : plancher réduit/0 pour ne pas
  ralentir une nav rapide. Vérif : plus de moment « carte sans arbre », cold-start < ~4 s.
- **S4 — Animations résistantes à animation-scale=0.** Helper réutilisable (`ui/common/FrameClock.kt` :
  `rememberFrameProgress(periodMs)` / `rememberFrameMillis()`), modelé sur le `withFrameNanos` du
  `RadarGlyph`. Convertir : `MiniArbreCrown` (sway + drift + cascade par arbre), fades du
  `ColdStartSplash`, hero du `WelcomeScreen`, célébration 1re capture (`SpeciesDetailScreen`).
  Laisser tel quel : pulse FAB GPS, décalage FAB chasse, chiffre de distance, `AnimatedVisibility`
  du splash — choix documenté en commentaire. Vérif device avec échelle d'animation désactivée.
- **S5 — `FilterSplash` : texte au ton « Réveil des … ».** Remplacer « Filtrage de X… » + gros
  spinner par « Réveil des {count} {label} » (count via `DatasetStats`/`SpeciesInfo` quand dispo ;
  mode genre / fallback : sans nombre), petit indicateur de progression discret conservé, couleurs
  et typo alignées sur le `ColdStartSplash`. Pas d'anim platanes.
- **S6 — Tips : intégration des retours + nouveaux tips.** Appliquer les verdicts de l'HTML (tuer
  / réécrire / commentaires) sur `splash-tips-static.json` et les générateurs de `write_splash_tips()`.
  Ajouter des tips post-1.0 (saisons calendaires, badges binaires + familles « Familier des/du … »,
  backup ZIP, mode chasse radar, fiches espèce & genre, barres de progression Profil, logos
  d'arrondissement, catalogue ~929 entrées) + quelques créations history/popculture. Regénérer
  `splash-tips.json` + `docs/tips/index.html`, revérifier le sanity-check placeholders. (option)
  test dans `tools/test_build_dataset.py` : ids uniques, `intro` présents, placeholders ∈ set.
- **Clôture.** Entrée `CHANGELOG.md` (`[1.3.0]` probable), bump `versionCode`/`versionName`,
  rotation ROADMAP (Réveil → « Cycles livrés post-1.0 », promotion de *Variantes*), `BACKLOG.md`
  (items absorbés barrés), `CLAUDE.md` (retrait de la ligne périmée sur le tint saisonnier).

## Prochains cycles

### Variantes

Refonte Arboretum « états/variants ». La colonne `season` (devenue inerte par Vérité) se réincarne en `variants` (bitmask ou table associée). États possibles : *en fleur*, *tout nu / hivernal*, *avec fruits*, *bébé* (faible circonférence), *géant* (forte circonférence). Détection auto quand le dataset le permet (circonférence), déclaration utilisateur sinon (chip à la capture, conditionnalité selon la date et le genre/l'espèce ?).

Inspiration : Dave the Diver / Pokédex enrichi. Re-capture du même arbre dans un état nouveau = upgrade visible de l'élément Arboretum, sans inflation artificielle. Migration `MIGRATION_4_5`, backup `schemaVersion = 3`. Badges variantes émergent naturellement. Items détaillés dans `BACKLOG.md`.

## Cycles livrés post-1.0

### Progression — `1.2.0` (2026-05-12)

Refonte de l'expression de la progression. Le FAB ★ devient un mode chasse persistant (`HuntPanel.kt` — radar `withFrameNanos`, cible remarquable dynamique, distance live 5 s). Profil et Badges séparés conceptuellement : progression chiffrée en jusqu'à 7 barres Material 3 sur le Profil (arbres déverrouillés / remarquables / espèces / genres découverts / genres complétés / arrondissements visités / arrondissements complétés) + ligne « X jours depuis ta première capture » ; badges désormais tous binaires (`BadgeState` aplati, fin des progressifs/paliers), catalogue = 10 statiques + 2 familles dynamiques dérivées du dataset — « Familier des … » (26 genres à ≥ 7 espèces identifiées) et « Familier du … » (22 = 20 arr. + 2 bois, dénominateurs précalculés dans `assets/arr-species.json`). Cycle « Endgame » dissous, sa maîtrise par arrondissement absorbée ici. Six sprints. Quickfix detekt en clôture (baseline enfin wirée). Détails dans `CHANGELOG.md` `[1.2.0]`.

### Catalogue — `1.1.0` (2026-05-11)

Refonte du catalogue d'espèces : nettoyage data amont (drop dur des `Non spécifié`, normalisation `sp.`, `SPECIES_FIXUPS`), cascade de noms vernaculaires français uniques avec assert d'unicité au build (Wikidata P1843 → Wikipedia frTitle filtré → redirects API → ~30 overrides éditoriaux → construction), 202 fiches genre dédiées avec stats Paris, Arboretum à 2 niveaux *Catalogue* / *Historique*, badge progressif *Mosaïque de chênes*. Dix sprints. Refresh OpenData absorbé en passant (217 042 arbres, 183 remarquables, 929 entrées catalogue dont 803 identifiées). Détails dans `CHANGELOG.md` `[1.1.0]`.

### Photos et progressivité — `1.0.2` (2026-05-10)

Profondeur et lisibilité après v1.0.1, zéro casse de schéma. Six sprints atomiques : re-capture + suppression de captures (CRUD complet sans table photo 1:N), refonte `PhotoLightbox` (bornes zoom/pan + swipe `HorizontalPager`), refonte badges en multi-paliers visibles (catalogue 13 → 8, 22 paliers, `BadgeState` sealed binaire/progressif), saut vers l'arbre exact sur la carte (`Routes.map(pulseArbreId)` fly-to zoom 20 + halo pulse 2 s), galerie photos cliquable dans le sheet de détail arbre. Détails dans `CHANGELOG.md` `[1.0.2]`.

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
