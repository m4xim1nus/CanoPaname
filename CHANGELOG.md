# Changelog

Format basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions [SemVer](https://semver.org/lang/fr/).

## [1.0.2] — 2026-05-10

Profondeur et lisibilité après v1.0.1, sans casse de schéma. Six sprints atomiques sous le codename *Photos et progressivité* : re-capture + suppression de captures, refonte `PhotoLightbox` (bornes zoom/pan, swipe entre photos), refonte badges en multi-paliers visibles (catalogue 13 → 8, 22 paliers cumulés), saut vers l'arbre exact sur la carte (fly-to + pulse), galerie photos cliquable dans le sheet de détail arbre.

### Ajouté

- Sheet détail : bouton « Recapturer » dès qu'une espèce ou un remarquable est débloqué (modèle Room déjà N captures par arbre, pas de nouveau schéma).
- Galerie photos : long-press sur une vignette ou icône poubelle dans la lightbox plein-écran ouvrent un dialog de confirmation. Si c'est la dernière capture de l'espèce / du remarquable, le dialog prévient du re-verrouillage et la suppression renvoie sur la Map.
- Badges : 3 badges progressifs multi-paliers `Marcheur` (1/10/25/50/100/250 captures), `Botaniste` (1/10/25/50/100/200 espèces), `Chasseur` (1/5/10/25/50 remarquables). Card pleine largeur avec barre + jalons et score absolu (« 37 / 50 »). Catalogue passe de 13 à 8 badges (5 binaires + 3 progressifs, 22 paliers au total).
- Saut vers un arbre exact sur la carte depuis ses points de contact : bouton « Voir sur la carte » plein écran sur la fiche-remarquable, icône Map en haut à gauche de la `PhotoLightbox` (universelle, fiche-espèce comme fiche-remarquable). La nav passe par `Routes.map(arbreId)` (query param `pulseArbreId`), qui déclenche un fly-to ~600 ms à zoom 20 (très fort zoom — un seul pin à l'écran, aucun doute sur l'individu ciblé) et un pulse blanc 2 s sur la position. Pas d'ouverture du sheet : on tape l'arbre soi-même si on veut la fiche.
- Sheet détail arbre : galerie photos cliquable (`PhotoGallery` avec vignettes 120 dp et titre « Tes photos (N) ») en lieu et place du texte « N photo(s) de capture ». Click → `PhotoLightbox` plein écran (zoom/pan/swipe). Long-press → dialog suppression avec wording adaptatif (re-verrouillage si dernière capture de l'espèce ou du remarquable, recompose en `UnknownContent` via les Flows réactifs).

### Modifié

- `PhotoLightbox` : pinch-zoom toujours 1×→5× avec pan désormais clampé aux bords (calcul `boxSize × ratio bitmap × scale`), plus de photo qui s'évade en vignette dans un coin. Galerie ≥ 2 photos : navigation entre photos via `HorizontalPager` (swipe horizontal + chevrons `Outlined.ChevronLeft/Right` désactivés aux bornes), pager gelé dès `scale > 1f`. Détecteur custom `awaitEachGesture` qui ne consomme rien à 1 doigt + scale=1 (laisse passer le pager) et reset auto du zoom à la transition de page.
- Profil : « Derniers badges » liste maintenant les 3 derniers événements de déblocage (chaque palier de progressif compte indépendamment).
- Compteur global de l'écran Badges passe de « X / 13 » à « X / 22 » paliers — progression plus continue qu'un saut binaire.

### Retiré

- 8 badges binaires absorbés par les nouveaux progressifs : `FIRST_CAPTURE`, `PROMENADE`, `MARCHEUR`, `CENTURION` (→ `Marcheur`) ; `BOTANISTE_AMATEUR`, `BOTANISTE_CONFIRME` (→ `Botaniste`) ; `CHASSEUR_REMARQUABLES`, `LEGENDE` (→ `Chasseur`). Pas de migration Room (les badges sont calculés depuis les captures).

## [1.0.1] — 2026-05-09

Patch dette + UX. Cinq sprints atomiques sous le codename *Vérité & Friction*. Aucune casse de schéma, aucune nouvelle feature visible.

### Ajouté

- Map : pulse FAB GPS + snackbar « Localisation en cours… » pendant le gap permission → 1er fix (timeout 30 s avec warning « GPS indisponible — sors à découvert »).
- Map : reprise GPS automatique quand l'utilisateur réactive la localisation système (BroadcastReceiver `PROVIDERS_CHANGED_ACTION`).
- Map : ring orange autour des clusters contenant ≥ 1 remarquable capturé (mémorisation visuelle, indépendant du fill `discovered_count`).
- Détail arbre : haptique `LongPress` à l'ouverture du sheet.
- Capture : snackbar + tic discret à l'annulation de l'app caméra.
- Profil : compteurs « Espèces du Catalogue » `X / 907 (Y %)` et « Arbres déverrouillés » `X / 213 042 (Y %)`.
- Profil : section « Derniers badges » (row des 3 plus récents `unlockedAt`, masquée si vide).
- Profil : label texte + timeout 60 s sur la barre de progression export/import.

### Modifié

- Documentation : mention explicite des 528 fiches espèces enrichies (sur 907) dans le pitch ; précision « tuiles OpenStreetMap via OpenFreeMap, sans envoi de données personnelles » dans la section vie privée du README.
- Fiches remarquables enrichies (qualification, résumé, description, cultivar) accessibles après capture.
- Map : icône FAB ★ passe de la loupe à l'étoile (`Outlined.Star`, tint orange remarquable).
- Map : snackbar distance remarquable allongée à 5 s (helper `showSnackbarFor` partagé).
- Détail arbre : copy `UnknownContent` rappelle la mécanique de déverrouillage par espèce ; `CaptureAvailability.TooFar` affiche la distance courante (« Trop loin (X m / max 30 m). Rapproche-toi. »).
- Capture : haptique déplacée du post-INSERT vers le tap « Capturer » (perçu immédiat avant l'ouverture caméra).
- Empty state des écrans de découverte : `bodyMedium` (14 sp) → `bodyLarge` (16 sp).
- Profil : remplacement de la card unique « Première capture » par la section « Derniers badges ».

### Retiré

- UI saisonnalité (sélecteur de saison sur Arboretum / Remarquables / Profil, bandeau d'archive, ambiance saisonnière sur la carte). Le schéma DB et l'enum `Season` sont conservés pour le futur cycle Variantes.
- 2 badges saisonniers `RONDE_DES_SAISONS` et `ANNEE_COMPLETE` (catalogue passe de 15 à 13 badges).
- Bullets `welcome_bullet_*` du WelcomeScreen (déclarés dans `strings.xml` mais jamais rendus depuis longtemps).

### Corrigé

- Profil : la 1re capture posée la veille au soir s'affiche désormais « il y a 1 jour » au lieu d'« aujourd'hui » (bascule `LocalDate` + `ChronoUnit.DAYS.between` zone Europe/Paris, plus de fenêtre 24 h glissante).

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
