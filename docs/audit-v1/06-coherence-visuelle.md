# Cohérence visuelle, iconographique, typographique — CanoPaname v1.0

## Verdict

CanoPaname tient debout comme **un seul objet visuel** d'un écran à l'autre : palette arboricole maîtrisée (vert feuille / or / écorce / orange remarquable), Fraunces SemiBold dosée juste sur les niveaux M3 d'affichage (display/headline/titleLarge), Material Outlined uniforme partout, dark mode propre. Pas de couleur orpheline, pas d'icône Material par défaut traînant. Le seul angle faible : l'**identité « CanoPaname » est exclusivement textuelle** — wordmark splash + About uniquement, jamais portée visuellement en session. L'app respire, elle ne crie pas son nom. Choix défendable en perso, à reconsidérer pour la mémorabilité publique.

---

## 1. Palette — équilibre botanique validé

`ArbresColors.kt` + `Color.kt` + `Theme.kt`.

5 tokens centraux avec variantes light/dark :

- **Vert feuille** (`#81C784` clair / `#2E7D32` sombre) → primary.
- **Or** (`#C9A227`) → accent discret, surface remarquable.
- **Écorce** (`#6D4C41` clair / `#BCAAAA` dark) → secondary cohésive avec le vert.
- **Orange remarquable** (`#FB8C00`) → signal perceptif aligné pin carte / badge fiche / icône remarquable. Pas d'ambiguïté.
- **Tinting saisonnier** sur `surface` (printemps pâle, automne doré, hiver bleu froid) — perceptible en light, imperceptible en dark (choix assumé).

**Verdict** : saturée juste comme il faut, ni guimauve ni cru. Le tinting saisonnier en light passe le test du « scan lent » ; en dark il est invisible mais n'agace personne.

---

## 2. Typographie — Fraunces + M3 hiérarchisée

`Type.kt`.

- **Fraunces SemiBold** uniquement sur : `displayLarge/Medium/Small`, `headlineLarge/Medium/Small`, `titleLarge`.
- **M3 sans-serif par défaut** sur `titleMedium`, `bodyMedium`, `bodySmall`, labels.
- Letter-spacing négatif (`-0.5sp`) sur les displays — bonne tension typographique.

**Verdict** : dosage juste. Fraunces marque les titres d'écran (Welcome, ProfileScreen stats, ArboretumScreen header, BadgesScreen sections), s'efface sur le body pour la lisibilité. Un seul regret mineur : pas de variante italique custom pour les binômes latins (recours au sans-serif italic, acceptable).

---

## 3. Iconographie — Material Outlined, zéro mélange

`BadgeIcons.kt` + Material 3 Compose.

- 15 badges → `Icons.Outlined.*` (DirectionsWalk, Hiking, MilitaryTech, Spa, LocalFlorist, AutoAwesome, Map, Explore, Star, EmojiEvents, CalendarMonth, DateRange, Height, Park, etc.).
- FAB et TopAppBar : Material Outlined partout (MyLocation, Person, Search, MenuBook, ArrowBack, OpenInNew, Info, Code).
- Drawables custom : `ic_arbre_canonical`, `ic_remarquable_badge`, illustrations empty state SVG — tous tintables, pas d'icônes Material rondes 2018 traînant en arrière-plan.

**Verdict** : aucune discontinuité. Le set est pur. Très bon.

---

## 4. Espacements — homogène mais pas systématique

Padding horizontal 16 dp partout (`padding(horizontal = 16.dp)`). Vertical 8/12/16/20 dp selon composant — pas d'erreur visible mais aucun token de spacing centralisé (`space_sm`, `space_md`, `space_lg` absent).

**Verdict** : fonctionnel, pas systématique. Aucune incohérence grave, mais pour la prévisibilité long terme un fichier `ArbresSpacing.kt` ferait sens.

---

## 5. Hiérarchie en 2 secondes — test du scan rapide

| Écran | 1er regard | Verdict |
|---|---|---|
| Welcome | Hero platane vert + titre Fraunces | ✓ |
| Map | Pins colorés sur fond MapLibre | ✓ signal clair |
| Fiche arbre | Nom + badge orange remarquable | ✓ |
| Profile | Stats card en `primaryContainer` | ✓ ressort bien |
| Arboretum liste | Card espèce + photo user | ✓ lisible |
| Badges grille | Débloqués en `tertiaryContainer`, verrouillés en `surfaceVariant` | ✓ différenciés |
| About | Wordmark + attributions | ✓ standard |

**Verdict** : hiérarchie impeccable. Chaque écran guide l'œil par couleur et poids.

---

## 6. Dark mode — robuste, pas cassé

