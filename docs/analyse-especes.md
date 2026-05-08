# Analyse du catalogue d'espèces — décisions à prendre

Document de travail. Synthétise une exploration de `app/src/main/assets/species-index.json` et `species-info.json` (dataset OpenData Paris « les-arbres », 213 042 arbres / 907 espèces) et propose des pistes pour le **BACKLOG** et la **ROADMAP**. Pas de décision prise — à reprendre dans une session ultérieure.

Date d'analyse : 2026-05-08, dataset au moment du build courant.

## 1. État des lieux

### 1.1 Chiffres clés

- **213 042 arbres**, **907 espèces**, **183 remarquables** (cf. `dataset-stats.json`).
- **221 noms communs distincts seulement** → 907 / 221 = **~4,1 espèces par nom commun en moyenne**.
- **115 noms communs (52%) regroupent ≥ 2 espèces** ; 106 sont solos.
- **93,2 % des arbres parisiens portent un nom commun ambigu** (partagé avec ≥ 1 autre espèce).
- **Couverture Wikipedia FR** : 528 / 907 espèces (58,2 %), couvrant **92,2 % des arbres** ; les 41,8 % sans page WP sont quasi-exclusivement des hapax / hybrides / cultivars rares.

### 1.2 Top des noms communs ambigus

| Nom commun | # espèces | # arbres |
|---|---:|---:|
| Chêne | **55** | 6 038 |
| Érable | **47** | 20 595 |
| Pin | 31 | 5 138 |
| Cerisier à fleurs | 21 | 2 606 |
| Bouleau | 21 | 1 970 |
| Épicéa | 20 | 236 |
| Tilleul | 19 | 22 953 |
| Magnolia | 19 | 1 566 |
| Aubépine | 19 | 331 |
| Saule | 19 | 459 |
| Pommier à fleurs | 17 | 959 |
| Sapin | 16 | 155 |
| Marronnier | 14 | 24 894 |
| Frêne | 14 | 4 932 |
| Micocoulier | 14 | 5 042 |
| Orme | 13 | 1 045 |

### 1.3 Espèces « floues » (genre ou espèce non identifiée par OpenData)

- **103 entrées `sp.` ou `n. sp.`** (genre connu, espèce non identifiée), totalisant **8 793 arbres (4,1 % du dataset)**.
- **3 entrées `Non spécifié`** (genre lui-même inconnu), totalisant **677 arbres** (dont 673 sous `Non spécifié sp.`).
- **56 lignes sans nom commun (`nc`) du tout** dans l'index (165 arbres) — souvent des coquilles OpenData (`Olea europea` au lieu de `europaea`).

Top des entrées `sp.` :

```
2025  Tilia sp.            (Tilleul)
 710  Quercus sp.          (Chêne)
 673  Non spécifié sp.     ← genre inconnu
 617  Aesculus sp.         (Marronnier)
 583  Prunus sp.
 536  Chamaecyparis sp.    (Faux-cyprès)
 463  Ulmus sp.            (Orme)
 398  Acer sp.
 358  Malus sp.            (Pommier)
 338  Fraxinus sp.         (Frêne)
 203  Platanus sp.
```

### 1.4 Distribution & long tail

- **Top 10 espèces = 50,1 %** des arbres parisiens.
- **Top 50 espèces = 80,9 %**. Les 857 restantes se partagent 19 %.
- **247 espèces représentées par 1 seul arbre** dans Paris.
- **507 espèces ≤ 5 arbres** (56 % du catalogue).

Top 10 :

```
38147  Platanus x hispanica          (Platane)
20030  Aesculus hippocastanum        (Marronnier)
10909  Styphnolobium japonicum       (Sophora)
 7655  Tilia tomentosa               (Tilleul)
 6645  Acer platanoides              (Érable)
 6624  Acer pseudoplatanus           (Érable)
 4827  Celtis australis              (Micocoulier)
 4147  Aesculus x carnea             (Marronnier)
 4089  Tilia cordata                 (Tilleul)
 3653  Pyrus calleryana              (Poirier à fleurs)
```

### 1.5 Curiosités

- **« Platane » = 4 espèces seulement** sous ce nom commun (vs. 55 pour Chêne). `Platanus × hispanica` totalise à lui seul 38 147 arbres = l'espèce #1 de Paris.
- **63 hybrides** dans le catalogue (notation `x`/`×`). Aucun cultivar nommé (`'Variété'`) exposé.
- **200 genres distincts**. Les plus diversifiés : Quercus (55), Acer (48), Prunus (44), Pinus (31), Malus (26), Crataegus (26).
- **Noms communs solo évocateurs** (1 seule espèce associée) : « Arbre aux quarante écus » (Ginkgo biloba, 1 138), « Parrotie de Perse / Arbre de fer » (Parrotia persica, 724), « Charme-Houblon » (Ostrya carpinifolia, 1 171), « Tulipier » (Liriodendron tulipifera, 1 017), « Noisetier de Byzance » (Corylus colurna, 3 381), « Merisier » (Prunus avium, 1 648).
- **Méta-curiosité** : la ligne `Non spécifié n. sp.` existe — littéralement « espèce non spécifiée d'un genre non spécifié ».
- **Coquilles OpenData** repérées : `Olea europea` (devrait être `europaea`, 33 oliviers sans `nc`), à confirmer si systématique sur d'autres binômes.

