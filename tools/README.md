# tools/

Scripts de build hors `:app`. Aucun pip install requis (stdlib uniquement).

## `build_dataset.py`

Génère la base SQLite pré-cuite des arbres parisiens consommée par Room.

```bash
cd tools
python3 build_dataset.py
```

Sortie : `app/src/main/assets/databases/arbres-paris.db` (~30-50 Mo).

Le CSV brut OpenData (`les-arbres.csv`, ~80 Mo) est mis en cache dans `tools/`
au premier run et n'est pas re-téléchargé si déjà présent. Les deux fichiers
sont git-ignorés (`.gitignore` racine + `app/src/main/assets/databases/*.db`)
— le script Python est la source de vérité, on régénère à la demande.

À régénérer :
- avant chaque build de release (pour avoir les données fraîches),
- quand le schéma `ArbreEntity` change (le `CREATE TABLE` du script doit
  rester synchrone avec ce que Room génère, sinon `createFromAsset()` crashe
  au premier lancement).

## Notes

- Source : <https://opendata.paris.fr/explore/dataset/les-arbres/>
- Le dataset `arbresremarquablesparis` n'est pas utilisé : `les-arbres` a
  déjà une colonne `remarquable` (OUI/NON) suffisante pour le MVP.
- Trees sans `geo_point_2d` ou `idbase` valides sont ignorés silencieusement.
- Hauteur/circonférence à 0 sont stockées NULL (= donnée manquante).
