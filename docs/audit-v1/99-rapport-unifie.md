# CanoPaname v1.0 — Audit produit & UX (synthèse)

> Synthèse curée des 6 rapports d'angle. Méthode et règles de coupe : voir `00-charte-audit.md`. Rapports d'angle complets : `01` à `06`.

## Verdict consolidé

CanoPaname v1.0 est un produit propre, esthétiquement cohérent, techniquement soigné, et qui honore à 90 % son pitch. Le code tient, l'identité visuelle tient, l'onboarding tient. **Les 10 % qui ne tiennent pas sont concentrés sur trois fronts** : le **first-run** (~7-10 secondes après le grant GPS sans feedback, fiche grise muette, bullet « remarquables » sans contexte), la **communication** (« 907 espèces » alors que 528 ont une fiche, « saisonnalité réelle » alors qu'elle est calendaire, « 100 % local » qui tait OpenFreeMap), et la **rétention long terme** (rythme de progression cassé entre J7 et J28, badges mal dosés, pas d'endgame). Ces trois fronts sont indépendants — on peut les traiter en parallèle sans dette croisée.

Le danger n'est pas que l'app soit jugée mauvaise. Il est qu'elle soit jugée *jolie mais inutile* après 3 semaines. La saisonnalité est aujourd'hui le seul amortisseur de rétention, et elle est cosmétique. Sans décision claire sur ce qu'elle doit devenir (vraiment phénologique, ou assumée calendaire), l'app vit ou meurt sur la motivation intrinsèque du joueur.

---

## Les 5 décisions structurantes

1. **Trancher la nature de la saisonnalité** — phénologie réelle (pivot v2 lourd, ~3-5 j de data + UI) ou « calendaire » assumé en communication (v1.0.1, 1 mot du README à changer). Statu quo = la promesse marketing reste en surplomb du produit.
2. **Donner de la visibilité à la progression** — % d'espèces sur le profil, liste « espèces manquantes », guidage GPS vers les espèces rares (pas seulement vers les remarquables). Sans ça, l'app fuit à J7-J28.
3. **Réparer les frictions du first-run** — feedback GPS pendant les 7-10 s post-permission, fiche grise qui rappelle la mécanique, feedback à l'annulation caméra. C'est ~1 j de travail qui change le TTFF (Time To First Success) du tout au tout.
4. **Trancher le modèle remarquables** — actuellement hybride asymétrique (entrée Arboretum séparée, fiche enrichie cachée tant que pas capturé, FAB ★ qui se comporte comme un search-tool). Décider : *espèce-boss* avec fiche pré-révélée, ou *vraie quête* avec radar persistant et progression continue.
5. **Aligner la communication sur la réalité** — 4 lignes du README + 1 ligne CHANGELOG à réécrire pour fermer les 3 mensonges communicatifs (907 / saisonnalité réelle / 100 % local). 30 minutes de travail qui résout plus de friction utilisateur que 10 fixes UX.

---

## Recos v1.0.1 — patch sans nouvelle feature

Effort agrégé estimé : **1-2 jours**. Cible : fixes UX, copy, micro-bugs visibles. Aucune touche au schéma, aucune nouvelle string en quantité.

