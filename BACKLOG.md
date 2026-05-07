# Backlog

File d'attente brute. Tout retour, idée ou bug atterrit ici en 1 ligne au format :

```
- [TAG] description courte (origine, date)
```

**Tags** : `[ ]` à trier, `[→Codename]` rangé dans un cycle, `[creuser]` mérite réflexion avant arbitrage, `[refusé]` tranché négatif.
**Origines** : `audit`, `user:moi`, `user:F&F`, `gh#N`, `device-test`.

Triage en lot au début de chaque cycle. Process complet dans `CLAUDE.md` (*Workflow & docs*).

---

## Cycle Vérité & Friction

- [→Vérité] Retirer UI saisons (SeasonSelector caché, ArchiveBanner retiré, Profil/Arboretum/Remarquables global-only) ; garder schéma `Season` + colonne `season` (user:moi, 2026-05-07)
- [→Vérité] Retirer 3 badges saisonniers du catalogue (cohérence suppression UI saisons) (user:moi, 2026-05-07)
- [→Vérité] README : « 907 espèces » → « 907 dont 528 fiches enrichies » (audit#1, 2026-05-06)
- [→Vérité] README : retirer « saisonnalité réelle » (audit#2, 2026-05-06)
- [→Vérité] PRIVACY + README : mention OpenStreetMap / OpenFreeMap (audit#3, 2026-05-06)
- [→Vérité] CHANGELOG [1.0.0] : « fiches remarquables accessibles après capture » (audit#4, 2026-05-06)
- [→Vérité] `UnknownContent` rappelle la mécanique de déverrouillage par espèce (audit#5, 2026-05-06)
- [→Vérité] `CaptureAvailability.TooFar` affiche la distance courante vs max 30 m (audit#6, 2026-05-06)
- [→Vérité] Bullet remarquables Welcome étoffé (audit#7, 2026-05-06)
- [→Vérité] Feedback GPS post-permission : snackbar + pulse FAB pendant le gap 7-10 s (audit#8, 2026-05-06)
- [→Vérité] BroadcastReceiver `PROVIDERS_CHANGED_ACTION` pour réagir si la loc système est activée après ouverture app (user:moi, 2026-05-07)
- [→Vérité] Bug Profil : « aujourd'hui » affiché pour une 1re capture d'hier (user:moi, 2026-05-07)
- [→Vérité] Compteur global Profil : « X / 907 espèces (Y %) » + « Z / 213 042 arbres (W %) » (audit#16 + user:moi, 2026-05-07)
- [→Vérité] Badges débloqués sur ProfileScreen : preview rangée 3-4 derniers (user:moi, 2026-05-07)
- [→Vérité] Cluster contenant ★ : ring orange fin via `has_remarquable_count` (user:moi, 2026-05-07)
- [→Vérité] Haptique `LongPress` à l'ouverture du sheet `ArbreDetailContent` (audit#12, 2026-05-06)
- [→Vérité] Haptique capture déplacée du post-INSERT vers le tap « Capturer » (audit#13, 2026-05-06)
- [→Vérité] Snackbar + Tick haptique à l'annulation caméra (audit#14, 2026-05-06)
- [→Vérité] Label + timeout 60 s sur progress bar export/import (audit#15, 2026-05-06)
- [→Vérité] FAB ★ : icône `Search` → `Star` (audit#10, 2026-05-06)
- [→Vérité] Snackbar distance remarquable 3 s → 5 s (audit#11, 2026-05-06)
- [→Vérité] `EmptyState` `bodyMedium` 14 sp → 16 sp (audit#9, 2026-05-06)

## Cycle Photos

- [→Photos] Photos multiples par espèce et par arbre individuel (user:moi, 2026-05-07)
- [→Photos] Photo visible dans le modal détail espèce + remarquable (user:moi, 2026-05-07)
- [→Photos] Suppression d'une photo possible tant qu'il en reste ≥ 1 sur l'espèce (user:moi, 2026-05-07)
- [→Photos] Backup `schemaVersion = 2` rétro-compatible lecture v1 (user:moi, 2026-05-07)
- [→Photos] Tranches de fréquence Arboretum (+10k, 2k-10k, 1k-2k, 100-1k, <100) avec sticky headers (user:moi, 2026-05-07)
- [→Photos] Liste « Espèces manquantes » + bouton « Trouver le plus proche » sur fiche espèce non capturée (audit-A, 2026-05-06)
- [→Photos] Restructuration badges progressifs : barres + paliers visibles (1, 10, 25, 50, 100, 250…) (audit-B + user:moi, 2026-05-07)
- [→Photos] Depuis fiche remarquable, bouton « Voir sur la carte » (recentre + zoom + pulse 2 s) (user:moi, 2026-05-07)

## Cycle Variantes

- [→Variantes] Refonte Arboretum « états » : la colonne `season` devient `variants` (en fleur, tout nu, fruits, bébé, géant) (user:moi, 2026-05-07)
- [→Variantes] Détection auto bébé/géant via circonférence ; déclaration utilisateur sinon (user:moi, 2026-05-07)
- [→Variantes] Re-capture du même arbre dans un état nouveau = upgrade visible élément Arboretum (user:moi + audit V2#4, 2026-05-07)
- [→Variantes] `MIGRATION_3_4` + backup `schemaVersion = 3` (user:moi, 2026-05-07)
- [→Variantes] Badges variantes émergent du nouveau modèle (user:moi, 2026-05-07)

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

## Refusé

- [refusé] Cold-start « 7-10 s freeze » signalé par audit (user:moi 2026-05-07 : audit faux, pas de problème de temps long bloquant au 1er lancement)
- [refusé] 4 badges saisonniers v1.1.0 proposés par audit (user:moi 2026-05-07 : caduques, on supprime les saisons)
- [refusé] Mini-transition d'ambiance switch saison (audit-D2 ; user:moi 2026-05-07 : caduque, suppression saisons)
- [refusé] Anticlimax du déblocage des 38 147 platanes (audit-tension#4 ; audit lui-même recommande de laisser tel quel — l'effet « wow » au J+3 vaut son anticlimax)
