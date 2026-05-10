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
- [→Catalogue] (S5 ✓) Fiche `(G, sp.)` enrichie : sous la galerie photos `sp.`, mini-catalogue filtré sur le genre — mêmes cards que l'Arboretum complet (`nv`, `#N`, count, photo 1re capture pour les capturées, silhouette « ??? » + binôme latin grisé pour les non-capturées). Lien vers la fiche-espèce sur tap. Donne au joueur une vue immédiate « j'ai 3/55 chênes » sans quitter la fiche genre (user:moi, 2026-05-10)
- [→Catalogue] (S5 ✓) Carte filtrée depuis la fiche `(G, sp.)` : extension de `MAP_FILTERED` à un set de `sk` (tous les `sk` du genre déjà capturés + le `sk` de l'entrée `(G, sp.)` elle-même). Affiche les pins gris des `(G, sp.)` non identifiés sur le terrain + les pins verts des espèces du genre déjà capturées (ex. ★ `Tilia cordata`, `T. tomentosa`, `T. platyphyllos` en plus de tous les `Tilia sp.`). Pas les pins gris des autres espèces du genre non encore capturées — focus volontaire sur « ce que j'ai à résoudre » + « mes trophées du genre » (user:moi, 2026-05-10)
- [→Catalogue] (S6) Fix invariant verify `Non spécifié` mal calibré du S3 : seuil 50 sur compteur CSV brut → invariant zombie-aware (count > 0 sur entrée `Non spécifié` → raise), garde l'esprit « le drop S1 régresse ». Fix local 2026-05-10, à committer en S6 (claude:audit local, 2026-05-10)
- [→Catalogue] (S6) Cascade `nv` améliorée — filtre `frTitle != binôme` dans `compute_vernacular_and_pokedex` : sur les 316 espèces où l'article Wikipedia FR est titré scientifiquement (`Aesculus_hippocastanum`, `Pinus_nigra`, etc.), passer au fallback `construit` qui utilise `nc` OpenData propre. Récupère ~50% des cas. ~5 lignes Python (user:moi + claude:audit, 2026-05-10)
- [→Catalogue] (S6) Cascade `nv` améliorée — Wikipedia redirects API : pour chaque article titré binôme post-filtre, requêter `?action=query&prop=redirects&titles=X` côté `fr.wikipedia.org`, filtrer les redirects pour ne garder que les noms vernaculaires français (heuristique : pas de subsp./var./x, première lettre majuscule, longueur ≤ 35 chars), choisir le plus court. Cache permanent `tools/.wikipedia-aliases-cache/{sk}.json`, throttled à 3 req/s. Récupère ~80% des cas restants type « Marronnier d'Inde », « Pin noir », « Copalme d'Orient » (user:moi, 2026-05-10)
- [→Catalogue] (S6) Cascade `nv` améliorée — extension manuelle `VERNACULAR_OVERRIDES` top 50 espèces : table de paires `(genre, espece) → nom commun` curée à la main pour les espèces les plus capturables à Paris. Source de vérité prime sur tout. Garantit la qualité éditoriale sur le top. Inclut au minimum : Aesculus hippocastanum, Styphnolobium japonicum, Corylus colurna, Pinus nigra, Sabal minor, Parrotia persica, Liquidambar orientalis, Olea europaea (user:moi, 2026-05-10)
- [→Catalogue] (S6) Strip `(espèce indéterminée)` du `nv` zombie + mapping `GENRE_FR` : refonte `construct_vernacular` pour `is_unknown=True` — utiliser `GENRE_FR[genre]` (table module-level ~50-100 entrées : Quercus→Chêne, Tilia→Tilleul, Pinus→Pin, Acer→Érable...) → fallback genre latin nu. Plus de parenthèse parasite en UI. Cas zombies `Non spécifié` (sk 187/488/823) : exception `"Indéterminé"` car le genre lui-même est inconnu. Vérification d'unicité préservée (claude:audit + user:moi, 2026-05-10)
- [→Catalogue] (S7) Toggle tri UI Arboretum 3 modes : `SegmentedButton` Découverte (Pokédex stable, défaut) / Fréquence (count Paris décroissant — la motivation joueur « j'ai chopé 1, 2, 4, 7 sur les 50 plus fréquentes ») / Genres (groupé alphabétique avec sous-chapitres `GridItemSpan(maxLineSpan)`, count cumulé). Backend `catalogueOrder` inchangé, transformations à l'affichage seulement. Mode Genres prépare techniquement les fiches genre du S8 (tap sur en-tête de chapitre = ouvrir la fiche genre) (user:moi, 2026-05-10)
- [→Catalogue] (S7) Helpers `SpeciesIndex` pour fiches genre : `entriesOfGenre(g)` (déjà présent), `genreCount(g)`, `genres(): List<String>` triés alpha, `capturedCountInGenre(g, capturedSks)`. Préparation data sans toucher l'UI fiche genre elle-même. Tests JVM dans `SpeciesIndexTest` (user:moi, 2026-05-10)
- [→Catalogue] (S8) Fiches genre dédiées : modèle léger `GenreEntry` dérivé du species-index (pas de JSON séparé — données déjà disponibles via `SpeciesIndex.entriesOfGenre`), écran `GenreDetailScreen`, route `Routes.GENRE(genre)`. Contenu : liste des espèces capturables du genre (= toutes celles non-zombies du genre), count cumulé Paris, mini-galerie photos de mes captures du genre, mini-carte filtrée (extension de `MAP_FILTERED` au set du genre). Couvre les 198 genres avec espèces identifiées et les 3 genres only-unknown (`Genista`, `Vitex`, `Ziziphus`). Tap d'entrée : en-tête de chapitre mode Genres + titre de fiche `(G, sp.)`. Promu depuis À creuser le 2026-05-10 suite audit (user:moi, 2026-05-10)
- [→Catalogue] (S8) Badge « Mosaïque de Quercus » progressif (capturer N espèces du genre Quercus, paliers 3/5/10) : émerge naturellement avec les fiches genre. Transféré depuis cycle Variantes. Décision conditionnelle : intégrer au S8 si la complexité reste raisonnable, sinon différer (user:moi, 2026-05-10)
- [→Catalogue] (S9) Clôture cycle : balade GrapheneOS pour smoke test (nv propres, 3 modes de tri, fiches genre), `./gradlew test lint assembleDebug` vert, entrée `CHANGELOG [1.1.0]` Keep-a-Changelog, rotation du cycle dans ROADMAP (compression en « Cycles livrés post-1.0 »), tag git `v1.1.0` + push (user:moi, 2026-05-11)

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_4_5` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)
- [→Variantes] Tranches de fréquence Arboretum (+10k, 2k-10k, 1k-2k, 100-1k, <100) avec sticky headers, onglet LISTE — décalé du cycle Photos parce que plus cohérent **après** le nettoyage catalogue d'espèces (user:moi, 2026-05-07 ; décalé 2026-05-09)
- [→Variantes] Carte filtrée par nom commun (set de `sk` fusionnés en expression `match`) — déplacée depuis Catalogue, le tag `unknownSpecies` posé par Catalogue rendra le picker propre (claude:analyse, 2026-05-08 ; déplacée 2026-05-10)
- [→Variantes] Badge « Inspecteur » (capturer N arbres `sp.`) — dépend du tag `unknownSpecies` posé par Catalogue. Le badge « Mosaïque de chênes » est transféré au S7 du cycle Catalogue (claude:analyse, 2026-05-08 ; déplacé 2026-05-10 ; Mosaïque transférée à Catalogue 2026-05-11)

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

## Refusé

- [refusé] Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche espèce non capturée (audit-A, 2026-05-06 ; user:moi 2026-05-09 : philosophie « découverte en marchant », la 81e espèce se trouve en tapant un pin gris à proximité, le côté quête est porté par les Remarquables ★)
- [refusé] Table `photo` 1:N + backup `schemaVersion = 2` (BACKLOG cycle Photos d'origine ; user:moi 2026-05-09 : le modèle Room actuel 1 capture = 1 photo supporte déjà N photos par arbre/espèce via N captures, pas de migration nécessaire)
- [refusé] Cold-start « 7-10 s freeze » signalé par audit (user:moi 2026-05-07 : audit faux, pas de problème de temps long bloquant au 1er lancement)
- [refusé] 4 badges saisonniers v1.1.0 proposés par audit (user:moi 2026-05-07 : caduques, on supprime les saisons)
- [refusé] Mini-transition d'ambiance switch saison (audit-D2 ; user:moi 2026-05-07 : caduque, suppression saisons)
- [refusé] Anticlimax du déblocage des 38 147 platanes (audit-tension#4 ; audit lui-même recommande de laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax)