| # | Reco | Source |
|---|---|---|
| 1 | **Réécrire la phrase « 907 espèces »** du README en « 907 espèces du dataset parisien à découvrir, dont 528 avec fiches botaniques enrichies ». | 03 |
| 2 | **Remplacer « saisonnalité réelle » par « saisonnalité calendaire »** dans le README — un mot, ferme le mensonge phénologique. | 03 |
| 3 | **Ajouter à `PRIVACY.md` et `README.md` la mention OpenStreetMap** — « les tuiles cartographiques viennent d'OpenStreetMap, sans envoi de données personnelles ». | 03 |
| 4 | **Ajouter au CHANGELOG v1.0.0 la mention « accessibles après capture »** sur les fiches remarquables. | 03 |
| 5 | **Réécrire le contenu de `UnknownContent`** (`ArbreDetailScreen.kt:71-73`) pour rappeler la mécanique : « Non capturé. Capture une espèce, tous les arbres du même type se déverrouilleront. Approche-toi à moins de 30 m. » | 01 |
| 6 | **Afficher la distance avec le max** sur `CaptureAvailability.TooFar` : « Trop loin (500 m / max 30 m). Rapproche-toi. » | 01 |
| 7 | **Étoffer le bullet remarquables du Welcome** : « Les arbres remarquables (★) sont une chasse spéciale. Appuie sur le bouton ★ en haut à droite pour trouver les plus proches. » | 01 |
| 8 | **Feedback visuel pendant le post-permission GPS** : pulse au FAB GPS ou snackbar « Localisation en cours… » pendant les 7-10 s avant 1er fix visible. | 01 |
| 9 | **Augmenter la taille `bodyMedium` des `EmptyState`** de 14 sp à 16 sp pour family & friends (ProfileScreen, ArboretumScreen, BadgesScreen, RemarquablesScreen). | 01 |
| 10 | **Remplacer l'icône `Search` du FAB remarquables par `Star`** (ou `ic_remarquable_badge`) — détail d'icône, mais c'est ce qui fait passer de « tool » à « quête ». | 02 |
| 11 | **Allonger la snackbar de distance remarquable de 3 s à 5 s** — actuellement le user oublie la distance avant de commencer à marcher. | 02 |
| 12 | **Haptique `LongPress` à l'ouverture du sheet `ArbreDetailContent`** — moment high-intent, aujourd'hui silencieux. | 05 |
| 13 | **Déplacer l'haptique de capture du post-INSERT vers le tap « Capturer »** — synchronise tactile et action perçue ; aujourd'hui l'haptique tombe après que la photo a déjà disparu. | 05 |
| 14 | **Feedback à l'annulation caméra** : snackbar « Capture annulée » + `Tick` haptique au lieu du `return@launch` silencieux dans `CaptureLauncher.kt:116-119`. | 05, 01 |
| 15 | **Ajouter label + timeout 60 s au `LinearProgressIndicator`** de `ProfileScreen.kt:370` (export/import) — actuellement barre 100 % sans contexte, sensation de freeze. | 05 |
| 16 | **Ajouter le compteur global au Profil** : « Espèces : 42 / 907 (4,6 %) » en plus des stats existantes — rend visible la progression. | 04 |
| 17 | **Étendre les screenshots du README de 3 à 6 frames** (Welcome, Map, Detail, **Arboretum, Badges, Profile**) — un visiteur GitHub ne doit pas croire que c'est juste une app de carte. | 06 |

---

## Recos v1.1.0 — feature increment additif

Sans casse de schéma backup ni de migration Room destructive. Cible : ajouter de la profondeur sans toucher aux fondations.

### A. Visibilité de progression et guidage

**Liste « Espèces manquantes » dans l'Arboretum.** Onglet supplémentaire qui liste les espèces non capturées, triées par proximité GPS de l'arrondissement où elles sont les plus présentes. Résout le scénario « 905/907 sans savoir lesquelles me manquent ». Affichage pur, pas de schéma touché. (rétention : 04)

**Bouton « Trouver le plus proche » sur la fiche d'espèce non capturée.** Affiche distance et direction sur la carte, en cohérence avec ce que fait déjà le FAB ★ pour les remarquables. Le différentiel actuel — radar pour remarquables, rien pour espèces rares — est un manque de symétrie. (rétention : 04)

**4 badges saisonniers.** « Botaniste de printemps », « Botaniste d'été », « Botaniste d'automne », « Botaniste d'hiver » — débloqués à 50 espèces capturées par saison. Donne une raison concrète de revenir au changement de saison. (rétention : 04, game design : 02)

### B. Combler les paliers de la courbe de progression

**Restructurer les badges de captures en 7-9 paliers.** Aujourd'hui 4 marches abruptes (1, 10, 50, 100) avec un mur Marcheur 50 → Centurion 100. Ajouter 75, 150, 250 lisse la pédagogie de progression. (game : 02)

