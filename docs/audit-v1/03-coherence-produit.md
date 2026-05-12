# Cohérence produit & identité — CanoPaname v1.0

## Verdict

CanoPaname vend un produit plutôt cohérent, mais fait trois mensonges communicatifs critiques et un résiduel. Le pitch reste fort (Pokédex botanique local + saisonnalité) mais échoue à honorer la promesse « 907 espèces », tait le trafic OpenFreeMap, et conditionne l'accès aux fiches remarquables sans le dire clairement. Ces ruptures érodent la confiance progressivement. **Recos : 3 fixes README/PRIVACY v1.0.1 + 1 clarification code + 1 espace lexical à trancher pour v1.1.**

---

## 1. Tension #1 : « 907 espèces » vs navigation limitée

**Verdict : Mensonge communicatif sérieux. Fixer la communication, pas le code.**

Le README clame « 907 espèces à découvrir » et « Pokédex à 907 espèces ». Réalité : 528 ont une fiche Wikipedia embarquée (cf. `ROADMAP.md:2.5` : « Wikipedia FR (528/907) »). Les 379 autres affichent une fiche vide (genre + espèce + stats Paris, mais pas de résumé riche).

**Le vrai mensonge** : le user qui capture une espèce rare (p.ex. un cormier, 5 individus à Paris) ne saura rien de son arbre — juste l'espèce nue, comme si l'app n'avait rien à lui dire. La fiche-espèce affichée sera une coquille vide. Aucun warning « Fiches incomplètes sur cette version ». C'est une frustration silencieuse : le user croit accéder à une richesse qu'il n'y a pas.

**Surtout**, pas de barre de recherche pour parcourir ces 907 entrées. Seule navigation : la carte ou l'Arboretum. Une espèce sans arbre proximal n'est découvrable que si on l'a capturée. Cela confond « 907 au dataset » avec « 907 accessibles au jeu ».

**Reco v1.0.1 : Réécrire la phrase du README** :
- Avant : « 907 espèces à découvrir »
- Après : « 907 espèces du dataset parisien à découvrir par capture, dont 528 avec fiches botaniques enrichies »

Et ajouter une ligne en « Données » du README : « Les espèces sans fiche Wikipedia affichent leur nom et stats Paris, mais pas de description. »

**Rationale** : Ce n'est pas une limite, c'est un design ; mais il faut le dire. L'app ne ment pas (elle embarque bien 907, elle n'en affiche pas 907 fiches complètes), elle juste la vérité de travers.

---

## 2. Tension #2 : « Saisonnalité réelle » vs 4 buckets fixes

**Verdict : Nuancé. Pas un mensonge, une simplification honnête mal communiquée.**

Le README promet « la **saisonnalité réelle** (un platane en mai ≠ en novembre) » comme différenciateur clé. Vision-jeu confirme : « multiplie naturellement le contenu ».

Réalité technique : 4 saisons calendaires fixes (`WINTER = déc-fév`, `SPRING = mar-mai`, `SUMMER = juin-août`, `AUTUMN = sep-nov`). Pas de phénologie réelle (dates de floraison/feuillage variables selon espèce et année). C'est un **archivage temporel par bucket**, pas une simulation biologique.

**Est-ce un mensonge ?** Non, pas vraiment. Un platane EN MAI affiche des feuilles EN PRINTEMPS, différent du platane EN NOVEMBRE (EN AUTOMNE). La saisonnalité *existe* et *change* visuellement. Ce que c'est PAS : prédiction phénologique (« cet arbre fleurit du 15 mars au 20 avril »). 

**Mais c'est mal communiqué.** Le user lit « saisonnalité réelle » et imagine que l'app connait les dates précises de floraison. Quand il capture deux platanes le 10 mai, l'app lui dit « tes 2 platanes EN PRINTEMPS ». L'app n'a fait que bucketing calendaire. Le mot « réelle » surpromet.

