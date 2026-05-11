# tools/

Scripts de build hors `:app`. Stdlib uniquement, aucun pip install requis.

## `build_dataset.py`

Génère tous les assets data consommés par l'app à partir du CSV OpenData
`les-arbres`.

```bash
cd tools
python3 build_dataset.py
```

### Sorties

| Fichier (sous `app/src/main/assets/`) | Rôle |
|---|---|
| `databases/arbres-paris.db` | Room (fiche détail), schéma identique à `ArbreEntity`. |
| `arbres-paris.geojson` | MapLibre, source clusterisée (`id`, `remarquable`, `sk`). |
| `species-index.json` | Lookup `sk → {g, e, nc?, u?, nv, n?}` (cf. format ci-dessous). |
| `dataset-stats.json` | Totaux affichés en Arboretum (`totalArbres`, `totalEspeces`, `totalEspecesIdentifiees`, `totalRemarquables`). |
| `species-info.json` | Stats Paris + résumé Wikipedia FR + lien fiche PDF Ville de Paris par espèce. |
| `remarquables-info.json` | Qualification + résumé + cultivar pour chaque arbre remarquable. |
| `genre-info.json` | Résumé Wikipedia FR du genre + stats agrégées (count, top 3 espèces, top 3 arr) pour les 202 genres (sprint 8). |
| `splash-tips.json` | Banque ~240 tips rotatifs du `ColdStartSplash` (fusion statique + dataset). |

Le CSV brut OpenData (`les-arbres.csv`, ~80 Mo) est mis en cache dans `tools/`
au premier run et n'est pas re-téléchargé si déjà présent. Gitignore.

### À régénérer

- avant chaque release (pour avoir les données fraîches),
- quand le schéma `ArbreEntity` change (le `CREATE TABLE` du script doit
  rester synchrone avec ce que Room génère, sinon `createFromAsset()` crashe
  au premier lancement).

## Phases du build

`build()` enchaîne :

