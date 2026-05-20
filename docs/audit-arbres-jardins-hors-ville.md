# Sources de données pour les jardins hors-juridiction Ville de Paris

> Document de travail — convo d'exploration du 2026-05-20.
>
> Point de départ : constat utilisateur (screenshots app v1.4.0) que plusieurs grands jardins parisiens sont quasi vides d'arbres dans le dataset — Jardin du Luxembourg, Jardin des Tuileries, Jardin des Plantes, Parc de la Villette.
>
> Objet : déterminer s'il existe une source open data normalisable/agrégeable avec le dataset OpenData Paris « les-arbres » pour combler ces trous.
>
> **Statut : recherche incomplète.** Le verdict OSM n'a pas pu être rendu — le sandbox de la session bloque l'accès réseau à Overpass. Aucune décision prise, aucun code modifié.

---

## 1. Le problème

Le dataset (`arbres-paris.geojson`, 217 264 arbres, construit depuis le CSV OpenData « les-arbres ») couvre uniquement le patrimoine **géré par la Ville de Paris** : essentiellement des arbres de voirie (rues, quais, contre-allées). Les arbres situés **à l'intérieur** des grands jardins gérés par d'autres entités publiques sont absents ; seuls leurs périmètres apparaissent, via les arbres des rues qui les bordent.

Une bounding box large autour de ces jardins remonte 200-700 arbres et donne une fausse impression de couverture — ce sont les arbres de voirie périphériques. Avec une bounding box resserrée sur l'intérieur strict :

| Site | Gestionnaire | Arbres dans la base (intérieur strict) | Ordre de grandeur réel |
|---|---|--:|--:|
| Jardin du Luxembourg | Sénat | 243 | ~2 980 |
| Jardin des Plantes | MNHN | 79 | ~2 000 (270+ espèces) |
| Jardin des Tuileries | Louvre / CMN | 42 | plusieurs centaines |
| Parc de la Villette | EPPGHV | 1 100 | mal connu, vraisemblablement partiel |

Le trou est généralisé sur les 4 sites, pas seulement le Luxembourg.

## 2. Couverture des sites Ville de Paris — OK, hors périmètre

Vérification directe dans `arbres-paris.geojson` : les cimetières, bois et parcs urbains gérés par la Ville sont bien couverts, aucun trou.

| Zone (Ville de Paris) | Arbres | Zone | Arbres |
|---|--:|---|--:|
| Bois de Vincennes (centre) | 8 712 | Cimetière Montparnasse | 2 082 |
| Bois de Boulogne (centre) | 5 000 | Pré-Catelan / Bagatelle | 1 726 |
| Père-Lachaise | 4 363 | Bercy | 1 258 |
| Champs-Élysées (jardins) | 3 171 | Cimetière Montmartre | 1 177 |
| Buttes-Chaumont | 2 457 | Parc Monceau | 880 |
| Champ de Mars | 2 445 | Invalides | 632 |
| | | Pitié-Salpêtrière | 322 |
| | | Cimetière de Passy | 334 |

→ Le périmètre des trous se limite réellement aux 4 jardins gérés hors-Ville.

## 3. Sources externes évaluées

### 3.1 MNHN — Jardin des Plantes : fiable à la source, **non distribuée**

- Le MNHN possède une base interne complète et géolocalisée du Jardin des Plantes : ~2 000 arbres, 8 500 espèces/variétés au total, étiquetés et suivis depuis le XVIIᵉ siècle. C'est cette base qui alimente l'app iOS *Hortus Botanica* (nom, famille, origine, année de plantation, floraison, photos, géolocalisation).
- Mais **rien d'exploitable en open data** :
  - seul un PDF du plan général est public ;
  - le dataset GBIF du MNHN (`92b3e8d2-f171-457a-988b-84ee5a03bd31`, « Données naturalistes acquises sur les sites du MNHN ») contient de la donnée d'**observation naturaliste** (faune/flore observée), pas un inventaire d'arbres plantés avec positions ;
  - le portail INPN est partiellement hors-service depuis la cyberattaque de l'été 2025 ;
  - numérisation des collections MNHN : 15,4 % seulement (chiffre 2020).
- **Verdict** : la donnée est fiable et complète à la source, mais n'est pas distribuée → **non agrégeable automatiquement**. Seule voie possible : demande de dump directe au MNHN (envisageable pour un projet perso, mais hors scope outillage).

### 3.2 OpenStreetMap — seule piste automatisable, **verdict non rendu**

OSM est la seule source potentiellement exploitable au build-time : universelle (couvre les 4 sites en une requête Overpass), format standardisé (`natural=tree` + `species=` + parfois `genus`, `height`, `circumference`), licence ODbL compatible avec un usage perso.

**L'audit live n'a pas pu être réalisé** : depuis le sandbox de cette session, tous les endpoints OSM répondent HTTP 403 (Overpass principal `overpass-api.de`, miroirs `overpass.kumi.systems` et `lz4.overpass-api.de`, ainsi que `taginfo.openstreetmap.org`).

Appréciation qualitative seulement, **à confirmer** :
- **Luxembourg & Tuileries** : sites touristiques centraux, historiquement très mappés par la communauté OSM Paris — bonne couverture probable, `species=` souvent renseigné.
- **Jardin des Plantes** : mapping probablement irrégulier — allées principales oui, mais l'arboretum dense (270+ espèces sur 23 ha) dépend de contributions ponctuelles.
- **Villette** : couverture moins probable, site moins prisé des mappers.

