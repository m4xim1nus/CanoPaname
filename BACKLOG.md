# Backlog

File d'attente brute. Tout retour, idée ou bug atterrit ici en 1 ligne au format :

```
- [TAG] description courte (origine, date)
```

**Tags** : `[ ]` à trier, `[→Codename]` rangé dans un cycle, `[creuser]` mérite réflexion avant arbitrage, `[refusé]` tranché négatif.
**Origines** : `audit`, `user:moi`, `user:F&F`, `gh#N`, `device-test`.

Triage en lot au début de chaque cycle. Process complet dans `CLAUDE.md` (*Workflow & docs*).

---

## Cycle Photos et progressivité

- ~~[→PhotosEtProgressivité] Re-capture autorisée et lisible : bouton `Capturer` → `Recapturer` sur arbre déjà capturé, pipeline inchangé~~ — livré sprint 1 (2026-05-09)
- ~~[→PhotosEtProgressivité] Suppression d'une capture (icône PhotoLightbox + long-press PhotoGallery), avec dialog uncapture si dernière de l'espèce → nav back Map~~ — livré sprint 1 (2026-05-09)
- [→PhotosEtProgressivité] PhotoLightbox : bornes zoom (pas plus petit que le cadre) + bornes pan (bords scotchés au cadre) + navigation entre photos (swipe horizontal + chevrons gauche/droite) (user:moi, 2026-05-09)
- [→PhotosEtProgressivité] Refonte badges progressifs : 3 badges multi-paliers (Marcheur 1/10/25/50/100/250, Botaniste 1/10/25/50/100/200, Chasseur 1/5/10/25/50), catalogue 13 → 10 (audit-B + user:moi, 2026-05-07)
- [→PhotosEtProgressivité] Sauter à un arbre sur la carte depuis (a) fiche remarquable et (b) PhotoLightbox de n'importe quel arbre capturé : param `pulseArbreId` sur `Routes.MAP`, fly-to ~600 ms + pulse 2 s (user:moi, 2026-05-07 ; élargi 2026-05-09)
- [→PhotosEtProgressivité] Galerie photos dans le sheet de l'arbre (remarquable et non) : remplacer le texte « N photo(s) de capture » par `PhotoGallery` cliquable → `PhotoLightbox` + long-press suppression (user:moi, 2026-05-10)

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_4_5` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)
- [→Variantes] Sort des entrées `Non spécifié` (677 arbres, 3 entrées) : drop dur côté `tools/build_dataset.py` ou affichage gris non-cliquable ? (claude:analyse, 2026-05-08)
- [→Variantes] Sort des entrées `sp.` / `n. sp.` (8 793 arbres, 4,1 % du dataset) : tag `unknownSpecies` + regroupement Arboretum sous le `nc` parent ? (claude:analyse, 2026-05-08)
- [→Variantes] Table `SPECIES_FIXUPS` côté `tools/build_dataset.py` pour coquilles OpenData (`Olea europea` → `europaea`, etc.) (claude:analyse, 2026-05-08)
- [→Variantes] Extraction nom vernaculaire FR (Wikidata `P1843` propre vs regex summary ~85 % vs les deux) (claude:analyse, 2026-05-08)
- [→Variantes] Compteur Arboretum à deux niveaux (`X / 221 noms communs` + `Y / 907 espèces`) (claude:analyse, 2026-05-08)
- [→Variantes] Carte filtrée par nom commun (set de `sk` fusionnés en expression `match`) (claude:analyse, 2026-05-08)
- [→Variantes] Sanity checks au build dataset (espèce > 100 perd sa page WP, `sk` existant disparaît, nouveau genre `Non spécifié` count > 50) (claude:analyse, 2026-05-08)
- [→Variantes] Affichage du nom vernaculaire FR sur la fiche-espèce (claude:analyse, 2026-05-08)
- [→Variantes] Tranches de fréquence Arboretum (+10k, 2k-10k, 1k-2k, 100-1k, <100) avec sticky headers, onglet LISTE — décalé du cycle Photos parce que plus cohérent **après** le nettoyage catalogue d'espèces (user:moi, 2026-05-07 ; décalé 2026-05-09)

## Cycle Endgame

- [→Endgame] Maîtrise par arrondissement : carte chromatique + badge « Maître du Xe » (audit V2#5, 2026-05-06)
- [→Endgame] Quêtes hebdomadaires locales, opt-in, sans push (audit V2#3, 2026-05-06)
- [→Endgame] Pré-affichage fiche remarquable enrichie même non capturé, bandeau « Pas encore découvert » (audit-B, 2026-05-06)
- [→Endgame] Fallback Wikipedia 379 espèces : « Famille X. Y individus à Paris. » (audit-B, 2026-05-06)

## À creuser

- [creuser] WelcomeScreen pas lu, intro depuis la carte (user:F&F + user:moi 2026-05-07 : pas prio mais à reconsidérer post-Photos)
- [creuser] Bouton partage PNG sur fiche espèce (audit-C, tension single-player vs F&F à trancher)
- [creuser] Mini-platane signature visuelle en session (TopAppBar Map ou Profil) (audit-E1)
- [creuser] Unifier espace lexical Arboretum / Catalogue / Pokédex (audit-E2 : recommande Arboretum pour l'UI)
- [creuser] Refonte modèle remarquables : espèce-boss vs vraie quête (audit V2#2 : décision structurelle)
- [creuser] Notifications push : digest mensuel opt-in vs rien (audit-tension#1)
- [creuser] Compteur Arboretum « X / 907 » vs « X / 528 » dans l'UI (audit-tension#3)
- [creuser] Phénologie réelle (dates floraison/feuillage par espèce) — décision structurante v2 (audit V2#1)
- [creuser] Étendre screenshots README de 3 à 6 (audit#17 : à faire après Photos pour avoir les nouveaux écrans)
- [creuser] Script `tools/scout_other_cities.py` qui interroge OpenData de villes du Grand Paris et produit un md de faisabilité (user:moi, 2026-05-07)
- [creuser] Mini-quiz d'identification entre espèces partageant le même `nc` (Quercus robur vs petraea, Tilia cordata vs platyphyllos) (claude:analyse, 2026-05-08)
- [creuser] Badges « Inspecteur » (capturer N arbres `sp.`) et « Mosaïque de chênes » (10 espèces sous le même `nc`) (claude:analyse, 2026-05-08)

## Refusé

- [refusé] Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche espèce non capturée (audit-A, 2026-05-06 ; user:moi 2026-05-09 : philosophie « découverte en marchant », la 81e espèce se trouve en tapant un pin gris à proximité, le côté quête est porté par les Remarquables ★)
- [refusé] Table `photo` 1:N + backup `schemaVersion = 2` (BACKLOG cycle Photos d'origine ; user:moi 2026-05-09 : le modèle Room actuel 1 capture = 1 photo supporte déjà N photos par arbre/espèce via N captures, pas de migration nécessaire)
- [refusé] Cold-start « 7-10 s freeze » signalé par audit (user:moi 2026-05-07 : audit faux, pas de problème de temps long bloquant au 1er lancement)
- [refusé] 4 badges saisonniers v1.1.0 proposés par audit (user:moi 2026-05-07 : caduques, on supprime les saisons)
- [refusé] Mini-transition d'ambiance switch saison (audit-D2 ; user:moi 2026-05-07 : caduque, suppression saisons)
- [refusé] Anticlimax du déblocage des 38 147 platanes (audit-tension#4 ; audit lui-même recommande de laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax)