**Pré-afficher la fiche remarquable enrichie même avant capture**, avec un bandeau « Pas encore découvert ». Asymétrie ludique actuelle gênante : capturer un platane remarquable révèle juste « Platane », pas la fiche enrichie. Ce changement aligne la promesse du CHANGELOG sur la réalité écran. (game : 02, cohérence : 03)

**Résumé fallback pour les 379 espèces sans Wikipedia.** Au lieu de « Pas d'info encyclopédique disponible », afficher « Famille X. Y individus à Paris. Informations botaniques limitées. ». Mieux qu'un vide. (game : 02)

### C. Partage minimal sans social

**Bouton de partage sur la fiche d'espèce** → export PNG (nom, photo échantillon, stats Paris). Permet le scénario family & friends « regarde ce marronnier » sans introduire de social feature ni de backend. Strictement local, partage via `Intent.ACTION_SEND` standard Android. (rétention : 04)

### D. Friction sensorielle et qualité perçue

**Haptiques micro sur les toggles** : `SeasonSelector`, `ArboretumScreen` viewMode, segmented buttons divers. `HapticFeedbackType.Tick` léger, jamais intrusif. Refactor préalable de `rememberCaptureHaptic()` en API générique `rememberHaptic()` exposant `light()` / `confirm()` / `error()`. (friction : 05)

**Mini-transition d'ambiance au switch saison** sur la carte. Aujourd'hui le `SeasonSelector` crossfade son icône, mais la carte se repeint sec. Une particule fade 300 ms relie le geste à un effet visuel. (friction : 05)

### E. Identité et discoverability

**Signature visuelle subtile en session.** Mini-platane (16-24 dp) en bout de TopAppBar de `MapScreen` ou `ProfileScreen`, ou wordmark compact dans un menu — porte le nom CanoPaname au-delà du splash sans casser l'épuration. (visuel : 06)

**Unifier l'espace lexical Arboretum / Catalogue / Pokédex** — garder « Pokédex » au marketing/README, choisir entre « Arboretum » et « Catalogue » pour l'UI (mon avis : Arboretum, car c'est la structure botanique réelle et le nom de l'écran principal). (cohérence : 03)

---

## Recos v2.0 — pivots structurels

Chaque pivot est exposé en *tension / décision / coût / bénéfice*. Ces recos ne se font pas à la volée — chacune mérite une phase dédiée.

### 1. Phénologie réelle — la grande décision

**Tension** : le README vend la « saisonnalité réelle » comme différenciateur clé, mais l'implémentation est un bucketing calendaire fixe (4 saisons) sans aucune simulation biologique. Cette tension est apparue dans 4 des 6 angles (UX, Game, Cohérence, Rétention) — c'est le signal le plus fort de l'audit.

**Décision** : intégrer des dates de floraison/feuillage par espèce (au moins pour le top 50, qui couvre 92 % des arbres), avec un calendrier interactif et un signalement contextuel sur la carte (« le tilleul fleurit dans 2 semaines »).

**Coût** : ~3-5 jours de data sourcing (TelaBotanica, FloreAlpes, Wikipédia FR a souvent les info de floraison) + ~2 jours d'UI calendrier + tests + bump majeur de version.

**Bénéfice** : la promesse marketing devient vraie. Multiplie l'engagement long terme (raison de revenir le 15 mars pour le tilleul). Crée un savoir botanique chez le joueur, pas juste une checklist. Différenciateur fort vs Pokémon GO.

### 2. Refonte du modèle remarquables

**Tension** : aujourd'hui hybride maladroit. Une entrée Arboretum séparée, mais une fiche enrichie cachée jusqu'à capture, avec un FAB ★ qui livre une snackbar 3 s plutôt qu'une vraie quête. Asymétrie avec les espèces régulières frustrante.

**Décision** : trancher entre deux modèles : (A) *espèce-boss* — fiche enrichie pré-révélée, capture est juste le « collector check-in » ; ou (B) *vraie quête au trésor* — overlay persistant ★ avec radar permanent, progression de chasse visible (« 12/169 », « le plus proche est à 240 m »), fiche révélée en deux temps (visible dès que vu sur la carte, riche après capture).

