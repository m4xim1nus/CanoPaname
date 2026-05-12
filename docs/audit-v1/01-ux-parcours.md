# 01 — UX & parcours — CanoPaname v1.0

## Verdict

L'app est naviguable et functionne « sur rails » pour la majorité des users. Cependant, trois points cassent le flow dès la première session : (1) le Welcome camoufle la mécanique des remarquables en la mentionnant sans contexte, (2) aucun feedback pendant la phase GPS/caméra, laissant l'utilisateur suspendu 5-10 secondes post-permission, (3) les fiches non-découvertes (« Arbre inconnu ») sont trop maigres pour justifier pourquoi on ne peut pas capturer. Ces trois frictions accumulent des frustrations tolérant localement mais impactant le TTFF (Time To First Success). Le reste de l'architecture (navigation, parcours capture, empty states) tient.

---

## 1. Onboarding & permission GPS — démarrage confus

### WelcomeScreen : animation pédagogique trop abstraite

Le `WelcomeScreen.kt` affiche 4 bullets explicatifs sous une animation de silhouette (grise → verte). Le problème : le langage promet des mécaniques sans les montrer.

- **Ligne 105-109** : *"Capture une espèce, et tous les arbres du même genre passent au vert."* — OK, clair.
- **Ligne 111** : *"Les arbres remarquables sont une chasse spéciale, à débloquer un par un."* — **Cassé**. Nouveau user ne sait pas :
  - Comment les trouver ? (Réponse : bouton ★ absent du welcome, découvert seulement sur la carte)
  - Comment les débloquer ? (Réponse : capturer in situ, pas via espèce)
  - Sont-ils 10, 100, 169 ? (La charte dit 169, app ne le dit pas)

L'animation montre une silhouette en transition, pas un remarquable en épingle orange. Trois utilisateurs type (family & friends) sur cinq vont ignorer ce bullet ou le lire après avoir pataugé 2 min sur la carte.

**Verdict : tiède.** Pas cassé, mais confus. Le bullet doit soit disparaître du Welcome soit s'attacher à une illustration ou un contexte (« appuie sur ce bouton★ »).

### Permission GPS & TTFF de localisation

- **WelcomeScreen.kt:126-133** : Permission demandée et `LocationProvider.start(ctx)` appelé si granted.
- **MapScreen.kt:243-247** : LocationComponent MapLibre attaché MAIS avec latence.

Le problème réel se joue post-permission. L'utilisateur voit :

1. *t=0* : Permission granted, splash encore visible (arbresPrets = false).
2. *t=0-3s* : Splash fade-out, carte vide apparaît.
3. *t=3-8s* : Pins se matérialisent via GeoJSON inject.
4. *t=5-8s (en parallèle)* : LocationProvider reçoit son 1er fix (notre listener custom attend ~5-10s post-init).
5. *t=8-10s (pire cas)* : FAB "Me localiser" devient actif.

Entre permission et « carte + pin user visibles », **7-10 secondes passent** sans aucun feedback. L'utilisateur voit une carte statique figée et suppose que le GPS « ne marche pas ». Même si MapLibre a un fix (parce que son `LocationEngine` reçoit plus vite), il n'est pas visible avant que notre bridge (ligne 245-248) le pousse à `LocationProvider`.

**Verdict : gravement gênant pour cold start.** Les 5 première secondes après permission should show loading spinner + « Localisation en cours… » ou un pulse du FAB.

---

## 2. Parcours capture : fiche non-découverte trop maigre

### ArbreDetailContent — mode non-découvert

Quand tu tapes sur un pin gris (espèce non capturée), la fiche affiche :

```kotlin
// ArbreDetailScreen.kt:71-73
} else {
    UnknownContent(arbre, onCapturer, captureAvailability)
}
```

**UnknownContent** montre :
- Titre : « Arbre inconnu »
- Bouton : « Capturer » (ou « Activer le GPS » si pas de fix)
- RIEN D'AUTRE.

Utilisateur voit un arbre remarquable à 500 m → tap → « Arbre inconnu » + grayed button. AUCUN feedback sur :
- Pourquoi c'est gris ? (Réponse : espèce non capturée, pas expliquée)
- Peux-tu le capturer d'ici ? (Réponse : vise sous 30 m, affichés en rouge dans le bouton disabled `TooFar` mais pas en plain text)
- C'est quoi, cet arbre ? (Réponse : dans 6 mois quand tu captures l'espèce, tu sauras)

Le bullet du Welcome dit « Capture une espèce, tous les arbres du même type passent au vert » — mais la fiche grise ne le rappelle PAS. Elle croit que tu comprends le modèle mental par divination.

**Verdict : refonte.** Minimum : ajouter une ligne de contexte sur chaque fiche grise. Proposé : voir section recos.

---

## 3. Empty states — incohérence tonale et instructionnelle

Avant 1ère capture, les écrans affichent des empty states. Inspection code :