**Reco v1.0.1 : Reformuler le README** :
- Avant : « différenciateur clé vs. Pokémon : la **saisonnalité réelle** (un platane en mai ≠ en novembre) »
- Après : « différenciateur clé vs. Pokémon : la **saisonnalité calendaire** (un platane capturé au printemps compte séparément de celui capturé en automne), multipliant naturellement la collection »

Rationale : « Réelle » dit « calibrée à la nature » ; la vérité est « en vraie saison ». La seconde est honnête et toujours attrayante.

---

## 3. Tension #3 : « Pas de classement, pas de social » vs 15 badges seuillés

**Verdict : Faux débat. L'app est honnête. Passer.**

Le README promet « Pensé family & friends, pas de classement, pas de social ». Tension notée : 15 badges avec seuils (10/50/100 captures, etc.) = « mini-leaderboard contre soi-même ». 

**Tranché** : Ce n'est pas un mensonge. Pas de leaderboard comparatif, pas de partage de progression, pas de raid/échange. Les badges mesurent ton *propre* chemin. C'est de la pression intrapersonnelle, pas interpersonnelle. C'est le design de Pokémon GO single-player : tes stats, ta progression, ton timing. Zéro friction sociale.

Aucune reco. Passer.

---

## 4. Tension #4 : « 100 % local, jamais envoyé » vs OpenFreeMap runtime

**Verdict : Mensonge par omission grave. Fixer README et PRIVACY.md.**

Le README assène : « Tout reste sur ton téléphone. Pas de cloud, pas de compte, pas de tracker. » PRIVACY.md dit « Aucun envoi vers un serveur ». Implicite user : zéro trafic réseau après le premier téléchargement.

Réalité : L'app télécharge des tuiles cartographiques depuis `tiles.openfreemap.org` au runtime. C'est le seul appel réseau (cf. `strings.xml:5` et `AboutScreen.kt:55`), sans envoi de GPS (vrai), mais c'est du trafic réseau **non mentionné** dans le README ni dans PRIVACY.md.

**Est-ce grave ?** Sur un plan légal (audit pré-public), non — pas de PII envoyée, juste des requêtes de tuiles publiques. Sur un plan de confiance user : oui. Quelqu'un qui choisit l'app pour la confidentialité maximale aprendra que la carte parle au monde à chaque pan. Ça n'invalide pas le produit (OpenFreeMap est propre), ça brise le pacte communicatif.

**Reco v1.0.1 : Ajouter à PRIVACY.md, §Connexions réseau** :
- Avant : « Tuiles cartographiques : `tiles.openfreemap.org` (OpenStreetMap, sans identifiant). C'est le seul appel réseau au runtime. »
- Après : Déjà là ! ✓

**Reco v1.0.1 : Mettre à jour README.md, §Données et vie privée** :
- Avant : « Tout reste sur ton téléphone. Pas de cloud, pas de compte, pas de tracker. Détails dans [PRIVACY.md](PRIVACY.md). »
- Après : « Tout reste sur ton téléphone, sauf les tuiles cartographiques qui viennent d'OpenStreetMap (envoi zéro données personnelles). Pas de cloud, pas de compte, pas de tracker. Détails dans [PRIVACY.md](PRIVACY.md). »

Rationale : L'app est honnête (PRIVACY.md le dit), elle juste pas visible au lecteur de README qui vise la transparence maximale.

---

## 5. Tension #5 : Fiches remarquables enrichies vs condition à la capture

**Verdict : Mensonge par omission moyen. Clarifier la communication.**

Le CHANGELOG promet « Pokédex remarquables dédié (169 fiches, lien fiche PDF Ville de Paris) ». L'écran Remarquables affiche 169 fiches — oui, mais **conditionnées à la capture préalable**.

Détail crucial : sans capture, tu ne vois rien de la fiche — juste une silhouette grise et l'adresse. Le lien fiche PDF ne s'affiche que post-capture. La richesse promise (qualification, résumé, cultivar) reste cachée.