**Critère de décision** : le ratio `species= / total` mesuré par site. Au-dessus de ~70 %, OSM est agrégeable proprement ; en dessous, beaucoup d'arbres seraient écartés (la règle « minimum viable » exige genre + espèce).

Requêtes à exécuter hors sandbox pour trancher :

```bash
for zone in \
  "Luxembourg:48.8431,2.3322,48.8480,2.3389" \
  "Tuileries:48.8616,2.3260,48.8657,2.3320" \
  "Plantes:48.8420,2.3550,48.8466,2.3620" \
  "Villette:48.8870,2.3830,48.8990,2.3960"; do
  name=${zone%%:*}; bbox=${zone#*:}
  curl -sG 'https://overpass-api.de/api/interpreter' -A 'CanoPaname-audit' \
    --data-urlencode "data=[out:json][timeout:60];node[\"natural\"=\"tree\"]($bbox);out body;" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);n=d['elements'];sp=sum(1 for x in n if x.get('tags',{}).get('species'));print(f'$name: {len(n)} arbres, {sp} avec species=')"
done
```

### 3.3 Sénat — Luxembourg : pas de dump

`jardin.senat.fr` publie des statistiques agrégées (~2 980 arbres, 50 % marronniers, 16 % tilleuls, 12 % platanes ; verger conservatoire de ~1 000 arbres, 379 variétés de pomme, 247 de poire) mais **aucun inventaire géolocalisé téléchargeable**. Inutilisable directement.

### 3.4 CMN / data.culture.gouv.fr — Tuileries : non concluant

Aucun inventaire arboricole structuré identifié pour les Tuileries côté Centre des monuments nationaux / Louvre. OSM resterait la couverture par défaut.

## 4. Arbitrages pris pendant la conversation

Si une agrégation est décidée plus tard, trois choix ont déjà été tranchés par l'utilisateur :

1. **Périmètre** : les 4 jardins cités + tout site géré hors-Ville détecté automatiquement (approche balayage par proximité, pas une liste figée).
2. **Stratégie d'identifiant** : namespace par source via plages réservées dans `idbase` (Long), p. ex. arbres OSM encodés `1_000_000_000_000 + osm_node_id`. Pas de nouvelle colonne `source` — la plage d'`idbase` porte l'information. Aucune migration Room nécessaire (le schéma accepte déjà des `idbase` arbitrairement grands).
3. **Qualité** : minimum viable — on accepte tout arbre disposant d'au moins lat/lon/genre/espèce. `circonferenceCm`, `hauteurM`, `adresse`, `varieteCultivar` restent nullable (l'UI gère déjà ce cas).

## 5. Piste d'implémentation esquissée (non décidée)

Si OSM passe le critère du §3.2, l'intégration se ferait dans `tools/build_dataset.py` :
- nouvelle fonction `fetch_osm_trees(bbox)` interrogeant Overpass, avec cache local sous `tools/.osm-cache/` (même pattern que `tools/.wikipedia-cache/`) ;
- normalisation de chaque nœud vers le schéma cible ; résolution genre/espèce depuis `tags["species"]` (binôme latin) ;
- déduplication spatiale : tout arbre OSM à moins de ~5 m d'un arbre Paris CSV est ignoré (la dédup se déclenchera surtout sur les périmètres ; l'intérieur des jardins n'a rien à dédupliquer) ;
- les espèces nouvelles s'ajoutent en fin de `species-index.json`, les `sk` existants restant préservés (mécanique déjà en place) ;
- penser à régénérer `arr-species.json` (dénominateur des badges « familier_arr_* ») après le merge.

Mention de licence « © contributeurs OpenStreetMap » à ajouter dans `NOTICE.md` et l'écran crédits de l'app.

## 6. Questions ouvertes / recherche à finir

- **Bloquant** : exécuter les 4 requêtes Overpass du §3.2 hors sandbox et reporter ici les compteurs `total / species=` par site. Sans ces chiffres, impossible de statuer sur l'agrégation OSM.
- Décision à prendre ensuite : si le ratio `species=` est bon → implémenter l'agrégation OSM ; sinon → écarter, ou se rabattre sur une demande de dump au MNHN pour le seul Jardin des Plantes.
- Couverture OSM réelle du Parc de la Villette à confirmer (le 1 100 actuel est peut-être déjà acceptable).

## Sources

- [MNHN — Jardin des Plantes](https://www.mnhn.fr/en/jardin-des-plantes-garden-of-plants)
- [App Hortus Botanica — inventaire géolocalisé des jardins MNHN](https://apps.apple.com/fr/app/hortus-botanica/id1527051518)
- [GBIF — Données naturalistes acquises sur les sites du MNHN](https://www.gbif.org/dataset/92b3e8d2-f171-457a-988b-84ee5a03bd31)
- [Sénat — Le Jardin du Luxembourg en chiffres](https://jardin.senat.fr/botanique/visiter-le-jardin-du-luxembourg-renovation-des-arbres/visiter-le-jardin-les-arbres-en-chiffres.html)
- [OpenStreetMap Wiki — Tag:natural=tree](https://wiki.openstreetmap.org/wiki/Tag:natural=tree)
