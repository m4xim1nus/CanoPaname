# CanoPaname — Third-Party Notices

CanoPaname est distribué sous licence MIT (voir [LICENSE](LICENSE)).
Cette application incorpore des œuvres tierces sous des licences distinctes,
listées ci-dessous. Pour chaque œuvre, le copyright et la licence d'origine
sont conservés.

CanoPaname est un projet indépendant et n'est **pas affilié à la Ville de Paris**.

## Données

### Arbres de Paris

Le jeu de données embarqué dans l'APK est dérivé des datasets suivants :

- **« Les arbres »** — © Ville de Paris, OpenData Paris
  <https://opendata.paris.fr/explore/dataset/les-arbres/>
- **« Les arbres remarquables »** — © Ville de Paris, OpenData Paris
  <https://opendata.paris.fr/explore/dataset/arbresremarquablesparis/>

Ces deux datasets sont publiés sous **Open Database License (ODbL) v1.0** :
<https://opendatacommons.org/licenses/odbl/1-0/>

La base SQLite embarquée (`app/src/main/assets/databases/arbres-paris.db`)
est une **Derivative Database** au sens de l'ODbL §4.4 et reste sous ODbL.
Le script de transformation est publié dans ce repository
(`tools/build_dataset.py`). La notice ODbL §4.3 accompagne la base dans
`app/src/main/assets/databases/ODbL-NOTICE.txt`.

### Tuiles cartographiques

- **OpenFreeMap** — <https://openfreemap.org> (licence MIT, attribution
  recommandée).
- **Données OpenStreetMap** — © OpenStreetMap contributors, ODbL
  <https://www.openstreetmap.org/copyright>

L'attribution est affichée à l'utilisateur via le bouton « ⓘ » natif de
MapLibre sur l'écran carte.

### Fiches espèces

Les résumés et liens présentés dans la fiche-espèce proviennent de la
**Wikipédia francophone** (API REST `summary`). Ces contenus sont rédigés
par les contributeurs de Wikipédia et publiés sous licence
**Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)** :
<https://creativecommons.org/licenses/by-sa/4.0/>

L'attribution CC BY-SA est affichée sous chaque résumé dans l'application,
avec un lien vers le texte de la licence.

### Fiches essences (Guide des essences de Paris)

Les caractéristiques botaniques, descriptions d'identification, proses
« L'essence à Paris » et services écosystémiques présentés dans la
fiche-espèce sont extraits du dataset **« Fiches Essences du Guide des
Essences de Paris »** — © Ville de Paris, OpenData Paris :
<https://opendata.paris.fr/explore/dataset/fiches-essences-du-guide-des-essences-de-paris/>

Ce dataset est publié sous **Open Database License (ODbL) v1.0** :
<https://opendatacommons.org/licenses/odbl/1-0/>

Les textes sont extraits par le script `tools/build_dataset.py` (via
`tools/essence_pdf.py`) et pré-cuits dans `app/src/main/assets/species-info.json`.
L'attribution « Source : Ville de Paris · Guide des essences » est affichée
dans l'encart correspondant de la fiche-espèce.

## Polices

### Fraunces SemiBold

Copyright 2020 The Fraunces Project Authors
(<https://github.com/undercasetype/Fraunces>) avec Reserved Font Name
« Fraunces ». Designée par Phaedra Charles et Flavia Zimbardi
(Undercase Type).

Sous licence **SIL Open Font License v1.1**.
Texte intégral : `app/src/main/assets/licenses/Fraunces-OFL.txt`.

## Bibliothèques

- **MapLibre Native Android SDK 11.11.0** — BSD-2-Clause
  © MapLibre contributors — <https://github.com/maplibre/maplibre-native>
- **AndroidX, Jetpack Compose, Material 3** — Apache-2.0 — © Google LLC
- **Kotlin / kotlinx.coroutines** — Apache-2.0 — © JetBrains s.r.o.
- **Room** — Apache-2.0 — © Google LLC
- **DataStore Preferences** — Apache-2.0 — © Google LLC
- **org.json** — JSON License — © 2002 JSON.org
