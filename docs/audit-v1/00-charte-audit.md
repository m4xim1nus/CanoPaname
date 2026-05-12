# Charte de l'audit produit & UX — CanoPaname v1.0

> Document de brief commun distribué aux 6 agents experts. Rédigé en amont par le porteur du produit (et son assistant). **Lis-la entièrement avant de produire ton rapport.**

---

## 1. Le produit en 3 phrases

CanoPaname est une **app Android single-player, family & friends**, qui transforme les 213 042 arbres parisiens géolocalisés (dont 169 « remarquables ») en un Pokédex botanique à collectionner. La capture se fait par proximité GPS (< 30 m) + photo prise depuis l'app, dans une logique de promenade lente plutôt que de jeu d'apparition AR. Tout est local : pas de Google Play Services, pas de cloud, pas de compte, pas de classement. Le différenciateur revendiqué dans le README est **la saisonnalité réelle** (un platane en mai ≠ en novembre).

## 2. Statut au moment de l'audit

- **v1.0.0** taguée et publiée le 2026-05-05 (Obtainium + GitHub Releases).
- 53 phases livrées, dernière phase `13C` (passage public).
- Audit pré-public déjà conduit sur l'axe `privacy / sécu / légal / release pipeline` (`docs/audit-pre-public.md`). **Ne pas rouvrir ces sujets**.
- Pas encore de retours users — l'audit doit produire un point de départ avant que le signal terrain n'arrive.

## 3. Vision et contraintes dures (non négociables sauf pivot v2)

- **Single-player, family & friends.** Pas de leaderboard, pas de chat, pas de raid, pas d'échange joueur. Ne pas proposer ces features sans les rattacher explicitement à un pivot v2.
- **Pas de Google Play Services.** GrapheneOS first. Geoloc native uniquement.
- **Pas de backend, pas d'auth, pas de cloud.** Le backup est un ZIP local via Storage Access Framework.
- **Stockage local SQLite (Room) seulement.**
- **Données embarquées au build** (pas de download runtime du dataset, ni des fiches Wikipedia).
- **Pas de feature flags, pas d'A/B.** App perso.
- **Simplicité > scale.** À chaque arbitrage, on choisit la version simple. Pas de framework DI, pas d'abstraction prématurée.
- **Saison.ordinal et speciesIndex sont figés** : modifier ces enums/index casse les captures existantes. Toute reco qui touche à ces structures doit le dire explicitement et atterrir en `v2.0`.

## 4. Les 6 tensions de départ déjà identifiées

Tu n'as **pas** à les redécouvrir. Tu peux soit les confirmer, soit les nuancer, soit les balayer si elles ne tombent pas dans ton angle. L'objectif est qu'au moins l'angle dont c'est le périmètre tranche dessus.

1. **« 907 espèces » vs navigation limitée.** Le README promet 907 espèces ; seuls **528 ont une fiche Wikipedia** embarquée (les autres affichent une fiche vide). Pas de barre de recherche dans l'app pour naviguer ces 907 entrées sans parcourir la carte ou l'Arboretum.
2. **« Saisonnalité réelle » vs 4 buckets fixes.** Le README la vend comme différenciateur clé. Réalité : 4 saisons calendaires figées (`WINTER/SPRING/SUMMER/AUTUMN`). Pas de phénologie réelle (dates de floraison/feuillage variables selon espèce et année). La saisonnalité actuelle est un **bucket d'archive**, pas une simulation biologique.
3. **« Pas de classement, pas de social »** vs 15 badges seuillés (10/50/100 captures, etc.) qui exposent la progression. C'est un **mini-leaderboard contre soi-même** — la pression existe, juste pas comparée à autrui.
4. **« 100 % local, jamais envoyé »** vs tuiles OpenFreeMap téléchargées au runtime. Le GPS n'est jamais envoyé (vrai), mais l'app fait du trafic réseau sortant non mentionné dans le README ni `PRIVACY.md`.
5. **Fiches remarquables enrichies** mais le lien fiche PDF Ville de Paris est conditionné à la capture préalable (sans capture, on ne voit rien des infos riches).
6. **Pas de feedback haptique ailleurs qu'à la capture (?)**. À vérifier au passage si ton angle le couvre.

## 5. Ton angle — où ton rapport tranche

Chaque agent reçoit en plus un **brief spécifique d'angle** (persona, lentille, périmètre couvert, périmètre exclu). Respecte les frontières d'angle pour éviter les doublons inutiles avec les autres rapports. Si tu touches un sujet hors-périmètre, dis-le en une phrase (« hors-périmètre, voir angle X ») et passe.

## 6. Calibrage tonal — asséré, pas neutre

> **Tu n'es pas là pour faire plaisir au porteur du produit. Tu es là pour produire un avis défendu. La neutralité est un échec.**

Référence absolue de calibrage : `docs/vision-jeu.md`. Lis-le en entier avant de commencer. C'est le ton attendu : opinionné, structuré en arbitrages explicites, sans graisse, avec des phrases du genre « Rejeté X. Justification : Y. Choix : Z ».

