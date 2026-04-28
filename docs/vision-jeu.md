# Vision « Mode jeu » — arbitrage avant Sprint C

Note d'arbitrage écrite avant d'attaquer la phase 2 du `ROADMAP.md` (capture + Arboretum). Inspiration validée : **Pokémon GO épuré**. Public visé : single-player + family & friends, pas de leaderboard. Stockage local seulement.

**Données disponibles** (vérifié dans `ArbreEntity.kt`) : genre, espèce, cultivar, nom commun, hauteur, circonférence, ★ remarquable, adresse, lat/lon. **Pas d'âge ni de date de plantation.** La circonférence n'est pas un proxy d'âge fiable (varie selon l'espèce). Toute mécanique fondée sur l'âge est donc exclue d'office.

## 1. Mécaniques Pokémon GO retenues

- **Capture par proximité GPS** — c'est ce qui transforme une carte en jeu.
- **Arboretum (= Pokédex)** avec % de complétion par espèce — boucle de progression long terme.
- **Badges narratifs ancrés dans le territoire** (« tour des marronniers du Luxembourg », « tous les remarquables du 5e ») — rétention sans pression compétitive. Pas de badges fondés sur l'âge.

## 2. Mécaniques explicitement écartées

- **Combats / raids / gym** — registre action + concurrence, hors-sujet pour des arbres.
- **Échanges entre joueurs / leaderboards** — éliminés d'office par le scope « family & friends ».
- **Anti-cheat anti-spoof** — coût d'ingénierie disproportionné pour usage perso.
- **Achats in-app, événements live serveur** — pas de backend, pas de monétisation.
- **Système d'XP / niveaux du joueur** — ajoute du grind sans valeur narrative.

## 3. Spécificités arbre à exploiter

- **Saisonnalité 4 formes** (`ROADMAP.md:58`) : la même espèce capturée en hiver vs en mai compte comme 2 entrées. Multiplie naturellement la collection sans inflation artificielle.
- **Taxonomie multi-niveaux** : genre / espèce / cultivar — déjà en data. **L'espèce est le niveau atomique du « Pokédex »**. Capturer une espèce débloque tous les arbres de cette espèce. % de complétion principal = % d'espèces découvertes ; secondaire = % de genres dérivés. Le cultivar reste affiché sur la fiche pour les curieux, mais n'intervient pas dans les mécaniques de jeu (raisons : 18 % seulement des arbres ont un cultivar renseigné contre 97 % pour l'espèce ; deux cultivars d'une même espèce sont indiscernables à l'œil nu pour un public non-spécialiste ; le grain espèce produit des moments de jeu bien plus forts — capturer un *Platanus x hispanica* fait basculer 38 147 pins d'un coup).
- **Caractère « remarquable »** : 217 855 arbres dont une poignée ★. Traitement à part (cf. §5) — chaque remarquable est une entrée individuelle dans l'Arboretum, indépendante de son cultivar.
- **Géographie réelle parisienne** : quêtes par arrondissement / parc. Pokémon GO a des biomes génériques ; nous avons un territoire identifié et chargé culturellement.

## 4. Nature de la « capture » — tranchée

Recommandation : **photo + GPS au moment du tap**, pas EXIF strict.

- Rejeté « j'étais là » GPS-only : trop maigre, l'app devient un tracker, zéro souvenir personnel.
- Rejeté mini-quiz d'identification : nécessite un asset visuel par espèce qu'on n'a pas (il faudrait sourcer Wikimedia, gros chantier), casse le ton détente. À coupler à la photo plus tard si envie, pas comme cœur.
- La photo génère un asset perso (le souvenir de l'arbre) qui justifie l'effort de sortir le téléphone. L'EXIF complique (lecture des tags, photos importées de la galerie) sans gain : valider via le GPS du device au moment du tap « Capturer », distance < 30 m de la position OpenData. Plus simple, garantie anti-tricherie informelle suffisante pour family & friends.

## 5. Boucle de jeu — qu'est-ce que le joueur voit en ouvrant l'app ?

C'est ici que notre jeu se distingue de ses inspirations. Pour situer :

- **Pokémon GO** : carte semi-abstraite + radar + apparitions aléatoires. Le joueur attend que quelque chose surgisse autour de lui.
- **Space Invaders** : action temps réel, viseur, pression. Pas du tout notre registre.

**Notre choix : la carte EST l'écran d'accueil et le hub de jeu.** Continuité directe avec la phase 1 — la carte n'est pas une vue préliminaire, c'est le terrain de chasse permanent.

### Découverte par espèce (mécanique structurante)

Au premier lancement, **tous les pins de la carte sont gris**. Ils sont visibles et cliquables, mais leur fiche est anonyme : « Arbre inconnu » + un bouton **Capturer** actif si < 30 m. Aucune information taxonomique n'est révélée tant que l'espèce n'a pas été capturée.

Quand le joueur capture son premier arbre d'une espèce X (photo + GPS), **tous les arbres de l'espèce X sur la carte basculent en pin vert**, et leur fiche complète devient accessible (genre, espèce, cultivar pour info, hauteur, circonférence, adresse, etc.). C'est la mécanique principale de progression : chaque capture éclaircit une partie du territoire — et certaines captures (un platane, un marronnier) débloquent des dizaines de milliers de pins d'un coup.

Conséquence : l'**Arboretum n'est pas seulement une vue de progression**, c'est la **clé qui débloque le contenu de la carte**. Au début, Paris est un brouillard de pins gris ; chaque espèce capturée révèle des centaines à dizaines de milliers d'arbres.

### Cas spécial : arbres remarquables

Les remarquables (★) ne suivent pas la règle ci-dessus. **Chaque remarquable est une entrée individuelle dans l'Arboretum**, comme s'il était une espèce à part. Capturer l'espèce d'un remarquable ne le débloque pas : il reste en pin gris « Arbre inconnu » jusqu'à ce qu'on l'ait capturé physiquement.

Pour aider à les trouver sans casser la mécanique de découverte : un **bouton ★ permanent au-dessus de la carte**. Un tap déplie un petit overlay : « Plus proche remarquable non découvert : 240 m ». Pas d'affichage permanent (recalcul GPS continu = bruit visuel + batterie), mais accessible en un geste quand on chasse activement.

### Boucle de jeu

1. Ouverture app → carte centrée sur la position GPS (livré Sprint A). Pins gris (espèce non découverte) ou verts (espèce capturée), remarquables non découverts en gris quelle que soit leur espèce.
2. Tap sur un pin :
   - **Pin gris** → fiche neutre « Arbre inconnu » + bouton **Capturer** si < 30 m.
   - **Pin vert** → fiche complète (taxonomie, hauteur, adresse, etc.).
3. Tap **Capturer** → ouverture caméra système. Photo prise → retour fiche, capture sauvegardée. Si c'était le premier arbre de l'espèce : tous les pins de cette espèce passent au vert (sauf remarquables). Si c'était un remarquable : ce pin précis passe au vert.
4. Bouton ★ au-dessus de la carte → overlay avec distance au remarquable non découvert le plus proche.
5. Pas de notifications push dans le MVP capture (cf. `ROADMAP.md:54`, prévu plus tard).

**Conséquence design.** Notre jeu est plus proche d'une **collection topographique** (Foursquare-style check-in + Pokédex) que d'un jeu d'apparition AR. C'est calme, ça récompense la promenade lente, et ça évite tout le contenu serveur live que Pokémon GO doit fabriquer pour combler les zones rurales — ici, Paris a déjà 217 855 arbres réels, fixes et géolocalisés. Le gameplay n'a rien à inventer ; il reflète ce qui existe.
