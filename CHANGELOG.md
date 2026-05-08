# Changelog

Format basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions [SemVer](https://semver.org/lang/fr/).

## [Unreleased]

### Modifié

- Documentation : mention explicite des 528 fiches espèces enrichies (sur 907) dans le pitch ; précision « tuiles OpenStreetMap via OpenFreeMap, sans envoi de données personnelles » dans la section vie privée du README.
- Fiches remarquables enrichies (qualification, résumé, description, cultivar) accessibles après capture.

### Retiré

- UI saisonnalité (sélecteur de saison sur Arboretum / Remarquables / Profil, bandeau d'archive, ambiance saisonnière sur la carte). Le schéma DB est conservé pour le futur cycle Variantes.
- 2 badges saisonniers `RONDE_DES_SAISONS` et `ANNEE_COMPLETE` (catalogue passe de 15 à 13 badges).

### Corrigé

- *(à compléter à la rotation : bug date « aujourd'hui » sur Profil, etc.)*

## [1.0.0] — 2026-05-05

### Ajouté

- Carte plein écran de 213 042 arbres parisiens (clusters MapLibre, OpenFreeMap).
- Capture par proximité GPS (< 30 m) + photo locale.
- Arboretum à 907 espèces avec fiche enrichie (Wikipedia FR, stats Paris, mini-carte filtrée).
- Pokédex remarquables dédié (169 fiches, lien fiche PDF Ville de Paris).
- 15 badges en 6 catégories, évalués depuis les captures.
- Saisonnalité 4 saisons, mode archive read-only.
- Profil avec stats Global / Saison vive.
- Export / import ZIP local (Storage Access Framework, dédup idempotent).
- Onboarding + écran « Comment jouer » rejouable.
- Splash cold start avec tips informatifs rotatifs.
- Coloration progressive des clusters carte selon découvertes.

### Privacy

- 100 % local. Aucune télémétrie, aucun compte, aucun service tiers au runtime.
