# Backlog

File d'attente brute. Tout retour, idée ou bug atterrit ici en 1 ligne au format :

```
- [TAG] description courte (origine, date)
```

**Tags** : `[ ]` à trier, `[→Codename]` rangé dans un cycle, `[creuser]` mérite réflexion avant arbitrage, `[refusé]` tranché négatif.
**Origines** : `audit`, `user:moi`, `user:F&F`, `gh#N`, `device-test`.

Triage en lot au début de chaque cycle. Process complet dans `CLAUDE.md` (*Workflow & docs*).

---

## Cycle Catalogue (en cours)

Détail complet dans `ROADMAP.md` *Cycle en cours*. Items absorbés (l'index reste lisible pour la rotation finale) :

- [→Catalogue] Drop dur des entrées `Non spécifié` (811 arbres, 4 formes — `sp.`, `n. sp.`, vide, `americana`) côté `tools/build_dataset.py` (claude:analyse, 2026-05-08 ; chiffres affinés 2026-05-10)
- [→Catalogue] Tag `unknownSpecies` (champ `u`) pour entrées `sp.` / `n. sp.` / espece vide normalisée, libellé construit « {nc} (espèce indéterminée) », non comptées dans `totalEspecesIdentifiees` (claude:analyse, 2026-05-08 ; arbitré 2026-05-10)
- [→Catalogue] Normalisation des 4 813 lignes avec `espece` vide (1 594 `Ulmus;`, 1 594 `Prunus;`, ~1 600 autres) en forme canonique `sp.` (claude:analyse, 2026-05-10)
- [→Catalogue] Table `SPECIES_FIXUPS` côté script pour coquilles OpenData (`Olea europea` → `europaea`, 33 oliviers ; à enrichir au fil de l'eau), appliquée avant indexation `sk` (claude:analyse, 2026-05-08)
- [→Catalogue] Extraction du nom vernaculaire FR : Wikidata `P1843` prioritaire (`qid` déjà stocké), regex summary WP en fallback, construction `{nc} ({Initiale}. {epithète})` en ultime, override manuel `VERNACULAR_OVERRIDES` (claude:analyse, 2026-05-08 ; arbitré 2026-05-10)
- [→Catalogue] Désambiguation automatique des collisions `nv` + assert d'unicité au build (raise si non-unique) (claude:analyse, 2026-05-10)
- [→Catalogue] Champ `nv` (nom vernaculaire unique) + `n` (numéro Pokédex stable) + `u` (flag) ajoutés dans `species-index.json`. `totalEspecesIdentifiees` ajouté dans `dataset-stats.json` (claude:analyse, 2026-05-10)
- [→Catalogue] Affichage du `nv` partout en titre, binôme latin en sous-titre italique (Arboretum, fiche-espèce, fiche-arbre) (claude:analyse, 2026-05-08)
- [→Catalogue] Numérotation Pokédex `#N` stable sur les espèces identifiées seulement ; `unknownSpecies` toujours en fin de catalogue, sans `#`, section visuellement distincte (user:moi, 2026-05-10)
- [→Catalogue] Auto-débloquage des fiches `sp.` : capture d'un `Tilia X` quelconque débloque la fiche `Tilia (espèce indéterminée)`, sans photo dédiée requise. Galerie photos reste alimentée par les seules captures explicites de `sp.` (user:moi, 2026-05-10)
- [→Catalogue] Sanity checks au build (raise) : espèce > 100 perd sa page WP entre 2 builds, `sk` existant disparaît, genre `Non spécifié` réapparaît count > 50, `nv` final non-unique. Warn pour fallback construit sur espèce > 1000 captures (claude:analyse, 2026-05-08)
- [→Catalogue] Compteur Arboretum principal `X / ~800` (espèces identifiées seules), ligne « + N captures à espèce indéterminée » côté Profil. **Pas** de double compteur 221/907 (arbitré 2026-05-10 contre la piste « X / 221 nc » du doc d'analyse)
- [→Catalogue] Fiche `(G, sp.)` enrichie : sous la galerie photos `sp.`, mini-catalogue filtré sur le genre — mêmes cards que l'Arboretum complet (`nv`, `#N`, count, photo 1re capture pour les capturées, silhouette « ??? » + binôme latin grisé pour les non-capturées). Lien vers la fiche-espèce sur tap. Donne au joueur une vue immédiate « j'ai 3/55 chênes » sans quitter la fiche genre (user:moi, 2026-05-10)
- [→Catalogue] Carte filtrée depuis la fiche `(G, sp.)` : extension de `MAP_FILTERED` à un set de `sk` (tous les `sk` du genre déjà capturés + le `sk` de l'entrée `(G, sp.)` elle-même). Affiche les pins gris des `(G, sp.)` non identifiés sur le terrain + les pins verts des espèces du genre déjà capturées (ex. ★ `Tilia cordata`, `T. tomentosa`, `T. platyphyllos` en plus de tous les `Tilia sp.`). Pas les pins gris des autres espèces du genre non encore capturées — focus volontaire sur « ce que j'ai à résoudre » + « mes trophées du genre » (user:moi, 2026-05-10)

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_4_5` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)
- [→Variantes] Tranches de fréquence Arboretum (+10k, 2k-10k, 1k-2k, 100-1k, <100) avec sticky headers, onglet LISTE — décalé du cycle Photos parce que plus cohérent **après** le nettoyage catalogue d'espèces (user:moi, 2026-05-07 ; décalé 2026-05-09)
- [→Variantes] Carte filtrée par nom commun (set de `sk` fusionnés en expression `match`) — déplacée depuis Catalogue, le tag `unknownSpecies` posé par Catalogue rendra le picker propre (claude:analyse, 2026-05-08 ; déplacée 2026-05-10)
- [→Variantes] Badges « Inspecteur » (capturer N arbres `sp.`) et « Mosaïque de chênes » (10 espèces sous le même `nc`) — déplacés depuis Catalogue, dépendent du tag `unknownSpecies` et de l'agrégation par `nc` (claude:analyse, 2026-05-08 ; déplacés 2026-05-10)

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
- [creuser] Mini-quiz d'identification entre espèces partageant le même `nc` (Quercus robur vs petraea, Tilia cordata vs platyphyllos) (claude:analyse, 2026-05-08 ; refusé du cycle Catalogue 2026-05-10 — scope dédié, UX du quiz + génération de paires + scoring trop coûteux à empiler)
- [creuser] Onglet « fiches genre » parallèle aux fiches espèce. 200 genres au total, 91 ont déjà une `(G, sp.)` côté OpenData, 109 mono/bi-spécifiques à synthétiser via un `genres.json` parallèle (vs entrées zombies dans `species-index.json`). Fiche genre = liste des espèces capturées du genre + count Paris + badge progressif « Mosaïque de Quercus » (déjà au cycle Variantes). Risques : duplication info/photos avec fiche espèce, cas tordus (hybrides ×, cultivars exotiques sans parent générique grand public). Couche pédagogique entre `nc` ambigu et `nv` précis. Reconsiderer après cycle Variantes (user:moi, 2026-05-10)

## Refusé

- [refusé] Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche espèce non capturée (audit-A, 2026-05-06 ; user:moi 2026-05-09 : philosophie « découverte en marchant », la 81e espèce se trouve en tapant un pin gris à proximité, le côté quête est porté par les Remarquables ★)
- [refusé] Table `photo` 1:N + backup `schemaVersion = 2` (BACKLOG cycle Photos d'origine ; user:moi 2026-05-09 : le modèle Room actuel 1 capture = 1 photo supporte déjà N photos par arbre/espèce via N captures, pas de migration nécessaire)
- [refusé] Cold-start « 7-10 s freeze » signalé par audit (user:moi 2026-05-07 : audit faux, pas de problème de temps long bloquant au 1er lancement)
- [refusé] 4 badges saisonniers v1.1.0 proposés par audit (user:moi 2026-05-07 : caduques, on supprime les saisons)
- [refusé] Mini-transition d'ambiance switch saison (audit-D2 ; user:moi 2026-05-07 : caduque, suppression saisons)
- [refusé] Anticlimax du déblocage des 38 147 platanes (audit-tension#4 ; audit lui-même recommande de laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax)
