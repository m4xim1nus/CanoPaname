# Backlog

File d'attente brute. Tout retour, idée ou bug atterrit ici en 1 ligne au format :

```
- [TAG] description courte (origine, date)
```

**Tags** : `[ ]` à trier, `[→Codename]` rangé dans un cycle, `[creuser]` mérite réflexion avant arbitrage, `[refusé]` tranché négatif.
**Origines** : `audit`, `user:moi`, `user:F&F`, `gh#N`, `device-test`.

Triage en lot au début de chaque cycle. Process complet dans `CLAUDE.md` (*Workflow & docs*).

---

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_4_5` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)

## Cycle Progression (livré — `1.2.0`, 2026-05-12)

Résumé dans `ROADMAP.md` (*Cycles livrés post-1.0*), détail dans `CHANGELOG.md` `[1.2.0]`. Item du BACKLOG absorbé (laissé en trace barrée) :

- ~~[→Progression] Maîtrise par arrondissement : badge « Maître du Xe » (audit V2#5, 2026-05-06)~~ → livré sous forme de famille de badges binaires `familier_arr_*` (22 badges = 20 arr. + 2 bois ; renommé « Maître » → « Familier » ; carte chromatique restée en Refusé)

## À creuser

- [creuser] Quêtes hebdomadaires locales, opt-in, sans push (audit V2#3, 2026-05-06 ; déplacé depuis Endgame 2026-05-12 quand le cycle a été dissous)
- [creuser] Texte de fallback minimal pour les fiches espèces sans page Wikipedia — pertinence à revérifier après que le cycle Catalogue 1.1.0 a élargi le périmètre couvert (le manque peut s'être réduit suffisamment pour rendre ce filet caduc) (audit-B, 2026-05-06 ; reformulé + déplacé depuis Endgame 2026-05-12)
- [creuser] Résidu post-cycle Catalogue : 11 entrées résiduelles avec `nv == binôme nu` et count ≤ 2 (post-fil-rouge S10), botaniquement douteuses — `Ehretia macrophylla`, `Sophora flavescens`, `Betula occidentalis`, `Crataegus japonicum`, `Crataegus baccata`, `Celtis cerasifera`, `Carpinus carpinifolia`, `Phellodendron japonicum`, `Zanthoxylum bungei`, `Alnus formosana`, `Brucea javanica`. Peut-être réelles mais rares à Paris, peut-être saisies erronées. Demandent une recherche botanique pour trancher keep / rebinder (claude:audit-S6, 2026-05-11 ; réduit de 29→11 au S10)
- [creuser] WelcomeScreen pas lu, intro depuis la carte (user:F&F + user:moi 2026-05-07 : pas prio mais à reconsidérer post-Photos)
- [creuser] Unifier espace lexical Arboretum / Catalogue / Pokédex (audit-E2 : recommande Arboretum pour l'UI)
- [creuser] Refonte modèle remarquables : espèce-boss vs vraie quête (audit V2#2 : décision structurelle)
- [creuser] Notifications push : digest mensuel opt-in vs rien (audit-tension#1)
- [creuser] Phénologie réelle (dates floraison/feuillage par espèce) — décision structurante v2 (audit V2#1)
- [creuser] Étendre screenshots README de 3 à 6 (audit#17 : à faire après Photos pour avoir les nouveaux écrans)
- [creuser] Script `tools/scout_other_cities.py` qui interroge OpenData de villes du Grand Paris et produit un md de faisabilité (user:moi, 2026-05-07)
- [creuser] Mini-quiz ou capacité d'identification entre espèces partageant le même `nc` (Quercus robur vs petraea, Tilia cordata vs platyphyllos) (claude:analyse, 2026-05-08 ; refusé du cycle Catalogue 2026-05-10 — scope dédié, UX du quiz + génération de paires + scoring trop coûteux à empiler)
- [creuser] Aide à l'indentification des genres/espèces
- [creuser] Leaderboard optionnel et minimaliste ?
- [creuser] Animations Compose neutralisées si « échelle d'animation système = 0 » (cas device:moi) : `rememberInfiniteTransition` reste figé (pulse FAB GPS, sway du ColdStartSplash, couronne MiniArbreCrown, hero WelcomeScreen) ; `Animatable`/`animate*AsState`/`AnimatedVisibility`/`Crossfade` snap au lieu d'animer (fades splash/célébration 1re capture, tint saisonnier, décalage FAB GPS, chiffre de distance de la chasse). Aucune casse fonctionnelle. À trancher : (a) accepter la dégradation, ou (b) repasser les quelques animations qui comptent (hero onboarding, célébration capture) en `withFrameNanos` comme le radar du mode chasse. (device:moi, 2026-05-12 ; émergé du sprint S1 Progression — le radar a été corrigé ainsi)

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