## 2. Implications gameplay

### 2.1 La conséquence majeure : un nom commun ≠ une espèce

C'est un signal pédagogique fort que l'app peut transformer en mécanique de progression : du joueur grand public qui reconnaît « un Tilleul » au naturaliste qui distingue `Tilia cordata` de `T. tomentosa` à la pubescence des feuilles. Aujourd'hui le jeu agrège déjà tout sous l'espèce (sk), mais ne valorise pas le niveau intermédiaire « nom commun ».

### 2.2 Pistes ouvertes (à arbitrer)

- **Carte filtrée par nom commun** : étendre `MAP_FILTERED` pour accepter un set de `sk` (« tous les Chênes » → 55 sk fusionnés en une expression `match`). Coût quasi-nul, le filtre `sk` MapLibre accepte déjà plusieurs valeurs. Utile pour planifier une sortie thématique (« je vais chasser tous les chênes du Bois de Vincennes »).
- **Compteur Arboretum à deux niveaux** : afficher « X / 221 noms communs » (objectif réaliste) **et** « Y / 907 espèces » (objectif complétiste). Sépare la motivation grand public de celle du botaniste obsessionnel.
- **Mini-quiz d'identification** entre espèces partageant le même `nc` (Quercus robur vs petraea, Tilia cordata vs platyphyllos). Réutilise les summaries Wikipedia déjà bakés dans `species-info.json` ; pas de nouveau fetch externe.
- **Badge « Inspecteur »** : capturer N arbres mal classifiés (`sp.` ou `n. sp.`) — valorise une catégorie aujourd'hui invisible.
- **Badge « Mosaïque de chênes »** (ou autre genre divers) : capturer 10 espèces différentes sous le même `nc`. Difficulté élevée, satisfaction élevée.
- **Affichage du nom vernaculaire FR** sur la fiche-espèce, à la place ou en complément du binôme latin : « Chêne pédonculé » plutôt que / en plus de « Quercus robur ».

## 3. Décisions à prendre

### 3.1 Garder les 55 espèces de chêne ?

**Recommandation : oui, garder telles quelles.** Coût ~0 (Room et le pipeline gèrent déjà ces 55 entrées sans effort), bénéfice élevé (profondeur éducative, raretés à collectionner, base pour les pistes 2.2). Jeter ce niveau de détail c'est se priver d'un axe que rien d'autre dans l'app ne fournit.

**Distinction nominale** : 40 / 55 chênes ont une page Wikipedia FR ; les summaries commencent quasi systématiquement par le nom vernaculaire (« Le Chêne pédonculé… », « Le Chêne vert ou Yeuse… »). Deux options pour extraire :
1. **Regex sur le 1er groupe nominal du `summary`** — simple, marche dans ~85 % des cas, mais pas 100 % fiable.
2. **Wikidata `P1843` (taxon common name) au moment du fetch** — plus propre, déjà à portée puisque `tools/build_dataset.py` stocke le `qid`. Recommandé.

Pour les 15 chênes sans WP (hapax, hybrides, cultivars rares — `Quercus phillyreoides`, `Q. x turneri`, `Q. robur (Fastigiata Group)`, etc.) : fallback sur le binôme latin tel quel.

### 3.2 Espèces `sp.` (genre connu, espèce inconnue) ?

**Recommandation : garder, avec libellé honnête.** Les 8 793 arbres `sp.` (4,1 %, dont 2 025 Tilleuls et 710 Chênes) sont des arbres physiquement présents qu'un joueur va vouloir scanner. Les supprimer crée des trous géographiques inexpliqués sur la carte (« je vois un arbre, l'app ne le voit pas »).

Affichage proposé :
- Fiche arbre : nom commun normal (« Chêne »), avec sous-titre `Quercus sp. — espèce précise non identifiée par la Ville de Paris`.
- Arboretum : les regrouper sous le nom commun parent plutôt que comme espèces à part entière. Un `Quercus sp.` capturé compte pour la card « Chêne » mais **pas** pour `55 / 55` espèces. Évite l'espèce-fantôme intrinsèquement non distinguable de ses vrais chênes.
- Badge potentiel : « Inspecteur » (cf. 2.2).