1. **Garde-fous + snapshot pré-build** — `load_existing_species_index()` lit
   l'ancien `species-index.json` (préservation des `sk` entre runs).
   `load_pre_build_state()` lit en plus l'ancien `species-info.json` pour
   capturer la présence WP par sk (entrée du sanity check #2).
2. **Lecture CSV + ingestion SQLite + GeoJSON streamé** — boucle unique sur
   ~213 k rows, agrégation par espèce (`count_by_sk`, `nom_commun_by_sk`,
   `heights_by_sk`, `circs_by_sk`, `arr_by_sk`).
3. **Fixups + normalisation** — `apply_species_fixups()` rebinde les coquilles
   latines (table `SPECIES_FIXUPS`) avant lookup `sk` ; `espece` vide ou
   `n. sp.` → forme canonique `sp.` ; `genre == "Non spécifié"` filtré dur
   (compteur séparé).
4. **`dataset-stats.json`** écrit (compte `totalEspecesIdentifiees` excluant
   les zombies count = 0 et les `u: true`).
5. **Wikidata SPARQL** — `resolve_via_wikidata()` (batchs de 50, cache
   permanent). Fournit `qid`, `wp` (titre Wikipedia FR), `vernacularNames`
   (P1843 @fr).
6. **Wikipedia REST** — `write_species_info()` récupère les summaries pour les
   espèces non encore cachées et écrit `species-info.json`.
7. **Cascade nv + Pokédex** — `compute_vernacular_and_pokedex()` enchaîne
   override → P1843 → frTitle → construit, désambigue les collisions, assigne
   les `n` Pokédex par `sk` croissant. Écrit `species-index.json`.
8. **Sanity checks** — `verify_species_invariants()` exécute les 5 contrôles
   ci-dessous. Raise sur les régressions structurelles, warn sur les signaux
   éditoriaux.
9. **Remarquables + splash tips** — `write_remarquables_info()` puis
   `write_splash_tips()` (fusion static + dataset).

## Sanity checks

| # | Action | Critère |
|---|---|---|
| 1 | **raise** | un `sk` connu pré-build a disparu du nouvel index |
| 2 | **raise** | une espèce avec `count > 100` a perdu sa page Wikipedia FR (cache `.wikidata-cache/{sk}.json` `miss=True` ou sans `wp`, alors qu'elle avait `wp` au build précédent) |
| 3 | **raise** | `> 50` rows OpenData portent `genre == "Non spécifié"` strict |
| 4 | **raise** | les `nv` finaux ne sont pas uniques après désambiguation |
| 5 | **warn** | une espèce > 1000 captures tombe sur la branche `construit` (candidat `VERNACULAR_OVERRIDES`) |

Le check #1 protège les captures Room déjà stockées chez l'utilisateur (qui
réfèrent les espèces par leur int). Le #2 attrape les renames d'articles
Wikipedia ou les pages supprimées. Le #3 attrape un drift OpenData ou une
régression du drop dur sprint 1. Le #4 verrouille la promesse d'unicité du
catalogue. Le #5 signale les espèces très courantes mais sans nom vernaculaire
résolu — candidats à override manuel curaté.

## Caches

Tous lazy permanents, gitignore. Pour invalider, supprimer le dossier :

| Cache | Contenu | Invalider |
|---|---|---|
| `.wikidata-cache/{sk}.json` | `qid`, `wp`, `summary`, `vernacularNames` | `rm -rf tools/.wikidata-cache` |
| `.wikipedia-aliases-cache/{sk}.json` | redirects vers l'article binôme (cascade nv) | `rm -rf tools/.wikipedia-aliases-cache` |
| `.wikipedia-genre-cache/{slug}.json` | summary Wikipedia FR du genre (sprint 8) | `rm -rf tools/.wikipedia-genre-cache` |
| `.remarquables-cache/` | API V2 paginated remarquables | `rm -rf tools/.remarquables-cache` |
| `.essences-cache/` | API V2 paginated fiches essences PDF | `rm -rf tools/.essences-cache` |

Les caches sont incrémentaux : seules les espèces nouvelles ou perdues sont
re-fetchées. Un build à chaud (caches présents) prend ~30 s ; un build à froid
~30 min (~907 espèces × Wikidata + Wikipedia REST throttled à 3 req/s).

## Format `species-index.json`

Tableau d'objets, un par espèce. Champs :

| Champ | Type | Notes |
|---|---|---|
| `i` | int | `sk` stable, persisté dans `CaptureEntity.speciesIndex`. **Jamais** réindexer. |
| `g` | str | Genre latin (ex. `Quercus`). |
| `e` | str | Épithète spécifique (ex. `robur`, `sp.`). |
| `nc` | str? | Nom commun le plus fréquent du CSV (`libellefrancais`). Optionnel. |
| `u` | bool? | `true` si entrée non identifiée (`sp.` / `n. sp.` / Non spécifié). Présent uniquement quand vrai. |
| `nv` | str | Nom vernaculaire **unique** post-désambiguation (titre Arboretum). |
| `n` | int? | Numéro Pokédex stable, identifiées (non `u`) avec `count > 0` seulement. |

Préservation des `sk` entre runs : critique pour les captures Room déjà
stockées chez l'utilisateur. Le script lit l'ancien `species-index.json` au
démarrage et réutilise les ids existants ; les nouveaux `(g, e)` héritent de
`max(sk) + 1`. Une corruption du fichier (`SpeciesIndexCorrupt`) avec une DB
asset présente fait `sys.exit(1)`.

## Tests

Helpers purs testés offline (pas de réseau, pas de CSV) :

```bash
cd tools
python3 -m unittest test_build_dataset -v
```

45 tests couvrent : fixups, détection unknown species, cascade nv,
désambiguation, intégration `compute_vernacular_and_pokedex`, snapshot
pré-build, et les 5 sanity checks (`VerifySpeciesInvariantsTest`).

## Notes

- Source : <https://opendata.paris.fr/explore/dataset/les-arbres/>
- Le dataset `arbresremarquablesparis` est utilisé en complément pour la
  fiche enrichie (qualification, résumé, cultivar).
- Trees sans `geo_point_2d` ou `idbase` valides sont ignorés silencieusement.
- Hauteur/circonférence à 0 sont stockées NULL (= donnée manquante).