- Primary inversé (`#81C784` clair en dark, `#2E7D32` sombre en light).
- Secondary écorce inversé (`#BCAAAA` en dark, `#6D4C41` en light).
- 4 variantes dark dégradées de `surface` saisonnier (`#1B2620` printemps, `#24201A` automne).
- Orange remarquable identique light/dark (volontaire, signal stable).

**Verdict** : pas de blanc sur blanc, pas de contraste pourri. Le tinting saisonnier en dark est imperceptible — assumé. Les illustrations SVG empty state s'adaptent (vérification plus poussée recommandée à un screenshot pass).

---

## 7. Identité de marque — portée textuellement, pas visuellement

Constat critique :

- **Wordmark « CanoPaname »** visible : Welcome (displayMedium vert), splash (displayMedium blanc), AboutScreen.
- **Icône platane** : `ic_launcher_foreground`, `ic_arbre_canonical` (hero Welcome, MiniArbreCrown splash).
- **Badge remarquable** : disque orange + platane crème (`ic_remarquable_badge.xml`) — marque visuelle forte, mais c'est une marque de **catégorie d'arbre**, pas une marque **de produit**.
- **Absence en session** : pas de logo platane dans la TopAppBar, pas de wordmark subtil en corner, pas de signature visuelle persistante. Une fois passé le splash, le mot « CanoPaname » disparaît jusqu'à ce qu'on aille au Profile → About.

**Verdict** : paradoxe assumé mais gênant pour la mémorabilité publique. L'app tient visuellement sur ses icônes (vert/or/orange) mais ne s'**auto-réfère** jamais. En contexte family & friends et single-player, c'est défendable — l'utilisateur sait ce qu'il a installé. En contexte Obtainium / repo public où la mémorisation du nom compte (« comment ça s'appelait, le truc des arbres ? »), c'est un manque.

---

## 8. Screenshots README — perception externe

`docs/screenshots/01-onboarding.png`, `02-carte.png`, `03-fiche-arbre.png`.

3 screenshots : Welcome, Carte, Fiche. Ce qu'ils ne montrent pas : Arboretum, Badges, Profile, Remarquables, Species. Le **journey de progression est invisible** depuis le repo. Un visiteur GitHub qui ouvre le README voit une app de carte à pins, pas un Pokédex de collection.

**Verdict** : suffisant pour l'intro, partiel pour le pitch.

---

## 9. Cohérence dans le détail — points secondaires

- **`ic_remarquable_badge.xml`** (orange + platane crème) est utilisé en pin carte ET en badge fiche ET en FAB ★ : alignement parfait.
- **`SeasonAmbience` particules** (flocon/pétale/feuille) — esthétique discrète et juste.
- **Splash crown** (7 mini-platanes en arc semi-circulaire au-dessus du hero) — composition asymétrique délibérée, très réussie.
- **`ArchiveBanner`** plein-écran en mode archive read-only : signalétique claire, palette `tertiaryContainer` cohérente.
- **Pas de gradient** dans les cards (palette plate Material 3) — choix sain, cohérence > variété.

---

## Pistes considérées et écartées

- **Refondre les illustrations empty state en iconographie riche** — budget illustrateur trop grand pour gain marginal sur une app perso. Les SVG simples actuelles tiennent.
- **Ajouter des gradients aux cards** — non, la palette plate Material 3 est plus cohérente avec l'épuration.
- **Variante italique custom Fraunces pour les binômes latins** — coût (police + intégration) vs gain (cosmétique mineur). Rejeté pour v1.x.
- **Refonte couleur remarquables** — palette actuelle propre, pas de raison de toucher.

---

## Recos par tier

| Tier | Titre | 1 phrase |
|---|---|---|
| v1.0.1 | Étendre le set de screenshots README de 3 à 6 frames | Ajouter Arboretum, Badges, Profile pour montrer la progression — un visiteur du repo ne doit pas croire que c'est une app de carte. |
| v1.0.1 | Vérifier au screenshot pass que les illustrations SVG empty state s'adaptent au dark mode | Audit visuel dark mode systématique sur les 6 écrans qui ont des illustrations. |
| v1.1.0 | Ajouter une signature visuelle subtile en session | Mini-icône platane (16-24 dp) en bout de TopAppBar Map ou Profile, ou wordmark compact dans un menu — marque le nom sans casser l'épuration. |
| v1.1.0 | Centraliser les tokens de spacing | `ArbresSpacing.kt` avec `space_xs` 4 / `space_sm` 8 / `space_md` 12 / `space_lg` 16 / `space_xl` 20 dp ; remplacer les hardcodes pour prévisibilité long terme. |
