# Performance perçue & friction sensorielle — CanoPaname v1.0

## Verdict

CanoPaname soigne ses animations ponctuelles (splash crown cascadée, célébration capture, transitions saisonnières) et ses tokens motion. Mais trois frictions cassent l'expérience : **(1) cold-start avec ~700 ms de freeze masqués par le splash sans aucun loader explicite**, **(2) haptique ghetto-localisée — un seul `LongPress` après INSERT capture, zéro feedback ailleurs**, **(3) loaders mid-session orphelins (FilterSplash sans cancel, `LinearProgressIndicator` Profile sans label)**. Pour une app dont le pitch est l'épuration esthétique, ces silences ressemblent à des oublis, pas à des choix. Note globale **3.7/5**.

---

## 1. Audit motion — tokens et cohérence

`ArbresMotion.kt` expose une grille saine :

- `micro` 150 ms / `short` 300 / `medium` 600 / `long` 1200 / `sway` 2400 (oscillation infinie) / `celebration` 1800.
- Easing `FastOutSlowIn` (sway) + `FastOutLinear` (snap).

Application :

- **Cold-start splash** (`MapOverlays.kt:120-230`) — hero fade-in 600 ms + couronne de 7 mini-platanes en cascade séquencée (gaps 500 ms) + sway 2400 ms infini. Phases calculées via `phaseOffset` irrationnel pour casser les harmoniques. **Excellent**.
- **Transitions saisonnières** (`SeasonAmbience.kt`) — 16 particules en parallaxe avec envelope fade asymétrique (15% / 30%) sur 1800 ms. Sobre, précis, jamais bruyant. **Excellent**.
- **Célébration capture** (`CaptureCelebrationOverlay.kt:92-110`) — halo 8→48 px en 300 ms, cœur pulse 1→1.5→1, binôme latin flottant 800 ms post-fade. **Très bon**, mais le binôme tient un peu trop.
- **Welcome canopée** (`WelcomeScreen.kt:172-202`) — lerp gris→vert + scale 0.85→1.0 sur **4000 ms reverse**. Boucle hypnotique mais aucun « punch » au tap CTA. **Trop long**, et le CTA n'a pas de feedback motion en retour.
- **`SeasonSelector` crossfade** — 300 ms (`motion.short`). **Juste**.

**Conclusion motion** : tokens bien appliqués, chaque animation a une raison. Mais ils servent les animations **vues à froid** — les **interactions tap** elles-mêmes sont nues.

---

## 2. Audit haptique — un seul appel dans toute l'app

Recherche systématique : un seul helper `Haptics.kt`, exposant `rememberCaptureHaptic()` qui mappe `HapticFeedbackType.LongPress`. Une seule utilisation : `CaptureLauncher.kt:148`, post-INSERT Room.

| Lieu | Type attendu | Présent ? | Verdict |
|---|---|---|---|
| Post-INSERT capture | `LongPress` | ✓ | Présent mais **timing tardif** : la photo a déjà disparu, le pin est déjà vert, le user sent vibrer sans savoir pourquoi. |
| Tap FAB GPS / Arboretum / Profil / ★ | `Tick` (léger) | ✗ | Manque. Ripple Material visible mais pas tactile. |
| Ouverture sheet (`ArbreDetailContent`) | `LongPress` | ✗ | **MANQUE CRITIQUE**. Moment high-intent, zéro feedback. |
| Bouton « Capturer » sheet (avant intent) | `LongPress` | ✗ | Manque. L'intent caméra avale le tap sans confirmation. |
| `SeasonSelector` change saison | `Tick` | ✗ | Manque. Changement de contexte global (vive ↔ archive) sans feedback. |
| `ArboretumScreen` toggle Liste/Catalogue | `Tick` | ✗ | Manque. Toggle muet. |
| `ProfileScreen` export/import | `LongPress` | ✗ | Manque. Initiation d'action longue silencieuse. |
| Erreur capture (>30 m, GPS stale) | `Heavy` ou `Reject` | ✗ | Manque. Snackbar verbal, zéro tactile. |
| Annulation caméra (cancel back) | `Tick` | ✗ | Manque. Retour silencieux. |

**Problème structurel** : `rememberCaptureHaptic()` est un helper trop spécialisé (nom hardcodé `Capture`). Il n'existe aucune API générique → personne d'autre n'utilise l'haptique. Et le timing post-INSERT est mal calé : l'haptique devrait toucher au moment de l'**action user** (tap Capturer), pas après le pipeline DB.

**Verdict** : présent **mais malplacé et solitaire**. ~8 interactions critiques sans haptique attendu.

---

## 3. Audit loaders et états transitoires

Recherche : `CircularProgressIndicator` (2 occurrences), `LinearProgressIndicator` (1).

