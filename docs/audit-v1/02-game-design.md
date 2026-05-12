# Game design & boucle ludique — CanoPaname v1.0

## Verdict

La boucle est ludiquement efficace en *micro* — chaque capture fait basculer les pins au vert, chaque badge débloqué gratifie, chaque remarquable chassé a un sens territorial. Mais elle s'érode rapidement en *macro*. À 4 semaines réelles à Paris : les 907 espèces se concrétisent mal (40-50 capturées), les badges de volume (Centurion : 100 captures) restent lointains, la saisonnalité reste cosmétique, l'Arboretum se transforme en checklist silencieuse. L'engagement dépend de quelle histoire le joueur se raconte — si c'est « j'explore Paris lentement » ça marche ; si c'est « je dois débloquer 15 succès », l'engagement s'étiole.

## 1. La triade ludique : carte → capture → Arboretum

### Carte comme hub

La vision-jeu promettait « la carte EST l'écran d'accueil et le hub de jeu ». C'est livré — MapScreen est le point d'entrée post-onboarding, les pins gris/verts reflètent l'état de découverte en temps réel (`capturedSpeciesIndices(currentSeason)`, `applyDiscoveryColor` appliquée à chaque capture). **Verdict : efficace.**

La révélation des pins fonctionne : capturer un Platane (*Platanus x hispanica*, espèce 7) débloque 38 147 pins d'un coup `(SpeciesInfo.kt:stats.count)`. C'est un moment fort — mais **c'est le seul des 907 espèces**. Les autres espèces ont des comptes médians entre 5 et 500 arbres. Aucun autre déblocage n'a cet impact climactérique. Donc il y a un pic spectaculaire à J+3, puis retomba dans l'ordinaire.

### Capture comme transaction