Bons réflexes :
- Trancher (« je pense que », « je préconise », « à mon sens c'est faux »).
- Citer des chemins `file:line` quand tu fais une affirmation sur l'app.
- Dire le coût d'une reco quand il est non-trivial.
- Distinguer ce qui est *gravement cassé*, *moyennement gênant*, et *frustration tolérable*.

Mauvais réflexes (= reformuler ou couper) :
- « Il pourrait être intéressant d'envisager… ».
- Listes à puces neutres sans hiérarchie.
- « D'un côté X, de l'autre Y » sans verdict en sortie.
- Recos génériques sans nom de fichier ni d'écran.

## 7. Tiering des recos (obligatoire)

Chaque reco doit atterrir dans **exactement un** des 3 tiers :

- **`v1.0.1`** — patch sans nouvelle feature. Reformulation README/copy, fix d'un empty state, fix d'un libellé ambigu, fix haptique manquant, fix accessibilité. Effort par item ≤ ½ jour. **Pas de string en quantité, pas de schéma touché**.
- **`v1.1.0`** — feature increment additif sans casser le schéma backup ni de Room migration destructive. Barre de recherche, nouveau type de badge, paramètres, raffinement saisonnier additif, partage borné.
- **`v2.0`** — pivot structurel. Touche au pitch, à la vision, au schéma de données, au modèle de jeu. Phénologie réelle, multi-device, refonte progression, social bordé. **Chaque reco v2.0 doit défendre 4 lignes** : tension / décision / coût / bénéfice.

**Si tu hésites entre deux tiers, c'est que la reco est mal cadrée.** Soit tu la retravailles jusqu'à ce qu'elle se range, soit tu la coupes.

## 8. Format imposé du rapport (à respecter strictement)

```markdown
# [Titre de l'angle] — CanoPaname v1.0

## Verdict
[3-5 lignes maximum. Tranché. Aucune neutralité. Si tu n'arrives pas à tenir en 5 lignes, c'est que tu n'as pas fini de réfléchir.]

## [4-7 sections spécifiques à ton angle]
[Le cœur du rapport. Chaque section nomme un sujet précis et tranche dessus.]

## Pistes considérées et écartées
[Les idées que tu as regardées puis exclues. Une ligne par idée + une ligne de raison. Vit ici, pas dans la synthèse finale.]

## Recos par tier
| Tier | Titre | 1 phrase |
|---|---|---|
| v1.0.1 | … | … |
| v1.0.1 | … | … |
| v1.1.0 | … | … |
| v2.0 | … | … |
```

Longueur cible du rapport d'angle : **3-6 pages MD rendues**. Au-delà, c'est qu'il y a de la graisse à couper. En-deçà, c'est qu'il n'y a pas eu de creusage.

## 9. Critères de coupe (pour ton propre tri)

Avant de mettre une reco dans ta table finale, vérifie :

1. **Spécificité.** La reco nomme un écran, un parcours, un fichier, ou une copie. *« Améliorer l'onboarding »* saute. *« Réécrire les 4 bullets du WelcomeScreen pour aligner sur la terminologie Catalogue »* passe.
2. **Tier assignable sans hésiter.** Si tu hésites, retravaille ou coupe.
3. **Verdict défendu.** Chaque reco a un *« pourquoi maintenant »* en 1 phrase. *« Plus joli »* ne suffit pas.

Le rédacteur de la synthèse `99-rapport-unifie.md` ne lira QUE ta table de recos par tier (et ton verdict). Ce qui n'y est pas n'existera pas dans la synthèse finale. Donc soigne la table.

## 10. Périmètres exclus (pour éviter les doublons)

| Sujet | Couvert par | Tu fais quoi |
|---|---|---|
| Privacy / sécurité / légal / release pipeline | `docs/audit-pre-public.md` | Ignorer. |
| Build size, ProGuard, perf tech invisible | Aucun (hors périmètre v1) | Ignorer. |
| Boucle de jeu **dans** une session | Angle 02 (Game design) | Si tu n'es pas 02, mention courte et passe. |
| Boucle **entre** sessions, rétention long terme | Angle 04 (Rétention) | Si tu n'es pas 04, mention courte et passe. |
| Cohérence du pitch / README → réalité | Angle 03 (Cohérence) | Si tu n'es pas 03, mention courte et passe. |
| Cohérence visuelle / iconographique / typographique | Angle 06 (Visuel) | Si tu n'es pas 06, mention courte et passe. |
| Empty states, accessibilité, parcours micro | Angle 01 (UX) | Si tu n'es pas 01, mention courte et passe. |
| Perf perçue, motion, haptique | Angle 05 (Friction sensorielle) | Si tu n'es pas 05, mention courte et passe. |

## 11. Sources de vérité

Lis et utilise au besoin :
- `README.md` — la promesse marketing.
- `ROADMAP.md` — l'historique des phases livrées et non-livrées.
- `CHANGELOG.md` — la note de release v1.0.0.
- `CLAUDE.md` — la vérité technique et les contraintes architecturales.
- `docs/vision-jeu.md` — la matière philosophique de référence (et le calibrage tonal).
- `docs/audit-pre-public.md` — la rigueur méthodologique de référence (pas son contenu).
- Le code dans `app/src/main/java/app/arbre/` — la source de vérité finale.

Tu as droit à `Read`, `Bash` (read-only), `Grep`, `Glob`. Tu n'as pas le droit d'éditer le code ni les .md autres que ton propre rapport.

## 12. Rappel final

Cet audit alimente la planification post-v1. Le user a déjà identifié *« vision jeu »* comme prochaine étape importante de sa séquence. Ton rapport doit aider à trancher : **où mettre le prochain effort** ? Une reco qui n'aide pas à cette décision est une reco qui peut sauter.

Bon courage. Va loin.