| Écran | Loader | Label / contexte | Verdict |
|---|---|---|---|
| `MapOverlays.kt:392` (`FilterSplash`) | spinner 28 dp blanc | « Filtrage de {speciesLabel}… » | Acceptable. Pas de cancel, pas de timeout. |
| `ProfileScreen.kt:370` | `LinearProgressIndicator` | **Pas de label** | **MANQUE CRITIQUE**. Barre 100 % sans contexte. |

Contextes attendus mais absents :

1. **GeoJSON mid-session enrichment** (`MapScreen.kt:195-219`) — debounce 1 s puis ~5-15 s en BG (`Dispatchers.Default`) pour enrichir 217 k features. Aucun loader. CLAUDE.md justifie : « trop lourd pour bloquer 1er paint ». Vrai architecturalement, mais perceptuellement le user voit son **pin individuel passer au vert immédiatement** puis **les clusters mettent 1 s à 10 s à suivre**. Sensation de bug.
2. **Export/import backup** (`ProfileScreen.kt:98-138`) — `BackupBusy` enum (`Idle`/`Exporting`/`Importing`) en state, mais aucun feedback visuel pendant les 5-10 s de zip. Snackbar arrive seulement à la fin.
3. **Cold-start GeoJSON load global** (`MapScreen.kt:354-396`) — la stratégie « pose layers vides → flip `arbresPrets = true` → injecte le GeoJSON » masque ~700 ms de freeze sous le splash. Architecturalement sain mais user perçoit un splash long.

Aucun loader ne montre la **progression** ni n'offre de **cancellation**.

---

## 4. Latences perçues — tap → réaction

**A. Ouverture fiche** (tap pin → sheet) : ~5-50 ms Room + 300 ms anim sheet = **305-350 ms perçus**. Tap ripple disparaît, puis 250 ms de noir, puis sheet slide. Si Room cold-start, 10× plus long sans feedback.

**B. Capture** (tap « Capturer » → célébration) : permission + GPS check (instant) + intent caméra IPC (~50-200 ms) + photo ~3-5 s real-world + decode/scale/compress (200-500 ms) + INSERT Room (10-30 ms) + recomposition + halo overlay = **~350-400 ms latence perçue post-photo** avant que le halo n'apparaisse. Pour le moment fort de l'app, c'est lourd.

**C. GPS first-launch** : couvert par le `attachMapLibreLocationBridge` (CLAUDE.md). Sans bridge, le bouton « Active le GPS » resterait coincé ~10 s. Avec bridge, la transition rouge → vert se fait en ~1 s. **OK**.

**D. Pan/zoom carte** : natif MapLibre, 60 fps. **5/5**.

**E. Cluster color-shift post-capture** : 2-step visible. Pin individuel passe vert immédiatement (`applyDiscoveryColor` sans debounce) ; cluster met 1-10 s à suivre. Justifié archi (217 k features) mais **friction visuelle** pour l'œil attentif.

**F. Switch saison** : crossfade icône/label 300 ms. Mais **aucun impact visuel sur la carte au changement** — les pins se repeignent sans transition. Saut sec. Pas d'`SeasonAmbience` au switch (figée à `Season.current()`, donc archive ne déclenche rien).

**G. Transitions écrans** : Compose nav par défaut, fluide. **4.5/5**.

---

## 5. Friction dégradée — cas d'erreur

**Annulation caméra** : `CaptureLauncher.kt:116-119` — `if (!success) { file.delete(); return@launch }`. **Zéro feedback**. Pas de snackbar, pas de toast, pas d'haptique. Le sheet reste ouvert, le user pense « mon tap a disparu ». **Mauvais UX pour un scénario fréquent** (tap involontaire, annulation par X de la caméra système).

**GPS stale / trop loin** : snackbar « Trop loin de l'arbre (XX m, max 30) » à `CaptureLauncher.kt:219`. Verbal correct, **mais aucun haptique** d'erreur. Pour une action high-intent qui échoue, `Heavy` ou `Reject` attendu.

**Photo 0 octets** (certains OEM Android écrivent un fichier vide en cas de souci) : guard `file.length() > 0` présent, mais que se passe-t-il en cas de retour ? Couvert par le même `return@launch` silencieux — friction identique à l'annulation.

---

## 6. Cold-start empirique — chronologie perçue

Synthèse CLAUDE.md + lecture code :