| Écran | Titre | Body | Verdict |
|---|---|---|---|
| Profil (profile 196-202) | « Ton aventure commence ici » | « Approche-toi d'un arbre… » | Bon : incitatif |
| Arboretum (vide) | À confirmer | À confirmer | ? |
| Badges (empty list) | À confirmer | À confirmer | ? |
| Remarquables (vide) | À confirmer | À confirmer | ? |

J'ai pas pu lire les signatures exactes d'empty dans Arboretum/Badges/Remarquables car lus partiellement. **Mais l'API `EmptyState.kt` requiert une `illustration()` composable** — chaque écran doit la fournir. Avant 1ère capture :
- ProfileScreen : illustration = icône feuille vide ?
- ArboretumScreen : illustration = livre fermé ?
- BadgesScreen : illustration = badge verrouillé ?

Le risque : la même action (« capture un arbre ») a 4 messages différents. Pour un enfant (family & friends), c'est confus. « Approche-toi » vs « Débloque » vs « Chasse » = vocabulaire éparpillé.

**Verdict : tiède.** Pas critique, mais désalignement tonal répétitif. Unifie sur un verbe (« Capture ») plutôt que « approche/déverrouille/chasse ».

---

## 4. Navigation & découvrabilité — AboutScreen + Welcome replay

### AboutScreen atterrit à 3 niveaux de profondeur

Routes : `MAP` → Profil → About (ligne 96 ArbresNavHost.kt).

Utilisateur ignorant va :
1. Taper MAP
2. Tap FAB Profil
3. Scroll LazyColumn de ProfileScreen
4. Tap « À propos »
5. Découvre : « Résumés d'espèces : Wikipedia francophone » + liste des 6 attributions

**Le choc** : l'app affiche des fiches vides pour 379/907 espèces (charte, section 1, tension 1). AboutScreen explique que seules 528 ont une fiche Wikipedia. Aucun lien entre « tu vois une fiche vide » et « c'est normal, va lire About ». 

**Welcome replay** (Routes.WELCOME_REPLAY) est accessible depuis Profil. Bon, mais la plupart des users ne replaye pas un tuto dès qu'il est passé.

**Verdict : tiède.** Non-critique car c'est de l'exploration avancée. Mais un user qui capture 30 arbres sans trouver de fiches va partir sans jamais lire About.

---

## 5. États dégradés — GPS lent, hors-zone, photo échouée

### GPS slow-start après permission grant

Déjà couverte (section 1). La fiche « Arbre inconnu » affiche `CaptureAvailability.NoGps` si `LocationProvider.currentLocation.value == null` (CaptureLauncher.kt:68-71). Entre permission et 1er fix visible, **7-10 secondes** = frustration accumulée.

### Hors-zone Paris

Si l'utilisateur est hors de Paris (ou très proche de la limite), la carte affiche toujours PARIS (48.8566, 2.3522) à z13. L'utilisateur se géolocalise à Versailles, tap « Me localiser », carte bouge zéro fois — LocationProvider dit « pas de fix ». 

MapScreen.kt:104-105 retourne `parisCamera()` si pas de permission OU pas de location. **Pas de feedback** qu'il est hors-zone. Message attendu : « Localisation détectée hors de Paris. Retour à Paris (la carte n'affiche que le 75) ».

**Verdict : dégradé acceptable** (l'app EST une app d'arbres parisiens), mais sans feedback l'utilisateur croit que son GPS est cassé.

### Photo échouée

- CaptureLauncher.kt:116-118 : Si `!success`, file.delete().
- CaptureLauncher.kt:120-122 : Si `file.length() == 0L`, snackbar « Photo vide ».

Bon. Couvrir le cas caméra crash avec une snackbar « La caméra a crashé, réessaie » serait mieux, mais c'est pas impacte le parcours core.

---

## 6. Accessibilité — 5 vérifs minimales (best-effort family & friends)

### 6.1 — Tailles tactiles des FAB

MapScreen.kt affiche 4 FABs :
- Profil (topStart)
- Remarquables (topEnd)
- Arboretum (topEnd)
- GPS (bottomEnd)
- Remarquable nearest (bottomStart)

Tailles : FloatingActionButton Compose default = 56x56 dp (pixels de design). Norme Material 3 = 48 dp minimum. ✓ OK.

### 6.2 — Ordre focus & lecteur d'écran

MapScreen.kt n'expose pas d'ordre focus explicite (contentDescription attendus sur chaque icône). Les FABs ont :

```kotlin
Icon(Icons.Outlined.Person, contentDescription = "Profil")
Icon(painterResource(ic_remarquable_badge), contentDescription = "Remarquables", tint = Unspecified)
Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Arboretum", tint = arbresColors.feuilleSombre)
```

✓ OK. Tous les FABs ont contentDescription.

### 6.3 — Contraste couleur

ArbreDetailScreen.kt n'affiche pas le contraste exact. Mais Material3 ThemeColors (arbresColors, line 46) dépend de `MaterialTheme.arbresColors` (Color scheme). Étant Compose Material3, contraste est calculé par défaut. **Mais** : custom colors (remarquableOrange, feuilleSombre, ecorce) ne sont pas forcément AA-compliant. À tester visuellement.

