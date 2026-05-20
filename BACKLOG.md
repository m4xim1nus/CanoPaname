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

_(vide — les 5 inputs du 2026-05-20 triés et calés le 2026-05-21)_

## Cycle Netteté

Polish de la boucle carte + capture. Pressenti en premier des trois cycles à venir.

- [→Netteté] Cône de vision sur le pin Location : afficher un secteur orienté selon la boussole du téléphone (capteur d'orientation), façon Google Maps — indique vers où l'utilisateur regarde (user:moi, 2026-05-20)
- [→Netteté] Filtres rapides depuis la sheet d'un arbre non remarquable : boutons « toute l'espèce » / « tout le genre » qui ne gardent sur la carte que les pins verts/orange concernés, avec défiltrage en un clic ; viser un re-rendu sans gros rechargement de la source GeoJSON (user:moi, 2026-05-20)
- [→Netteté] Transition de capture sans flash carte : entre la validation de la photo et l'ouverture de la fiche espèce, la carte réapparaît brièvement — couvrir la bascule par un overlay pour ne jamais repasser visuellement par la carte (user:moi, 2026-05-20)
- [→Netteté] WelcomeScreen pas lu, intro depuis la carte — fold pressenti, inclusion dans le cycle encore à discuter/creuser (user:F&F + user:moi 2026-05-07 ; fold proposé 2026-05-21)

## Cycle Herbier

Enrichissement des fiches espèces (saisonnalité, attributs, picto, photos de référence). Pressenti après Netteté et avant Variantes — dépendance assumée : le calendrier de floraison alimentera la suggestion d'état « en fleur » de Variantes.

- [→Herbier] Upgrade Arboretum (saisonnalité, picto, photos ref) : exploiter le parsing PDF des fiches-essences Ville de Paris (200 espèces — calendriers floraison/fructification + atouts/limites + photos officielles) ; cascade Wikidata→iNat (photos) + Wikidata→POWO→Wikipedia FR (attributs) pour les 584 hors-fiches ; trou résiduel = saisonnalité fine sur la longue traîne. Détails et chiffres dans `PROSPECTION_ARBORETUM.md`. (user:moi, 2026-05-17)
- [→Herbier] Texte fallback pour fiches espèces sans Wikipedia. Audit S7 (2026-05-16) : **254/784 espèces identifiées (32 %) sans `summary`**, mais ne couvrent que **7 778/204 364 arbres (3.8 %)** — quasi exclusivement des hybrides/cultivars/sous-espèces (Tilia x europaea, Pinus nigra subsp. nigra, Gleditsia triacanthos f. Inermis, x Cupressocyparis leylandii…). Fallback gratuit dispo via `genre-info.json.summary` (texte Wikipedia du genre, déjà cuit). Lecture pondérée par arbres = sous le seuil ≤ 5 %, lecture par espèce = bien au-dessus — à trancher dans un cycle futur si la friction utilisateur devient sensible. (audit-B 2026-05-06 ; ré-audité 2026-05-16 hors-cycle Boussole ; fold Herbier 2026-05-21)
- [→Herbier] Résidu post-cycle Catalogue : 11 entrées résiduelles avec `nv == binôme nu` et count ≤ 2 (post-fil-rouge S10), botaniquement douteuses — `Ehretia macrophylla`, `Sophora flavescens`, `Betula occidentalis`, `Crataegus japonicum`, `Crataegus baccata`, `Celtis cerasifera`, `Carpinus carpinifolia`, `Phellodendron japonicum`, `Zanthoxylum bungei`, `Alnus formosana`, `Brucea javanica`. Peut-être réelles mais rares à Paris, peut-être saisies erronées. Demandent une recherche botanique pour trancher keep / rebinder (claude:audit-S6, 2026-05-11 ; réduit de 29→11 au S10 ; fold Herbier 2026-05-21)

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_4_5` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)

## À creuser

### Aide à l'identification

_Aider à reconnaître / distinguer les espèces. Même logique, à arbitrer ensemble._

- [creuser] **Écran Comparaison d'espèces** : long-press 600 ms sur une card Arboretum (cancel si déplacement > 8 px, iOS contextmenu fallback) ouvre un bottom-sheet picker des autres espèces capturées, puis route `#/compare/{skA}/{skB}` qui affiche les deux fiches en side-by-side — empilées verticalement sur téléphone, deux colonnes parallèles à scroll indépendant sur tablette. Utile pour distinguer espèces proches (Quercus robur vs petraea, Tilia cordata vs platyphyllos). Implémenté côté PWA, à reconsidérer pour Android si le besoin se confirme. (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu, garder pour cycle ultérieur ; sorti de la proposition Herbier 2026-05-21)
- [creuser] Mini-quiz ou capacité d'identification entre espèces partageant le même `nc` (Quercus robur vs petraea, Tilia cordata vs platyphyllos) (claude:analyse, 2026-05-08 ; refusé du cycle Catalogue 2026-05-10 — scope dédié, UX du quiz + génération de paires + scoring trop coûteux à empiler)
- [creuser] Aide à l'identification des genres/espèces — item parapluie de la sous-famille ; pourrait devenir creux si « Comparaison d'espèces » est livrée

### Engagement & rétention

_Mécaniques de ré-engagement, en tension avec le ton « compagnon de balade calme »._

- [creuser] Notifications push : digest mensuel opt-in vs rien (audit-tension#1)
- [creuser] Quêtes hebdomadaires locales, opt-in, sans push (audit V2#3, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 quand le cycle a été dissous)
- [creuser] Leaderboard optionnel et minimaliste ? (en tension avec l'invariant CLAUDE.md « pas de classement » ; gardé en creuser sur choix user:moi 2026-05-20)

### Elargissement de l'app

_Plus d'arbres, plus de fun_

- [creuser] Combler les vides du dataset sur les 4 jardins gérés hors Ville de Paris (Luxembourg/Sénat ~2 980 réels vs 243 en base, Plantes/MNHN ~2 000 vs 79, Tuileries/CMN, Villette/EPPGHV) — `les-arbres` ne couvre que la voirie municipale. MNHN et Sénat n'ont pas de dump open data exploitable ; OSM (`natural=tree` + `species=`) est la seule piste automatisable mais **verdict non rendu** (recherche incomplète : 4 requêtes Overpass à exécuter hors sandbox, critère = ratio `species=` ≥ ~70 %). Détails, arbitrages déjà pris (namespace `idbase`, qualité minimale lat/lon/genre/espèce) et requêtes dans `docs/audit-arbres-jardins-hors-ville.md`. (user:moi, 2026-05-20)
- [creuser] Script `tools/scout_other_cities.py` qui interroge OpenData de villes du Grand Paris et produit un md de faisabilité (user:moi, 2026-05-07)

## Refusé

- [refusé] Bouton partage PNG sur fiche espèce (audit-C, tension single-player vs F&F à trancher - trop loin d'un intérêt)
- [refusé] Carte chromatique vert/jaune/gris par arrondissement (audit V2#5, 2026-05-06 ; user:moi 2026-05-12 lors du cadrage Progression : la barre « X / 22 arrondissements visités » dans le Profil + le badge « Maître du Xe » suffisent pour mettre sur la piste, l'overlay chromatique sur la carte serait redondant et chargerait inutilement la vue principale)
- [refusé] Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche espèce non capturée (audit-A, 2026-05-06 ; user:moi 2026-05-09 : philosophie « découverte en marchant », la 81e espèce se trouve en tapant un pin gris à proximité, le côté quête est porté par les Remarquables ★)
- [refusé] Planificateur d'itinéraire / boucle de marche greedy nearest-neighbour passant par N arbres jamais capturés, presets 1/2/3/5 km, CTA depuis fiche espèce (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu ; user:moi 2026-05-21 : refusé — contredit « découverte en marchant », même motif que le refus « Espèces manquantes » en plus directif, et chevauche le mode chasse Étoile)
- [refusé] Carte flottante « prochaine espèce à découvrir » suggérant la closest never-captured species, refresh à chaque fix GPS, tap → fly-to + ouvre sheet (pwa:livraison-dev-externe, 2026-05-13 ; passé en revue pour Boussole 2026-05-14 — non retenu ; user:moi 2026-05-21 : refusé — « va ici ensuite » permanent, même motif que le refus « Espèces manquantes »)
- [refusé] Refonte modèle remarquables : espèce-boss vs vraie quête (audit V2#2 ; user:moi 2026-05-21 : outdated — la question structurelle a été de facto tranchée vers « vraie quête / individu » par les cycles Progression et Boussole, fiche-remarquable individuelle déverrouillée à la capture, comptage par arrondissement, mode chasse Étoile)
- [refusé] Table `photo` 1:N + backup `schemaVersion = 2` (BACKLOG cycle Photos d'origine ; user:moi 2026-05-09 : le modèle Room actuel 1 capture = 1 photo supporte déjà N photos par arbre/espèce via N captures, pas de migration nécessaire)
- [refusé] Cold-start « 7-10 s freeze » signalé par audit (user:moi 2026-05-07 : audit faux, pas de problème de temps long bloquant au 1er lancement)
- [refusé] 4 badges saisonniers v1.1.0 proposés par audit (user:moi 2026-05-07 : caduques, on supprime les saisons)
- [refusé] Mini-transition d'ambiance switch saison (audit-D2 ; user:moi 2026-05-07 : caduque, suppression saisons)
- [refusé] Anticlimax du déblocage des 38 147 platanes (audit-tension#4 ; audit lui-même recommande de laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax)
- [refusé] Pré-affichage fiche remarquable enrichie même non capturée, bandeau « Pas encore découvert » (audit-B, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 — c'est une chasse avec assez d'infos avec la distance)