**Coût** : 2-3 jours UI + révision des recos 5/11 v1.0.1 (snackbar) + cohérence avec le nouveau modèle.

**Bénéfice** : transforme la chasse aux remarquables d'une *liste de courses* en une *boucle de jeu autonome*. Donne à l'app un endgame structurel pour les joueurs avancés (50/169 puis 100/169 puis 168/169 sont des paliers nourrissants).

### 3. Quêtes / défis hebdomadaires localisés

**Tension** : pas de relances, pas de notifs (par design, choix défendable), donc la rétention dépend uniquement de la motivation intrinsèque du joueur. Aux J28-J90, ça suffit rarement.

**Décision** : ajouter un système de défis hebdomadaires/saisonniers stockés localement, sans push (compatible vision « no dark patterns ») mais visibles quand le joueur ouvre l'app. Exemples : « Cette semaine, capture un arbre dans 3 arrondissements », « Ce printemps, trouve le tilleul en floraison ».

**Coût** : ~3-5 j (table `Quest` Room, écran dédié, UI de progression). Pas de backend.

**Bénéfice** : crée une **raison de revenir** sans empiler des notifs. Compatible avec la philosophie single-player. Module désactivable dans les paramètres pour les puristes anti-gamification.

### 4. Archive photographique temporelle

**Tension** : à J365+, l'app devient une galerie statique. Pas de photo comparative saisonnière, pas de timeline, pas de « ce marronnier en avril 2026 vs avril 2027 ». Les photos vieillissent, ensevelies dans Room, sans valeur narrative émergente.

**Décision** : autoriser plusieurs photos par capture (re-capture du même arbre à des saisons différentes), construire une UI de galerie multi-saison sur la fiche d'arbre individuelle, et un comparateur « avant/après ».

**Coût** : ~3 j (multi-photo schéma backup compatible v2 + UI galerie + tests migration).

**Bénéfice** : transforme l'app de Pokédex de collection en *portfolio botanique pérenne*. Donne une raison structurelle de revenir aux mêmes arbres chaque année. Compatible avec la philosophie « promenade lente ».

### 5. Contenu déblocable par arrondissement

**Tension** : les badges « Tour des arrondissements » existent (10/20 arrondissements) mais c'est binaire. Pas de granularité fine, pas de boucle « je maîtrise le 5e ».

**Décision** : ajouter un système « Maîtrise » par arrondissement — un arrondissement est « maîtrisé » quand toutes ses espèces dominantes et tous ses remarquables sont capturés. Affichage carte chromatique (arrondissements maîtrisés en vert, partiels en jaune, vierges en gris).

**Coût** : ~2 j (parser arrondissement depuis OpenData, UI filtre, badge « Maître du 5e » par arr).

**Bénéfice** : nouveau moteur de rétention spatial, fait de Paris un « jeu à 20 niveaux ». Compatible avec la promesse single-player et family & friends (« papa a tout le 11e, maman a tout le 5e »).

---

## Tensions héritées non résolues

Quatre questions que l'audit a regardées sans trancher — elles appartiennent au porteur du produit, pas aux experts d'angle.

1. **Notifications push : vertueux ou autodestructeur ?** Aucune permission `NOTIFICATION` dans le manifest, choix assumé « no dark patterns ». Mais la rétention en pâtit clairement (rapport 04). Le compromis « digest mensuel opt-in » est défendable mais introduit de la complexité non-mineure (DataStore opt-in, notif scheduler) pour un bénéfice difficile à mesurer en single-player. La décision dépend de la philosophie personnelle du porteur — pas de bonne réponse universelle.
2. **Single-player vs family & friends — le partage.** L'app n'a aucun hook de partage (pas d'`Intent.ACTION_SEND`, pas d'export image fiche). Reco v1.1.0 propose un partage PNG borné, mais reste à voir si c'est jugé compatible avec la promesse « pas de social ». Frontière fine entre *partager une découverte* (acceptable) et *commencer à empiler des features sociales* (interdit).
3. **Le « 907 espèces ».** Faut-il afficher dans l'UI 907 (vérité dataset, donc la promesse marketing) ou 528 (vérité fiches enrichies, donc la promesse expérience) ? Reco v1.0.1 #1 ferme la communication, mais la question UI reste : le compteur Arboretum « X / 907 » envoie-t-il le bon message ?
4. **Le déblocage de 38 147 platanes.** Moment fort suivi d'anticlimax (rapport 02). On ne peut pas le « réparer » sans repenser le modèle de capture en profondeur (variations entre individus d'une même espèce ?). Probablement à laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax.