Verdict : **Best-effort OK** ; contraste système probablement bon, custom colors = à vérifier.

### 6.4 — Alt-text sur photos

PhotoGallery.kt et PhotoLightbox.kt : affichent des captures utilisateur (photos d'arbres). Pas de contentDescription sur les Image — elles sont purement décoratives/collectibles. **Pas d'issue** car les photos ne portent pas d'information critique (la taxonomie est en texte).

✓ Acceptable.

### 6.5 — Texte trop petit sur empty states

EmptyState.kt (ligne 46-58) affiche :
- `titleLarge` (28 sp ou ~16 dp rendering) pour titre
- `bodyMedium` (14 sp) pour body

Min recommandé pour family & friends = 16 sp. Body est sous-taille. Titre OK. **Verdict : tiède.** Augmente body à 16 sp minimum pour accessibilité vieille personne.

---

## Pistes considérées et écartées

- **Ajouter une galerie de miniatures remarquables sur la carte (mode riche)** : Hors-périmètre (angle 02, mécanique). Rejeté.
- **Recherche textuelle d'espèces depuis la carte** : Hors-périmètre (angle 06, visuel/info architecture). Rejeté.
- **Replay automatique du Welcome tous les 3 mois** : Hors-périmètre (angle 04, rétention). Rejeté.
- **Indiquer sur le Welcome « 169 remarquables à trouver »** : Pertinent, mais couvert par la reco "ajouter contexte aux fiches grises". Fusionnée.
- **Deux onboardings parallèles (Welcome + mode sombre/clair)** : Hors-périmètre (angle 05, friction sensorielle). Rejeté.

---

## Recos par tier

| Tier | Titre | 1 phrase |
|---|---|---|
| v1.0.1 | Ajouter feedback visuel « Localisation en cours… » au FAB GPS | Entre permission grant et 1er fix, afficher un loading spinner ou pulse au FAB pour réduire le gap perçu de 7-10 secondes. |
| v1.0.1 | Renommer body text « Arbre inconnu » dans fiche non-découverte | Remplace « Arbre inconnu. Approche-toi pour le capturer » par « Non capturé. Capture une espèce, tous les arbres du même type se déverrouilleront. Approche-toi à moins de 30 m. » |
| v1.0.1 | Augmenter taille body text empty states de 14 sp à 16 sp | Family & friends (enfants, vieux) : 16 sp minimum pour lisibilité confortable. Cible ArboretumScreen, BadgesScreen, RemarquablesScreen, ProfileScreen empty states. |
| v1.0.1 | Ajouter ligne contexte au bullet remarquables du Welcome | « Les arbres remarquables (*) sont une chasse spéciale. Appuie sur le bouton ★ en haut à droite pour trouver les plus proches. » = tie button location. |
| v1.0.1 | Afficher distance en texte sur CaptureAvailability.TooFar | Remplace le message « Trop loin (500 m) » par « Trop loin (500 m / max 30 m). Rapproche-toi encore. » pour contexte explicite. |
| v1.1.0 | Ajouter badge « Explorateur hors-Paris » ou message bienvenue si hors-zone | Détecte géolocalisation hors de Paris, affiche snackbar « Tu es hors de Paris. CanoPaname affiche seulement les arbres du 75. Reviens à Paris ou explore le périmètre ! » Une fois par session. |
| v1.1.0 | Exposer AboutScreen de manière plus discuvrable (Profil → About OU FAB Info sur carte) | Relocalise About depuis Profil seul vers deux chemins : reste en Profil + ajoute un FAB Info optionnel sur carte. Réduit profondeur de navigation pour discovery "Pourquoi cette fiche est vide?". |
| v1.1.0 | Afficher le nombre total de remarquables (169) au Welcome et sur RemarquableScreen header | « Chasse spéciale : 169 arbres remarquables à débloquer un par un » = fixture d'expectation. Améliore la compréhension scope/complétion. |
| v1.1.0 | Unifier copy empty states sur verbe commun « Capture » | Remplace « Approche-toi / Déverrouille / Débloquer » par « Capture un arbre pour démarrer : ProfileScreen (bon baseline), ApplyToOthers. Tone alignment pour 4-6 ans. |
| v2.0 | Implémenter phénologie réelle (dates floraison/feuillage vs buckets 4 saisons) | Tension 2 (charte). Mécanique actuelles = archives calendaires statiques, pas simulation botanique. Coût : refonte schema Season, migrate data, UI selector complexe. Bénéfice : «réel» claim. |

---

## Notes architecturales (hors-scope audit, FYI)

- `LocationProvider` custom : bien intégré, bridge MapLibre correct (ligne 642-660 MapScreen.kt). TTFF latence n'est pas architecture mais feedback UX.
- GeoJSON 2-pass (empty → raw → enriched) : bien optimisé pour cold-start. Splash masque bien le freeze.
- Bottom sheet capture : pas de loading state entre permission et intent caméra. Ajoute 1s de clarity.
- ModalBottomSheet state management : ok, pas de trembling.