- t=0 : process start, MainActivity onCreate.
- t≈100-300 ms : MapView onCreate/onStart/onResume, style Builder.
- t≈300-400 ms : `ColdStartSplash` rendu (hero fade-in 600 ms démarre).
- t≈400-700 ms : **GeoJSON 32 Mo parsé sur UI thread (bloquant)**. Choreographer freeze. User voit splash statique mais le crown anime.
- t≈700-1200 ms : pins visibles sous le splash (tous gris, pas d'enrichment).
- t≈1700-2300 ms : splash fade-out (300 ms), map révélée.
- t≈2500-10000 ms : enrichment BG re-pousse, clusters virent gris → vert.

Perception : **les ~700 ms de freeze sont masqués par le crown anim**, pas catastrophique. Mais **l'app n'indique nulle part qu'elle est en train de charger** — un user qui ne voit pas le crown bouger pourrait croire à un freeze.

---

## 7. Cartographie sensorielle — notes par interaction

| Interaction | Note | Raison |
|---|---|---|
| Cold-start splash | **4/5** | Excellent motion (cascade, sway). Freeze 700 ms masqué — sensation latente. |
| Pan/zoom carte | **5/5** | Fluidité native, zéro lag. |
| Tap pin → sheet | **3/5** | Latence ~300 ms sans micro-feedback. Pas d'haptique. |
| Capture pipeline | **3.5/5** | Haptique post-INSERT bien intentionné mais mal calé. Pas de feedback avant/pendant compression. |
| Célébration capture | **4.5/5** | Halo + cœur excellents. Binôme latin un peu long en fin. |
| Switch saison | **3/5** | UI crossfade ok, mais carte se repeint sec. Aucune transition d'ambiance. |
| Cluster color-shift | **3/5** | 2-step visible (pin vert immédiat, cluster vert décalé 1-10 s). Friction architecturale assumée. |
| Transitions écrans | **4.5/5** | Compose nav fluide, exit/enter 300 ms. |
| Loaders mid-session | **2/5** | `LinearProgressIndicator` sans label, FilterSplash sans cancel, enrichment silencieux. |
| Frictions d'erreur (cancel caméra, GPS stale) | **2/5** | Verbal-only. Pas d'haptique d'erreur, retour silencieux à l'annulation. |

**Note globale** : **3.7/5**.

---

## Pistes considérées et écartées

- **Loader spinner permanent au cold-start** : ajouterait du bruit visuel, le crown cascade fait déjà l'office d'animation occupée. Rejeté.
- **Haptique sur tous les FAB** : risque de « vibro fatigue » à force de taps. Préférer `Tick` sur high-intent (capture, switch saison) plutôt que sur tous les boutons.
- **Refonte 2-wave cluster color-shift** : exigerait l'enrichment live sans debounce, soit ~5 s freeze à chaque capture. Trade-off perdant. Rejeté.
- **`SeasonAmbience` persistante en archive** : ajouterait une ambiance à l'archive, mais le mode archive doit « sentir figé » par design. Rejeté.
- **Progress bar au FilterSplash** : filtrage <1 s en pratique, pas de besoin observé. Rejeté.

---

## Recos par tier

| Tier | Titre | 1 phrase |
|---|---|---|
| v1.0.1 | Haptique à l'ouverture du sheet (`ArbreDetailContent`) | `LongPress` au moment où la sheet passe de `null` à un `Arbre`, crée le « il s'est passé quelque chose » immédiat. |
| v1.0.1 | Label + timeout au `LinearProgressIndicator` du Profile | Wrapper le loader export/import dans une `Column` avec label « Exportation en cours… » et timeout 60 s ; sinon l'app a l'air gelée. |
| v1.0.1 | Feedback à l'annulation caméra | Au `if (!success)` dans `CaptureLauncher.kt`, snackbar « Capture annulée » + `Tick` haptique pour que l'user sache que son tap a bien été pris en compte puis abandonné. |
| v1.0.1 | Ajuster le timing haptique capture | Déplacer l'appel `captureHaptic()` du post-INSERT vers le **moment du tap « Capturer »** (avant intent caméra) pour synchroniser tactile et action perçue. |
| v1.1.0 | Haptiques micro sur toggles (`SeasonSelector`, ArboretumScreen viewMode) | `HapticFeedbackType.Tick` sur `onSelect` des `SingleChoiceSegmentedButton` — feedback non-intrusif, confirme le toggle sans alourdir. |
| v1.1.0 | Pulsation subtile si Room query > 100 ms au tap pin | Entre tap et sheet visible, fade alpha 50→100 ms si la fetch Room dépasse 100 ms ; indication de chargement sans bloquer la transition. |
| v1.1.0 | Transition d'ambiance au switch saison | Au changement saison via `SeasonSelector`, jouer une mini-particule fade 300 ms sur la carte (même si archive) — relie le geste à un effet visuel léger. |
| v1.1.0 | API `Haptics` générique | Refactor `rememberCaptureHaptic()` en `rememberHaptic()` exposant `light()` / `confirm()` / `error()` — ouvre la voie à des haptiques disséminés sans helper spécialisé par feature. |