---

## Annexe — les 6 angles en 1 paragraphe chacun

**01 — UX & parcours.** L'app est naviguable et fonctionne « sur rails ». Trois frictions cassent le first-run : gap GPS de 7-10 secondes sans feedback, fiche grise muette qui ne rappelle pas la mécanique, bullet « remarquables » trop abstrait. Architecture (NavHost, parcours capture, empty states) tient. 5 recos v1.0.1 + 4 v1.1.0 + 1 v2.0 (phénologie). Rapport complet : `01-ux-parcours.md`.

**02 — Game design & boucle ludique.** Boucle efficace en *micro* (capture → bascule pins verts → badge), érodée en *macro* (badges mal dosés, mur 50→100, saisonnalité cosmétique, FAB remarquables qui ressemble à un search-tool). 4 badges sont triviaux ou inaccessibles, 2 sont aléatoires, 1 est mal défini. Le déblocage de 38 147 platanes au J+3 est le seul moment fort, suivi d'anticlimax. 3 recos v1.0.1 + 4 v1.1.0 + 3 v2.0. Rapport complet : `02-game-design.md`.

**03 — Cohérence produit & identité.** Pitch fort, mais 3 mensonges communicatifs critiques : « 907 espèces » (528 avec fiches), « saisonnalité réelle » (calendaire), « 100 % local » (OpenFreeMap tu). 1 mensonge moyen sur les fiches remarquables conditionnées. Les badges seuillés ne contredisent pas « pas de classement » (intrapersonnel, OK). Espace lexical Pokédex / Arboretum / Catalogue fragmenté mais tolérable. Rebranding « Arbres » → « CanoPaname » réussi. 4 recos v1.0.1 + 1 v1.1.0 + 1 v2.0. Rapport complet : `03-coherence-produit.md`.

**04 — Replay value & rétention.** Rétention structurellement fragile, dépendante de la seule saisonnalité (qui est cosmétique). Loi de Pareto : 100 espèces = 92 % du dataset, donc J7 = plateau. Endgame inexistant : pas de liste « espèces manquantes », pas de radar pour rares, badges impossibles (`année_complete`, `botaniste_confirme` à 200 espèces). Sans relances ni structure de chasse, fuite à J28-J90 quasi-certaine. 1 reco v1.0.1 + 6 v1.1.0 + 3 v2.0. Rapport complet : `04-retention-replay.md`.

**05 — Performance perçue & friction sensorielle.** Animations ponctuelles soignées (splash crown cascadée, célébration capture, ambiance saisonnière) avec tokens `ArbresMotion` cohérents. Mais l'haptique est solitaire (un seul `LongPress` post-INSERT, mal calé), les loaders mid-session sans label, le cold-start masque ~700 ms de freeze sans indication, l'annulation caméra silencieuse. Note globale **3,7/5**. 4 recos v1.0.1 + 4 v1.1.0 + 0 v2.0. Rapport complet : `05-friction-sensorielle.md`.

**06 — Cohérence visuelle, iconographique, typographique.** Identité visuelle solide : palette arboricole maîtrisée, Fraunces SemiBold dosée juste, Material Outlined uniforme, dark mode propre. Aucune incohérence iconographique. Seul angle faible : l'identité « CanoPaname » est exclusivement textuelle (splash + About) — jamais portée visuellement en session. 2 recos v1.0.1 + 2 v1.1.0 + 0 v2.0. Rapport complet : `06-coherence-visuelle.md`.