**Est-ce grave ?** Moyennement. C'est un design choice (tu dois chercher le remarquable physiquement pour découvrir sa richesse). Mais le CHANGELOG parle de « fiche enrichie » sans préciser « tu dois l'avoir capturée ». C'est un devoir de clarté.

**Reco v1.0.1 : Ajouter une note au CHANGELOG v1.0.0** :
```
- Pokédex remarquables dédié (169 fiches PDF Ville de Paris, accessibles après capture).
```

Et ajouter un note préalable de code : dans `RemarquablesScreen.kt`, ajouter un commentaire de 2 lignes :
```kotlin
// Les fiches remarquables affichent le nom et adresse même non-capturés,
// mais la fiche PDF et résumé ne s'affichent qu'après la première capture.
```

Rationale : La vraie tension ici est qu'on cache la richesse. C'est un *choix*, pas une limitation — mais l'app doit le dire clairement upfront.

---

## 6. Tension #6 : Feedback haptique ailleurs qu'à la capture

**Verdict : Non exploré, hors-périmètre Cohérence. Voir angle 05.**

La charte note : « Pas de feedback haptique ailleurs qu'à la capture (?). À vérifier au passage ». Cela relève de la friction sensorielle, pas de la cohérence pitch/code. Passer à angle 05.

---

## Tensions supplémentaires découvertes

### 7. Espace lexical fragmenté : Pokédex vs Arboretum vs Catalogue

**Verdict : Incoherent, mais tolérable.**

L'app utilise **trois noms** pour la même fonction — collection d'espèces :
- « Pokédex » : README, AboutScreen, vision-jeu.
- « Arboretum » : écran principal, code UI.
- « Catalogue » : toggle LISTE/CATALOGUE dans ArboretumScreen (cf. `strings.xml:22`).

**Où ça ?**
- `README.md:1,17` : « Pokédex botanique »
- `AboutScreen.kt:146` : « Pokédex botanique des arbres de Paris »
- `ArboretumScreen.kt:110` : `Text("Arboretum")`
- `strings.xml:21-22` : `segment_liste`, `segment_catalogue`
- `vision-jeu.md` : « Arboretum (= Pokédex) »

**Est-ce grave ?** Non. « Pokédex » est le pitch (il inspire), « Arboretum » est la vraie structure (inventaire botanique), « Catalogue » est le mode d'affichage (comme une grille). Un user comprendra. Mais c'est du bruit.

**Reco v1.1.0 : Unifier sur « Catalogue » ou « Arboretum »** pour tout sauf README (où « Pokédex » reste marketing). Le code et l'UI doivent parler le même français. Détail bas, peut attendre v1.1 où les strings seront retouchées pour d'autres raisons.

---

### 8. Identité du projet : « CanoPaname » vs résidus « Arbres »

**Verdict : Réglé. Passer.**

La charte craint des résidus « Arbres » liés au rebranding (« Arbres » → « CanoPaname »). Vérif : 
- Package code : `app.arbre` ✓ (figé pour ne pas casser les app links).
- App name : `CanoPaname` ✓ (AndroidManifest).
- Tous les libellés UI : CanoPaname ✓ (pas de résidu visible).

Rebranding réussi. Passer.

---

### 9. Nombre de remarquables : 169 vs 183

**Verdict : Mineur, data version.**

Le README et CHANGELOG disent « 169 remarquables » ; dataset-stats.json dit 183. C'est une question de when the stats were last computed vs. when the README was updated. Minuscule drift.

**Reco v1.0.1 : Vérifier et éventuellement bumper le README** à 183 si c'est l'état courant. Si ROADMAP.md dit 169 comme seuil établi, garder 169 et laisser le delta en note technique. Pas critique.

---

### 10. Tonalité inconsistante des bullets onboarding

**Verdict : Mineure. Voir angle 01 (UX).**