### 3.3 Entrées `Non spécifié` (genre + espèce inconnus) ?

**Recommandation : dégager côté script de build.** Les 3 entrées `Non spécifié` (677 arbres) ne portent aucune information exploitable et polluent toutes les vues (Arboretum, fiches, mini-cartes filtrées, badges). Filtrer dans `tools/build_dataset.py` au même endroit qui drop déjà les rows sans `genre`/`espece` — étendre la condition à `genre == "Non spécifié"`.

Conséquence : 677 pins disparaissent de la carte, mais ils ne portaient aucune valeur de jeu (rien à découvrir, fiche vide). Acceptable.

Alternative douce si on ne veut pas perdre la complétude géographique : les garder visibles en gris permanent, sans cliquabilité ni fiche. À débattre.

### 3.4 Coquilles OpenData ?

**Recommandation : table de remap manuelle dans le script de build**, à enrichir au fil des découvertes. Format envisagé :

```python
# tools/build_dataset.py
SPECIES_FIXUPS = {
    ("Olea", "europea"): ("Olea", "europaea"),
    # à compléter
}
```

Court, reproductible, audit-able dans git, n'invalide jamais les `sk` existants (la clé est dérivée déterministiquement, on remap avant indexation).

## 4. Traitement amont durable

L'objectif : **un nettoyage qui survit aux mises à jour OpenData sans intervention manuelle à chaque refresh**. Le bon endroit est `tools/build_dataset.py` qui tourne déjà à chaque rebuild du dataset.

Règles à graver dans le script :

1. **Drop dur** : `genre in {"Non spécifié", "", null}` → exclure de l'APK entier (pas même dans le GeoJSON).
2. **Tag `unknownSpecies: bool`** dans `ArbreEntity` (ou colonne dérivée dans `species-index.json`) pour `e in {"sp.", "n. sp."}`. L'UI s'en sert pour le sous-titre « espèce précise inconnue » et pour les exclure du compteur 907 espèces.
3. **Normalisation orthographique** : table `SPECIES_FIXUPS` (cf. 3.4), appliquée **avant** l'indexation `sk`.
4. **Extraction du nom vernaculaire FR** : nouveau champ `nv` dans `species-info.json`, peuplé via Wikidata `P1843` au même endroit que les summaries (déjà cachés dans `tools/.wikipedia-cache/`). Fallback regex sur le summary, fallback ultime sur le binôme latin.
5. **Sanity checks au build** (assert + log) :
   - « Si une espèce avec count > 100 perd sa page WP entre 2 builds, throw » → évite de découvrir en prod qu'une refonte WP a cassé un pan d'identification.
   - « Si un `sk` existant disparaît du nouveau CSV, warn » → garde-fou contre la casse d'index suite à une réorganisation OpenData.
   - « Si un genre inconnu apparaît avec count > 50, warn » → détection de nouveaux modes de saisie pourris côté Ville de Paris.

Tout ce traitement reste **côté outil de build**. L'app reste passive (lit du JSON propre) et il n'y a pas de migration Room à pousser à chaque cleanup. Les `sk` restent stables (jamais réindexés), seul le contenu de `species-info.json` s'enrichit.

## 5. À reprendre — checklist pour la prochaine session

Pour décision avant intégration BACKLOG / ROADMAP :

- [ ] **Garde-t-on les 55 espèces de chêne distinctes ?** (recommandé : oui)
- [ ] **Stratégie d'extraction du nom vernaculaire** : Wikidata `P1843` ou regex `summary` ou les deux ?
- [ ] **Comportement des entrées `sp.`** : regroupées sous le nc parent dans Arboretum, ou espèces à part entière comptées dans `907` ?
- [ ] **Entrées `Non spécifié`** : drop dur ou affichage gris non-cliquable ?
- [ ] **Compteur Arboretum à deux niveaux** (`X/221 nc` + `Y/907 esp`) : oui/non ?
- [ ] **Carte filtrée par nom commun** : phase ROADMAP ?
- [ ] **Mini-quiz d'identification** : à scoper ou rejeter ?
- [ ] **Badges « Inspecteur » / « Mosaïque de chênes »** : intéressants ou bruit ?
- [ ] **Sanity checks au build** (point 4.5) : tous, certains, aucun ?

## Annexe — sources de données

- `app/src/main/assets/species-index.json` (907 entrées `{i, g, e, nc?}`)
- `app/src/main/assets/species-info.json` (907 entrées `{i, stats, wp?, qid?, summary?, pdf?}`)
- `app/src/main/assets/dataset-stats.json` (`{totalArbres, totalEspeces, totalRemarquables}`)
- Pipeline source : `tools/build_dataset.py` (génère tous les assets ci-dessus à partir du CSV OpenData « les-arbres »)
