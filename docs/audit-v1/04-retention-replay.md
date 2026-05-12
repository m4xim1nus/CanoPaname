# Angle 04 — Replay value & rétention — CanoPaname v1.0

## Verdict

CanoPaname a une **rétention structurelle fragile** qui dépend entièrement de la saisonnalité. La courbe de progression (10 % du dataset en 1 capture, 50 % en 10, 95 % en 135) crée une fausse accélération initiale puis un plateau frustrant. Sans mécanique de re-engagement saisonnier, notifications de relance, ou contenu débloquable post-95 %, l'app transitera à l'abandon dès J30. La saisonnalité est la **seule** surface de jeu long terme — elle ne suffit pas à elle seule. Le user doit être poussé activement vers les saisons futures, et les remarquables ne sont pas assez nombreux ni assez guidés pour remplir ce rôle.

## Curbe de progression : J1 → J90

### J1 : Euphorie du premier déblocage

**Moteur** : découvrir qu'une capture débloque des dizaines de milliers de pins. Capturer un marronnier (38 147 instances à Paris) = 10 % du dataset géré. C'est fort. Les 5 premières captures (marronnier, platane, érable, châtaigne, févier) = ~67 000 arbres = 31 % du visible. **Le user a l'impression de progress ultra-rapide.**

**Durée** : 1–3 jours si l'app est dans les appels quotidiens. Promenade parisienne + ouverture app = naturel.

**Problème** : ce momentum est un piège. Le user se dit « j'en ai plein à découvrir ». Spoiler : il n'en a pas.

### J7 : Premier plateau

**Moteur** : le user a capturé ~25–30 espèces, atteint ~80 % du dataset. Loi de Pareto appliquée méchamment : les 100 espèces les plus abondantes = 92 % des arbres. Le reste, 800+ espèces, = 8 %.

**Réalité sur le terrain** :
- Top 10 espèces : 38 147 + 20 030 + 10 909 + 7 655 + 6 645 + ... = ~100 000 arbres.
- Top 50 : ~172 000 arbres (80 %).
- Top 135 : ~201 000 arbres (95 %).
- Top 290 : ~212 000 arbres (99 %).

Le user découvre que « 100 % » n'existe pas — certaines espèces ne croisent jamais sa route. Arboretum affiche « 37/907 » et le scroll est douloureux.

**Espace de jeu restant** : ~30 % du dataset en volume = 64 000 arbres, mais 87 % des espèces restantes, toutes rares. Chacune = 1–50 captures max.