Les bullets du WelcomeScreen (cf. `strings.xml:10-13`) mélangent :
- Point de vue : « Tous les pins de la carte commencent gris » (impersonnel).
- Directive : « Approche-toi à moins de 30 m » (2e personne).
- Explication : « Capture une espèce, et tous les arbres du même **genre** passent au vert » (2e personne + erreur : c'est l'espèce, pas le genre — mais c'est un vrai bug, hors cohérence).

C'est une question de UX/polish, pas de cohérence stratégique. Angle 01.

---

## Audit terminologie (table synthétique)

| Concept | Noms utilisés | Où | Verdict |
|---------|---------------|-----|---------|
| Collection d'espèces | Pokédex, Arboretum, Catalogue | README, AboutScreen, UI | Fragmenté mais clair au user |
| Espèces affichées | 907 | README | Incomplet (528 avec fiche) |
| Trafic réseau | Caché | README | Mensonge par omission |
| Remarquables | Fiches enrichies | CHANGELOG | Incomplet (post-capture only) |
| Saisons | Réelle, calendaires | README vs code | Bien, juste mal nommée |
| Badges | Pas mentionné vs 15 listés | README vs code | Cohérent (progression, pas social) |

---

## Pistes considérées et écartées

1. **Proposer une barre de recherche pour les 907 espèces** — Hors-angle. Relève de la boucle de jeu (angle 02). Si c'est techniquement souhaitable, ce sera une reco angle 02.
2. **Implémenter la phénologie réelle (dates de floraison variables)** — v2.0 ou plus. Hors v1. Coût énorme (sourcer data phénologie + UI pour afficher date réelle), bénéfice faible (les 4 buckets font déjà le job).
3. **Créer un mode hors-ligne complet (tuiles pre-cached)** — Hors v1.0. V1.1.0 ou plus. Pertinent pour les subway riders, mais requiert un toggle "pré-télécharger les tuiles" + gestion de la taille APK.
4. **Renommer « Pokédex » en interne** — Overkill pour v1.0.1. V1.1.0 quand on retouche les strings pour d'autres raisons.

---

## Recos par tier

| Tier | Titre | Résumé |
|------|-------|--------|
| v1.0.1 | Clarifier README : « 907 espèces dont 528 fiches enrichies » | Le user sait d'entrée que certaines fiches seront vides. Ligne une. |
| v1.0.1 | Ajouter à README : « Les tuiles cartographiques viennent d'OpenStreetMap » | Honnêteté sur trafic réseau. Deux lignes. |
| v1.0.1 | Nuancer README : « saisonnalité **calendaire** » au lieu de « réelle » | Evite la surpromesse phénologique. Un mot. |
| v1.0.1 | Ajouter note CHANGELOG : remarquables accessibles « après capture » | Transparence sur le conditionnement. Deux mots. |
| v1.1.0 | Trancher espace lexical : Pokédex (pitch) vs Arboretum (code) vs Catalogue (UI) | Unifier le code/UI sur un seul nom, garder Pokédex au marketing. Refonte strings. |
| v2.0 | Si phénologie réelle : tension #2 devient obsolète | Coût : +2 mois recherche data + UI. Bénéfice marketing moyen. À décider selon signal user. |

---

## Notes finales

**Posture générale** : L'app a un pitch fort (Pokédex local + promenade lente + saisonnalité). Elle l'honore en code. Mais elle communique la réalité de manière honnête-mais-imprécise. Les trois ruptures (907→528, local→OpenFreeMap, fiche→capture-gated) sont **vraies**, elles juste pas dites clairement.

Le coût de réputation est proportionnel au user qui a choisi l'app *spécifiquement* pour sa cohérence/transparence. Les devs qui cherchent « open source + local » la trouveront honnête. Les novices qui voient « Pokédex + 907 espèces » risquent la frustration.

**Stratégie recommandée** : v1.0.1 c'est du nettoyage communicatif (3-4 lignes de réécriture = ~30 min). v1.1.0, trancher l'espace lexical si le produit gagne des users (feedback outil = « Catalog ou Arboretum ? »). v2.0, ne rouvrir phénologie que si les users demandent (« Why is my cherry tree spring-only ? »).

