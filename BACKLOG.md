# Backlog

File d'attente brute. Tout retour, idée ou bug atterrit ici en 1 ligne au format :

```
- [TAG] description courte (origine, date)
```

**Tags** : `[ ]` à trier, `[→Codename]` rangé dans un cycle, `[creuser]` mérite réflexion avant arbitrage, `[refusé]` tranché négatif.
**Origines** : `audit`, `user:moi`, `user:F&F`, `gh#N`, `device-test`.

Triage en lot au début de chaque cycle. Process complet dans `CLAUDE.md` (*Workflow & docs*).

---

## À trier

_(vide — items absorbés par le cycle Boussole le 2026-05-14)_

## Cycle Boussole

- [→Boussole] Pivoter barre « Arrondissements complétés » + badges `familier_arr_*` sur le dénominateur remarquables de l'arr (plus toutes les espèces). « Arrondissements visités » inchangé (n'importe quel capture). (user:moi, 2026-05-14)
- [→Boussole] Recherche universelle, FAB loupe top-start → sheet 3 sections : espèces capturées (FR/latin), genres découverts, arrondissements (parseur `"1"`/`"01"`/`"1er"`/`"75001"`/`"premier"`). Tap → fly-to centroid pour arr / fiche pour espèce/genre. Hors périmètre : adresse précise, OpenData id, sheet récap arr. Centroids arr pré-calculés côté Python. (pwa:livraison-dev-externe, 2026-05-13 ; cadré 2026-05-14)
- [→Boussole] Réorg FAB Map : top-start 🔍 Recherche, top-end ⊙ Localiser, bottom-end pile [Remarquables, Arboretum, Profil] (bas-en-haut), bottom-start ★ Chasse inchangé. (pwa:livraison-dev-externe, 2026-05-13 ; cadré 2026-05-14)
- [→Boussole] Bar chart Profile : Card additionnelle sous `ProgressionCard` (pas en remplacement des 7 barres). Penchant à confirmer au démarrage : 2 graphes séparés hebdo, fenêtre 12 sem — captures totales + nouvelles découvertes d'espèces. (pwa:livraison-dev-externe, 2026-05-13 ; cadré 2026-05-14 ; à rediscuter en début de sprint)
- [→Boussole] 6 badges Pokédex binaires `pokedex_{10,20,50,100,200,500}` : avoir capturé toutes les espèces ayant `pokedexNumber ∈ [1..N]`. (user:moi, 2026-05-14)
- [→Boussole] Texte fixe « arrondissement » dans titres de chapitres Remarquables/Catalogue (1er-20ème, pas pour les Bois). (user:pote, 2026-05-14)
- [→Boussole] Compteur N/M à côté des titres de chapitres Remarquables/Catalogue (cohérence Arboretum par genre). (user:pote, 2026-05-14)
- [→Boussole] Fix tip splash citant « 930 espèces » — chiffre obsolète vs dataset actuel (784 identifiées / 934 catalogue / 204 genres / 217 264 arbres / 183 remarquables). Vérifier le périmètre exact à citer. (user:moi, 2026-05-14)
- [→Boussole] Revérifier le besoin d'un texte fallback pour fiches espèces sans Wiki post-Catalogue 1.1.0. Si ≤ 5% des espèces capturables manquent → fermer `[refusé]`. (audit-B, 2026-05-06 ; rapatrié 2026-05-14)
- [→Boussole] Étendre screenshots README de 3 à 6 (nouveau layout Map, sheet Recherche, Profile avec bar charts). (audit#17, 2026-05-06 ; rapatrié 2026-05-14)

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_4_5` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)

## À creuser

- [creuser] Quêtes hebdomadaires locales, opt-in, sans push (audit V2#3, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 quand le cycle a été dissous)
- [creuser] Résidu post-cycle Catalogue : 11 entrées résiduelles avec `nv == binôme nu` et count ≤ 2 (post-fil-rouge S10), botaniquement douteuses — `Ehretia macrophylla`, `Sophora flavescens`, `Betula occidentalis`, `Crataegus japonicum`, `Crataegus baccata`, `Celtis cerasifera`, `Carpinus carpinifolia`, `Phellodendron japonicum`, `Zanthoxylum bungei`, `Alnus formosana`, `Brucea javanica`. Peut-être réelles mais rares à Paris, peut-être saisies erronées. Demandent une recherche botanique pour trancher keep / rebinder (claude:audit-S6, 2026-05-11 ; réduit de 29→11 au S10)
- [creuser] WelcomeScreen pas lu, intro depuis la carte (user:F&F + user:moi 2026-05-07 : pas prio mais à reconsidérer post-Photos)
- [creuser] Unifier espace lexical Arboretum / Catalogue / Pokédex (audit-E2 : recommande Arboretum pour l'UI)
- [creuser] Refonte modèle remarquables : espèce-boss vs vraie quête (audit V2#2 : décision structurelle)
- [creuser] Notifications push : digest mensuel opt-in vs rien (audit-tension#1)
- [creuser] Phénologie réelle (dates floraison/feuillage par espèce) — décision structurante v2 (audit V2#1)
- [creuser] Script `tools/scout_other_cities.py` qui interroge OpenData de villes du Grand Paris et produit un md de faisabilité (user:moi, 2026-05-07)
- [creuser] Mini-quiz ou capacité d'identification entre espèces partageant le même `nc` (Quercus robur vs petraea, Tilia cordata vs platyphyllos) (claude:analyse, 2026-05-08 ; refusé du cycle Catalogue 2026-05-10 — scope dédié, UX du quiz + génération de paires + scoring trop coûteux à empiler)
- [creuser] Aide à l'indentification des genres/espèces
- [creuser] Leaderboard optionnel et minimaliste ?
- [creuser] **Écran Comparaison d'espèces** : long-press 600 ms sur une card Arboretum (cancel si déplacement > 8 px, iOS contextmenu fallback) ouvre un bottom-sheet picker des autres espèces capturées, puis route `#/compare/{skA}/{skB}` qui affiche les deux fiches en side-by-side — empilées verticalement sur téléphone, deux colonnes parallèles à scroll indépendant sur tablette. Utile pour distinguer espèces proches (Quercus robur vs petraea, Tilia cordata vs platyphyllos). Implémenté côté PWA, à reconsidérer pour Android si le besoin se confirme. (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu, garder pour cycle ultérieur)
- [creuser] **Planificateur d'itinéraire greedy nearest-neighbour** : depuis position GPS courante, génère une boucle de marche qui passe par N arbres jamais capturés, presets 1/2/3/5 km. CTA "🚶 Itinéraire" depuis fiche espèce. Carte : 3 layers MapLibre (polyligne du trajet + numbered stops + labels), bandeau supérieur progression + bouton « Quitter ». Implémenté côté PWA, gameplay « parcours » qui complète bien la chasse passive ; à arbitrer pour Android — possible chevauchement avec le mode chasse Étoile actuel. (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu, garder pour cycle ultérieur)
- [creuser] **Carte « prochaine espèce à découvrir »** sur la map : carte flottante en bas qui suggère la closest never-captured species (fallback : closest single-capture-species "variety tree" si tout est déjà découvert dans un rayon raisonnable). Refresh à chaque fix GPS. Tap → fly-to + ouvre sheet. Implémenté côté PWA. Esprit proche du mode chasse Étoile mais ciblé « espèce » plutôt que « remarquable », donc peut coexister. (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu, garder pour cycle ultérieur)
- [ ] CI : `release.yml` — échec transitoire de restauration du cache Gradle (`Failed to restore gradle-home-… : Cache service responded with 400`), sans effet sur le build. À surveiller : si ça récidive, vérifier la conf de `gradle/actions/setup-gradle` (clé de cache / quota) (ci:release.yml run v1.2.0, 2026-05-12)

## Refusé

- [refusé] Bouton partage PNG sur fiche espèce (audit-C, tension single-player vs F&F à trancher - trop loin d'un intérêt)
- [refusé] Carte chromatique vert/jaune/gris par arrondissement (audit V2#5, 2026-05-06 ; user:moi 2026-05-12 lors du cadrage Progression : la barre « X / 22 arrondissements visités » dans le Profil + le badge « Maître du Xe » suffisent pour mettre sur la piste, l'overlay chromatique sur la carte serait redondant et chargerait inutilement la vue principale)
- [refusé] Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche espèce non capturée (audit-A, 2026-05-06 ; user:moi 2026-05-09 : philosophie « découverte en marchant », la 81e espèce se trouve en tapant un pin gris à proximité, le côté quête est porté par les Remarquables ★)
- [refusé] Table `photo` 1:N + backup `schemaVersion = 2` (BACKLOG cycle Photos d'origine ; user:moi 2026-05-09 : le modèle Room actuel 1 capture = 1 photo supporte déjà N photos par arbre/espèce via N captures, pas de migration nécessaire)
- [refusé] Cold-start « 7-10 s freeze » signalé par audit (user:moi 2026-05-07 : audit faux, pas de problème de temps long bloquant au 1er lancement)
- [refusé] 4 badges saisonniers v1.1.0 proposés par audit (user:moi 2026-05-07 : caduques, on supprime les saisons)
- [refusé] Mini-transition d'ambiance switch saison (audit-D2 ; user:moi 2026-05-07 : caduque, suppression saisons)
- [refusé] Anticlimax du déblocage des 38 147 platanes (audit-tension#4 ; audit lui-même recommande de laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax)
- [refusé] Pré-affichage fiche remarquable enrichie même non capturée, bandeau « Pas encore découvert » (audit-B, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 — c'est une chasse avec assez d'infos avec la distance)