**Rétention** : **début de fuite**. Sans GPS guidé vers les zones rares, sans notif « nouveau linden découvert en Seine-Saint-Denis », le user doit activement **chercher** la progression. Ça n'est plus passif (« regarde combien j'ai découvert aujourd'hui »), c'est actif (« je dois aller à Montmartre pour l'if »). La plupart abandonnent ici.

### J28 : Plateau stérile

**Moteur absence** : le user a 40–60 espèces, 85 % du visible. Les badges décent lentement (« Promenade » @ 10, « Marcheur » @ 50, puis rien jusqu'à « Centurion » @ 100 captures — soit 2–3 mois). Les remarquables (10/50 badges) restent invisibles car éparpillés. Aucun sprint visible.

**Ce qui manque pour relancer** :
1. Notification (« une nouvelle saison commence ! Quels arbres changent ? ») — **absence complète**.
2. Digest ( « Cette semaine, 3 espèces rares trouvées en 15e ») — **absence**.
3. Radar GPS (`"Tilleul 200 m"`) — **n'existe pas pour les espèces rares**, seulement pour remarquables.
4. Progression visible par saison (« Hiver : 0/907, Printemps : 23/907 ») — **l'Arboretum évolue par saison, but pas de % globaux saisonniers**.

**Rétention** : **dépend entièrement de la saisonnalité**.

### Saisonnalité : le seul amortisseur

La même espèce capturée en hiver ≠ en été = 2 entrées dans Arboretum selon saison sélectionnée. **C'est la seule raison de rouvrir après J28.**

**Le calcul brutal** :
- Supposons user capture 40 espèces en printemps.
- En été, il peut re-tenter les mêmes 40 + chercher nouvelles (différentes fenêtres de floraison/feuillage selon espèce).
- **Réalité fournie par le code** : 4 saisons fixes (décembre–février, etc.). Pas de phénologie (dates de floraison par espèce et année). Les saisons sont des **buckets d'archive**, pas une simulation.

**Verdict tranché** : saisonnalité = **moteur de rétention à moyen terme (J28 → J90), mais cosmétique à long terme**.

Pourquoi ? Car :
1. Après J90, le user a vu le cycle entier. À moins d'avoir raté des espèces intentionnellement, il n'y a rien de neuf à l'été Y+1.
2. 169/183 remarquables = minuscule cible. Une fois capturés, pas de raison de revenir.
3. Les badges les plus ambitieux demandent 12 mois consécutifs (« Année complète ») ou 50 remarquables. Pas de sprint mensuel.

### J90+ : Endgame inexistant

Quatre scénarios à J90 :

**Scénario A : User a 95 % espèces, 168/183 remarquables. Un seul platane d'hiver à capturer au Bois de Vincennes.**

L'app n'a aucune mécanique pour l'aider. Pas de waypoint, pas de « arbres restants listés triés par proximité GPS ». L'Arboretum affiche « 905/907 » mais aucune liste « esp non capturées ». Le user rage-quit.

**Scénario B : User a complété son Arboretum (907 espèces × 4 saisons = 3 628 captures théoriques).**

Une fois que la réalité devient un catalogue complet, qu'est-ce qui justifie une nouvelle ouverture ? L'app devient une galerie d'archive. Photos stockées dans les captures, affichées chronologiquement, mais sans hook narratif. Pas de « parcours du week-end » exportable. Pas d'album « Tous les marronniers que j'ai croisés ». Pas de comparaison temporelle (« Ce marronnier en avril 2026 vs avril 2027 »).

**Scénario C : User a raté des espèces avant novembre → badge « Année complète » impossible à débloquer.**

Il n'a aucun signal de rattrapage. Le badge reste ????? pour l'éternité. Aucune relance « tu as 11 mois consécutifs, il te manque janvier — reviens en janvier prochain ».

**Scénario D : User a capturé 169 remarquables, 0 autres badges majeurs.**

Rien d'autre à viser. Les stats globales n'ont pas de % visible (« Tu as 75 % des espèces »). Aucune barre de progression. Juste un compteur brut.

## Saisonnalité : moteur ou cosmétique ?

### La théorie (vision-jeu.md)

> « Saisonnalité réelle (un platane en mai ≠ en novembre) multiplie naturellement le contenu. »

Vrai. Cela génère 4× la surface de jeu théorique.

### La réalité (code + terrain)

1. **Phénologie absente** : les 4 saisons sont des buckets calendaires fixes, pas une simulation. Pas de date de floraison par espèce. Tous les maronniers fleurissent au même mois chaque année, peu importe l'année.

2. **Les espèces changeantes par saison** : vrai dans la nature (feuillaison, floraison), mais **pas rendu** dans CanoPaname. Pas de photo saisonnière de comparaison. Pas de « voici ce marronnier en hiver : chauve, vs été : feuillé ». L'UI capture une seule photo ; l'app ne gère pas les galeries saisonnières.

3. **Mécanique re-capture** : possible techniquement (pas de contrainte unique `(arbreId, season)`), affichée par saison dans Arboretum. Mais l'incentive est **zéro**. Pas de badge, pas de bonus. Juste « t'as capturé ce marronnier 4 fois, une par saison ». Personne ne le remarque.

4. **Effet réel J28 → J90** :  le user peut **théoriquement** re-explorer pour l'été. Ça le ramène. Mais à J90, après avoir vu les 4 saisons, **c'est fini**. Pas d'âge du jeu à simuler (« les arbres grandissent »). Pas de photo historique.

### Verdict tranché

Saisonnalité = **semi-moteur**. Elle étire la courbe de rétention de J7 → J90 mais n'offre rien post-J90. Sans phénologie réelle, sans photo saisonnière multi-capture, sans incentive badge, c'est un **délai plutôt qu'une raison**. Le user revient en été parce qu'il n'a rien d'autre à faire, pas parce que l'app le demande activement.

## Relances absentes : vertueux ou autodestructeur ?

### État actuel

- **Zéro notifications push** (pas de NOTIFICATION permission dans AndroidManifest.xml).
- **Zéro digest hebdomadaire**.
- **Zéro rappel saisonnier** (« nouveau printemps commence, viens compléter tes captures »).
- **Zéro " streaks "** (tu as joué 5 jours d'affilée, continue !).

### Raison probable

« Family & friends, no dark patterns. Pas d'addiction engineering. »

C'est défendable **en absolu**. Mais en **relatif** à la rétention, c'est autodestructeur.

### Le débat

**Vertueux**:
- User ne reçoit pas de notification à 21h quand il dort.
- Pas d'interface parasitaire (badges push = streaks visuels).
- Engagement authentique : on ouvre parce qu'on **veut**, pas parce qu'une notif nous y force.

**Autodestructeur**:
- User oublie l'app après J7.
- Pas de **cue** (au sens Fogg Behavior Model) : sensation + proximité + motivation = action. Sans la notification, la sensation manque.
- Les jeux de collection (Strava, Duolingo, Goodreads, Untappd) prospèrent sur un équilibre « notif + gamif légère ». Pas de notif = chute 70–80 % rétention.
- Remarque spécifique : Pokémon GO **dépend** de notifications événementielles. CanoPaname en refuse. C'est une décision design valide, mais elle a un coût.

### Compromis défendable

Pas de push notifications, **mais** :
- Digest push mensuel non-personnalisée (« Le printemps arrive ! », délivré le 1er mars).
- Ou un appel au retour : quand l'user ouvre le Profil et voit « 0/907 espèces capturées cette saison », l'app dit « quand tu veux réessayer, on sera là ». (Actuellement, il n'y a aucun CTA).

Actuellement, c'est vertueux jusqu'à J7, puis autodestructeur.

## Remarquables : chasse au trésor ou liste de courses ?

### Mécanique en place

1. Bouton ★ au-dessus de la carte.
2. Tap → overlay : « Plus proche remarquable non découvert : X m ».
3. User navigue GPS vers le remarquable, capture.
4. Badge @ 10 et @ 50 remarquables.

### Chasse au trésor : oui, mais boîtée

**Succès** :
- 183 remarquables = 183 objectifs discrets, éparpillés sur Paris.
- Le GPS guidance (« 240 m ») crée une "hunting loop" : ouverture app → tap ★ → marche vers GPS → capture → libération dopamine.
- Remarquables ≈ POI Pokémon GO, mais arbre réel et stationnaire = moins d'éparpillement aléatoire.

**Problème d'endgame** :
- 183 / 169 annoncé : discrepancy. Supposons 169 vraiment accessible.
- À 168/169, le user sait qu'il manque 1. Lequel ? L'app ne dit pas. Doit-il fouiller les 20 arrondissements ?
- Pas de liste « remarquables non capturés triés par proximité ». Pas de « dernier découvert : il y a 3 mois, à 2.5 km de chez toi ».
- Badge « Légende » @ 50 atteint en ~1 mois de jeu intensif. Puis quoi ? Aucun badge ne dépasse 50. Plafond trop bas.

### Liste de courses vs chasse

**Résultat J28** : après avoir capturé les 10 remarquables les plus proches (borne à ~3 km de Paris intramuros), le user a atteint le badge « Chasseur ». Les 159 restants sont **optionnels et non incités**. C'est une **liste de courses**, pas une chasse.

L'app n'incite pas à continuer. Aucun badge de progression lissée (« 20/50 remarquables »). Aucun radar permanent pour les espèces rares (seulement pour remarquables).

## Family & friends : pas de partage

### État actuel

Aucune feature de partage (pas d'Intent.ACTION_SEND, pas de screenshot intégré, pas d'export photo).

Scénario : user capture un arbre magnifique. Veut-il le montrer à un ami ? Doit il :
- Aller dans Profil → Sauvegarde → Exporter ZIP (lourd).
- Ou : passer par Galerie, chercher la photo.
- Pas de partage d'une **fiche espèce** (« Regarde ce marronnier en détail »).
- Pas de partage d'un **parcours du week-end** (« J'ai marché 5 km et capturé 12 espèces »).

### Périmètre exclure ou reco ?

La charte dit : « Scénario family & friends ». Ici, il y a un manque. Mais c'est **hors-périmètre angle 04** (rétention long terme), car le partage affecte surtout la boucle dans la session (viral + social credit) = **angle 02**.

**Mention courte** : Family & friends est un carrefour entre angle 02 (boucle) et angle 04 (rétention). Aucun hook de partage n'existe. Reco pour v1.1.0 : export image d'une fiche espèce (bouton partage simple → PNG).

## Endgame : que faire à J365+ ?

### Scénario 1 : User a complété l'Arboretum (907 espèces × 4 saisons = 3 628 captures)

L'app devient galerie. Pas de raison de revenir. Vérification : " ai-je tous les remarquables ? ". Si oui, fin. Si non, il faut chercher activement. Pas de guidance.

### Scénario 2 : User a 95 % du contenu visible

Rester à 905/907 est frustrant. L'app ne dit pas quelles espèces manquent. Arboretum affiche « 905/907 » mais pas de liste filtrée « non-capturées triés par proximité arrondissement ».

### Reco structurelle

Ajouter une **liste " Espèces manquantes "** avec :
- Nom scientifique + nom commun.
- Nombre d'arbres à Paris.
- Arrondissements où trouver.
- Bouton " naviguer vers le plus proche ".

Cela **résout** le problème d'endgame et donne une raison de revenir post-J90.

Tier : **v1.1.0** (affichage pur, pas de schéma touché).

## Pistes considérées et écartées

| Idée | Raison |
|---|---|
| Achats in-app (cosmétiques saisonniers, filtres) | Éliminé par contrainte « no monetization ». |
| XP et niveaux du joueur | Ajoute du grind. Pas de valeur narrative (« pourquoi je monte de niveau ? »). Vision-jeu.md l'a déjà rejetée. |
| Échanges joueur (« partage ma découverte ») | Pivot social = v2.0. Hors scope. |
| Phénologie réelle (dates de floraison) | Coût d'ingénierie = contenu par espèce + calendrier variable. Rejeté en v1, plausible v2. |
| Photo comparative saisonnière (« cet arbre en avril vs novembre ») | Nécessite galerie multi-capture par arbre + UI de comparaison. v1.1.0 si UI simple. |
| Streaks visuelles / notifications push | Rejeté "no dark patterns", mais coût énorme à la rétention. Voir section relances. |
| Badges par saison (« Botaniste printanier ») | Réduit la frustration plateau. v1.1.0 : ajouter 4 badges saisonnier threshold (50 espèces par saison chacun). |

## Recos par tier

| Tier | Titre | 1 phrase |
|---|---|---|
| v1.0.1 | Ajouter % espèces au Profil | Afficher « Espèces : 42/907 (4.6 %) » aux stats globales et saisonnières pour rendre visible la progression. |
| v1.1.0 | Liste « Espèces manquantes » | Ajouter un onglet Arboretum « À découvrir » listant les 2 espèces non capturées avec count Paris et arrondissements top. |
| v1.1.0 | Navigation GPS vers espèce rare | Bouton « trouver le plus proche » sur la fiche espèce non capturée → affiche distance et direction sur la carte. |
| v1.1.0 | 4 badges saisonniers | Ajouter « Botaniste printemps/été/automne/hiver » débloqués à 50 espèces capturées par saison pour inciter la re-exploration. |
| v1.1.0 | Partage image fiche espèce | Bouton partage sur chaque fiche espèce → export PNG (nom, photo échantillon, stats Paris) pour partage simple. |
| v1.1.0 | Digest mensuel (optional) | Au 1er de chaque mois, notif non-personnalisée « Printemps arrive! Quels arbres changent? » si opt-in Profil. |
| v1.1.0 | Comparaison avant/après saison | Profil : ajouter un mini-tableau « Progression par saison » montrant captures hiver/printemps/été/automne pour voir l'évolution visuelle. |
| v2.0 | Phénologie réelle | Intégrer dates de floraison/feuillage par espèce pour créer un calendrier interactif (« Tilleul fleurit début juin »). Coût : +5 champs metadata + UI calendrier. Bénéfice : saisonnalité devient scientifique, pas cosmétique. |
| v2.0 | Archive photographique temporelle | Réimporter photos anciennes (EXIF) + générer timeline (« Cet marronnier en avril 2024 vs 2025 »). Coût : galerie multi-capture, comparateur visuel. Bénéfice : endgame = portfolio pérenne, raison de revenir annuellement. |
| v2.0 | Contenu déblocable per-arrondissement | « Maîtrise le 5e » = capture toutes espèces du 5e + tous remarquables. Coût : badge parser arrondissement + UI filtre. Bénéfice : nouveau moteur rétention spatial. |

---

**Calibrage final** : sans changement, l'app fuit à J7–J28. Avec les recos v1.1.0 (% visible + liste manquantes + badges saisonnier), elle tient jusqu'à J90. Avec v2.0 (phénologie + archive), elle devient une app pérenne.