Photo + GPS <30m (enregistrés, validés par `isFirstOfSpecies` avant l'INSERT). `CaptureRepository.insertCapture()` émet `CaptureEvent` qui déclenche l'animation climax (CaptureCelebrationOverlay). **Techniquement impeccable.** Le moment de capture est protégé : pas possible de spam-capturer le même arbre en 0.1 s, pas de levée d'épée sur les géo-spoofing.

Mais dans une session de 15 minutes typique en promenade parisienne (30-40 arbres visibles proches, 0-2 nouveaux), tu ne captureras qu'une ou deux espèces nouvelles. Donc la «gratification capture» demande des sessions stratégies (« je vais au Jardin des Plantes pour débloquer 10 espèces »), ce qui est HORS de la promesse « promenade lente ». Tension palpable.

### Arboretum comme galerie-progression

L'Arboretum liste les espèces capturées filtrées par saison (ArboretumScreen.kt). Le header affiche le % de complétion : « X / 907 espèces découvertes ». **Deux problèmes sérieux :**

1. **Confusion nomenclature** — le README promet « 907 espèces » ; l'Arboretum affiche ce dénominateur. Mais les remarquables ne comptent pas comme espèce (BadgeEvaluator.kt:41-42 : `if (!capture.remarquable)`). Donc l'UI suggère faussement qu'il y a 907 espèces à débloquer ET 169 remarquables en sus. C'est faux : les remarquables REMPLACENT leur espèce parente dans le modèle ludique. (Voir §2.)

2. **Manque de motivation entre les paliers** — Après 20 captures, tu as 7-12 espèces en Arboretum. Avant d'atteindre 50 (badge Botaniste amateur), il faut +40 captures en moyenne, soit 5-10 jours de marche stratégique. Pas de feedback intermédiaire sur le chemin. Les 907 espèces affichées sont un **mirage proportionnel** : tu n'en verras jamais plus de 10 % en jeu casual (Poisson + loi de Zipf : ~600 espèces ont <10 individus à Paris, 200 ont <5). Le dénominateur tue la sensation de progression.

## 2. Les 15 badges : audit 1 par 1

| ID | Catégorie | Criterion | Verdict | Atteignable ? | Dosage |
|---|---|---|---|---|---|
| first_capture | Découverte | 1 capture | Trop trivial pour être un badge. C'est une **onboarding gate**, pas une récompense. | ✓ J+0 (5 min) | Trop facile — prise en tête le jour 1 |
| promenade | Découverte | 10 captures | Cohérent. Représente ~3-5 sessions moyennes. | ✓ J+7 | Bien dosé |
| marcheur | Découverte | 50 captures | Requiert stratégie. Possible en 2-3 semaines. | ✓ J+14-21 | Léger — la plupart des testeurs actifs vont l'atteindre |
| centurion | Découverte | 100 captures | **Problématique.** Requiert soit du grind, soit une obsession. Les sessions naturelles platoient à 3-5 captures/jour. Donc 20+ jours de jeu actif. | ✓ J+30 | Frustrant — on voit approcher le badge mais jamais on ne l'atteint en play naturel |
| botaniste_amateur | Botanique | 50 espèces | **Voir ci-dessus § proportionnel.** 50 espèces = beaucoup de diversité parcourue. Possible, mais demande de la curiosité + variété géographique. | ✓ J+21-45 | Bien dosé, nécessite exploration |
| botaniste_confirme | Botanique | 200 espèces | **Quasi-impossible en casual.** 200 espèces = ~22 % du catalogue. Avec la loi de Zipf parisienne, tu plafonneras à 40-60 en jeu normal. | ✗ Pratiquement inaccessible | Très frustrant |
| espece_rare | Botanique | Espèce <100 individus à Paris | **Problématique de définition.** Le code cherche `SpeciesStats.count < 100` (BadgeEvaluator.kt:48). Il existe 318 espèces <100 à Paris — donc ce badge est trivial une fois qu'on en a capturé 10-15. Pas de sensation de « rareté » perçue. | ✓ J+7 (aléatoire) | Mal défini — confusion entre rareté statistique et rareté perçue |
| tourneur_de_paris | Géographie | 10 arrondissements différents | Cohérent — demande de la variété. Possible en 2-3 semaines de promenade. | ✓ J+14 | Bien dosé |
| tour_complet | Géographie | 20 arrondissements | **Ambitieux mais juste.** Paris intra-muros = 20 arr., plus bois + exclaves = 36 zones géo. Atteindre 20 arr. demande du dévouement (traverse complète recommandée). | ✓ J+45+ | Bien dosé — récompense l'exploration systématique |
| chasseur_remarquables | Remarquables | 10 remarquables | 183 remarquables au total. 10 = 5 %. Prise en main facile via overlay « Plus proche remarquable » (mais voir §3). | ✓ J+14 | Bien dosé |
| legende | Remarquables | 50 remarquables | 50 = 27 %. Demande de la chasse ciblée. Possible en 6-8 semaines. | ✓ J+45 | Bien dosé — défi perceptible |
| ronde_des_saisons | Saisons | ≥1 capture/saison (4 saisons) | **Cosmétique.** Code livré sur les 4 buckets calendaires (Season.kt:17-21). Pour débloquer, il suffit de jouer dans 4 périodes disjonctes. Aucun phénotype ludique. | ✓ J+365 (inévitable) | Trivial si on joue >3 mois ; presque inaccessible en 4 semaines (automne+hiver+printemps + 1 jour d'été = difficile à orchestrer) |
| annee_complete | Saisons | Capture chaque mois, 12 mois consécutifs | **Pire badge.** Code vérifie 12 YearMonths consécutifs (BadgeEvaluator.kt:99-103). Demande 1 an minimum. Hors de portée de tout player casual. | ✗ Pratiquement inaccessible J+365 | Frustrant — visible, infaisable en cycle court |
| geant | Démesure | Arbre >30 m de haut | 1 % des arbres. Aléatoire à la capture. Pas de mécanisme de chasse — il faut tomber sur un par chance ou lire les fiches. | ✓ J+14 (probabiliste) | Bien dosé en jeu passif ; frustrant en jeu actif (impossible à cibler) |
| vieux_sage | Démesure | Arbre >4 m de circonférence | ~5 % des arbres. Même problème : impossible à cibler, dépend de la chance. | ✓ J+21 (probabiliste) | Bien dosé en passif ; cassé en actif |

**Synthèse badges :** 4 badges cosmétiques/inaccessibles (first_capture, annee_complete, botaniste_confirme, ronde_des_saisons), 2 badges aléatoires (geant, vieux_sage), 1 badge mal défini (espece_rare). Les 8 autres sont correctement dosés. **Pas de progression doucement courbe** — c'est des marches : trivial jusqu'à 50, puis mur à 100, puis abyssal à 200.

## 3. La mécanique remarquables et le FAB "Plus proche"

**Promesse vision-jeu (§4)** : « Un bouton ★ permanent au-dessus de la carte. Un tap déplie un petit overlay : « Plus proche remarquable non découvert : 240 m ». »

**Livraison (MapScreen.kt:520-532)** : FAB en bas à gauche avec icône Search, pas ★. Tap → snackbar ephémère affichant la distance. **Pas d'overlay persistant, pas de navigation vers le remarquable.**

**Verdict : écart mineure mais révélateur.** Le FAB est actif mais discret. La snackbar est une mauvaise UX pour la chasse aux remarquables — elle disparaît en 3 secondes, après quoi tu as oublié la distance. La promesse était de créer un **mini-jeu de chasse au trésor** ; la livraison est un **search-tool utilititaire**. Ludiquement, cela brise la sensation de quête : tu appuies, tu reçois un nombre, c'est fini. Pas de feedback récurrent, pas d'animation, pas de « tu t'approches/tu t'éloignes ».

**Coût de fix (v1.0.1)** : remplacer Search icon par icône ★, garder snackbar mais ajouter toast ephémère qui disparaît +lentement (5 s) ou persistent jusqu'au prochain tap. Effort : 30 min.

## 4. Saisonnalité : cosmétique ou ludique ?

**Promesse README** : « La saisonnalité réelle » comme **différenciateur clé**. 

**Promesse vision-jeu (§3)** : « La même espèce capturée en hiver vs en mai compte comme 2 entrées. Multiplie naturellement la collection sans inflation artificielle. »

**Réalité code** (Season.kt:17-38) : 4 saisons calendaires figées (déc-fév, mar-mai, juin-août, sep-nov). Aucun lien à la phénologie réelle — pas de dates de floraison, pas de jours juliens pour le feuillage. `fromTimestamp()` applique juste un `when(month)`.

**Effet ludique** : Légèrement positif. Capturer un Platane en hiver et en été sont deux captures distinctes. Ça multiplie effectivement la sensation de progression : à 4 mois de jeu, tu as « vu » 4 × plus d'espèces-saison. Mais c'est une **illusion statistique**. Le vrai apport serait la phénologie — « en mai le Platane a des feuilles, en janvier c'est branches nues ». Ce signal serait ludiquement pertinent : tu reconnaîs l'espèce par sa forme saisonnière, pas juste par une date. Là, il n'y a rien.

**Verdict : cosmétique, pas ludique.** C'est une **multiplication du catalogue par 4** sans sens biologique. C'est efficace pour gonfler les stats (« 213 042 arbres × 4 saisons »), mais cela ne crée aucun pattern cognitif. Après 2 mois, le joueur a oublié que « hiver = pas de feuilles ». Il capture juste « espèce X en saison Y ».

**Tension #2 de la charte validée.**

## 5. Débloquer 38 147 pins du Platane : anticlimax ou moment fort ?

Quand tu captureras ton premier Platane, tous les 38 147 pins verts pour cette espèce s'illumineront sur la carte (`enrichGeoJsonWithDiscovery`, MapScreen.kt:204-205, applyDiscoveryColor).

**Moment ludique fort ?** OUI, pour 10 secondes. Tu vas zoomer out pour voir l'étendue de la révélation.

**Après 10 secondes :** Ces 38 147 pins ont la même fiche : « Platane, hauteur 12-15 m, circonférence 100-150 cm ». Il n'y a pas de **variation perçue**. Contrairement à Pokémon GO où capturer un Pokémon révèle sa force (CP), son type, ses stats, ici tu révèles 38 147 **clones identiques**. C'est comme débloquer un skin en jeu action et avoir 38 147 copies identiques de ce skin.

**Verdict : moment fort suivi d'anticlimax.** Le pic est trop rapide, trop isolé. Aucune autre espèce (2e place : Marronnier, 20 030) n'a ce potentiel de climax. Les autres déblocages demandent de la patience sans gratification équivalente.

## 6. Fiches vides : 379 espèces sans Wikipedia

**Réalité** : 528 espèces ont une fiche Wikipedia embarquée ; 379 n'en ont pas. Code : SpeciesDetailScreen.kt:160, WikipediaBlock.

**Affichage** : « Pas d'info encyclopédique disponible pour cette espèce. »

**Ludiquement** : C'est un **mur symbolique**. Quand tu captures une espèce rare (ex : Ehretia dicksonii, 23 arbres), tu ouvres sa fiche et lis « Pas d'info ». Pas d'histoire, pas de curiosité assouvie, pas de raison de partager. Contraste violent avec un Platane ou un Marronnier (Wikipedia riche, PDF Ville de Paris dispo).

**Verdict : frustrant, mais acceptable.** Capturer une rareté devrait être gratifiant en tant que tel (« tu as trouvé 1 des 23 Ehretia de Paris »). La fiche vide vient après cette sensation. C'est un **léger coup** à l'engagement, pas une cassure.

**Piste : en v1.1.0, ajouter un fallback résumé depuis les stats.** Ex : « Famille des Celastraceae. 23 individus à Paris. Absent de Wikipedia FR, informations botaniques limitées. » C'est mieux qu'un vide.

## 7. Remarquables : modèle ludique brisé

**Promesse vision-jeu (§5)** : « Chaque remarquable est une **entrée individuelle** dans l'Arboretum, indépendante de son espèce. Capturer l'espèce X ne débloque pas les remarquables de l'espèce X. »

**Livraison** : Correct dans le code (BadgeEvaluator.kt:60-61 : `if (capture.remarquable)` branche séparée).

**Problème d'UX** : Les remarquables affichent une **fiche enrichie** (PDF Ville de Paris) SEULEMENT après capture. Sans capture, on voit juste « Arbre inconnu ». Contrairement aux espèces régulières où capturer l'espèce déverrouille toute l'info, les remarquables restent **noir sur blanc** jusqu'à la capture.

**Conséquence ludique** : Tu chevauches deux chasses en parallèle (espèces vs remarquables) mais elles sont asymétriques. Capturer le Platane dans les Tuileries (remarquable) ne te dit RIEN sur cet arbre précis — juste « Platane ». La fiche remarquable reste verrouillée. C'est frustrant.

**Verdict : tension de conception non résolue.** Un remarquable devrait soit être traité comme une espèce (révéler son info à la capture d'espèce), soit être un vrai « boss » avec sa propre fiche cachée (style Pokémon légendaire). Là, c'est un hybride maladroit.

**Coût de fix (v1.1.0)** : Pré-afficher la fiche remarquable enrichie même avant capture (avec un badge « Pas encore découvert »). Effort : 2-3 h.

## 8. Mécanique progressions : absence de gradations douces

Les captures accumulent rapidement (exponentielle perçue : « j'en ai 50 ! »). Mais la progresse vers les badges suit une **courbe de step-function**.

- **0-10 captures** : badges triviales (First, Promenade).
- **10-50 captures** : plateau agréable (Marcheur, quelques spécies).
- **50-100 captures** : mur brutal (Centurion demande 100, aucun badge intermédiaire).
- **100+ captures** : Botaniste Confirmé (200 espèces) est unilatéralement inaccessible.

**Verdict : pédagogie cassée.** La progression devrait être lisse : 1 / 5 / 10 / 25 / 50 / 100 / 250 / 500 captures (7 paliers vs 4). Ou ajouter des badges intermédiaires.

## 9. Single-player vs « family & friends » : tension non résolue

**Promesse** : Pas de leaderboard, pas de social. Pur single-player.

**Réalité** : Les badges EXPOSENT implicitement la progression (BadgesScreen.kt: « 3 / 15 débloqués »). Si deux joueurs ouvrent l'app le même jour et affichent leurs badges côte à côte, c'est un mini-leaderboard.

**Tension #3 de la charte non tranchée.**

La promesse v1 est « single-player ». Les badges font appel à la comparaison sociale même sans plateforme de ranking. C'est OK pour « family & friends » (parents vs enfants), cassé pour échelle anonyme (100 k joueurs).

**Verdict : acceptable en v1 (scope "family"), mais conscient du problème.**

## 10. Mécaniques manquantes pour vivifier la boucle

### Manque #1 : Système de défis ou quêtes éphémères

« Cette semaine, capture un arbre de chaque saison » ou « Cette saison, explore le 15e ». Cela créerait une **raison de rentrer 2-3× / semaine**. Actuellement, la raison de jouer est purement extrinsèque (« débloquer Centurion »). Les v1 de Pokémon GO, Pikmin Bloom, etc. ont des quêtes quotidiennes.

**Coût (v1.1.0 ou v2.0)** : Ajouter une table `Quest`, un écran dédié, une notification optionnelle. Effort : 3-5 j. **Critique pour rétention.**

### Manque #2 : Feedback haptique de capture (hormis photos)

À chaque capture réussie, vibration + son. Code existe pour la photo, mais pas de retour sensoriel à la confirmation. Pokémon GO a un *buzz* distinctif.

**Coût (v1.0.1)** : Ajouter `HapticFeedback.performHapticFeedback(CONFIRM)` et un son système. Effort : 1 h.

### Manque #3 : Photobook ou journal de captures

L'Arboretum affiche les fiches, mais pas de **narration visuelle** — une timeline des captures chronologiques avec photos et annotations joueur. Ça créerait de l'attachement.

**Coût (v1.1.0)** : Ajouter un onglet « Journal » à l'Arboretum. Effort : 3-4 j.

## Pistes considérées et écartées

- **Phénologie réelle (dates de floraison variables par espèce)** : Rejetée. Coût d'données énorme (faut un modèle florule par espèce parisienne + année). V2 seulement.
- **Combats / raids d'arbres** : Hors registre, rejeté dès vision-jeu.
- **Échanges joueur** : Cassé par single-player, rejeté dès vision-jeu.
- **Système d'XP global du joueur** : Ajouterait du grind sans narratif. Rejeté.
- **Partage de photo dans l'app** : Single-player, pas de backend. Rejeté v1.
- **Paliers de hauteur/circonférence comme mécaniques** : Données trop incomplètes (18 % sans cultivar, 3-4 % sans hauteur). Rejeté.

## Recos par tier

| Tier | Titre | 1 phrase |
|---|---|---|
| v1.0.1 | Remplacer Search icon par ★ pour remarquables FAB | L'icône est un détail, mais c'est le détail qui change « tool de recherche » en « quête ludique ». |
| v1.0.1 | Ajouter feedback haptique à la confirmation de capture | Chaque capture devrait faire *vibrer* le téléphone — cela renforce la sensation de réussite. |
| v1.0.1 | Ajuster les timings de snackbar remarquable (5 s au lieu de 3) | Actuellement le joueur oublie la distance en 3 secondes ; allonger à 5 s aide à la chasse. |
| v1.1.0 | Ajouter affichage des fiches remarquables même avant capture | Actuellement capturer un remarquable platonien ne révèle pas sa fiche enrichie ; pré-afficher avec badge « À découvrir ». |
| v1.1.0 | Restructurer les badges en 7-9 paliers au lieu de 4 pour captures | Centurion (100) est trop loin après Marcheur (50) ; ajouter badges à 75, 150, 250 captures pour lisse la courbe. |
| v1.1.0 | Remplacer le texte « Pas d'info encyclopédique » par résumé fallback | Pour les 379 espèces sans Wikipedia, afficher « Famille X, Y individus à Paris, détails botaniques limités ». |
| v1.1.0 | Ajouter onglet « Journal » ou « Timeline » à l'Arboretum | Afficher un historique chronologique des captures avec photos, permettant au joueur de revisiter l'exploration. |
| v2.0 | Implémenter quêtes/défis hebdomadaires | Créer des objectifs courts (« explore 3 arrondissements cette semaine ») pour raison de retour 2-3×/semaine. Tension : requiert backend ou stockage local avancé. Coût : 5 j. |
| v2.0 | Ajouter phénologie réelle (au moins pour top 50 espèces) | Faire varier le visuel/description des espèces selon saison réelle (floraison, feuillage). Coût : 3-4 j de data ; 2 j d'UI. |
| v2.0 | Refondre modèle remarquables (vs espèces) pour cohérence ludique | Actuellement c'est un hybride asymétrique. Trancher : soit intégrer remarquables comme espèces « boss », soit créer un parallèle symétrique. Coût : 2-3 j. |

