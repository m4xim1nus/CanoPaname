# Prospection — Upgrade Arboretum

> Doc de prospection vivant pour préparer un futur cycle d'upgrade de l'écran Arboretum (photos de référence, picto, saisonnalité, états saisonniers des espèces). Aucune décision engagée, aucun code touché. Sert de pré-lecture quand le cycle s'ouvrira.

---

## Partie A — Source actuelle : « Fiches Essences du Guide des Essences de Paris »

### Contexte

L'app utilise déjà 3 sources OpenData Paris : `les-arbres` (géoloc), `arbresremarquablesparis`, et `fiches-essences-du-guide-des-essences-de-paris`. De cette dernière, **on n'exploite aujourd'hui qu'un seul champ** : `nom_fichier_pdf_associe` → URL PDF, exposée comme bouton « Fiche PDF » dans `SpeciesDetailScreen.kt` via `SpeciesInfo.pdfUrl`.

Question : qu'est-ce qu'il y a *réellement* dedans (API + PDF) qu'on n'exploite pas, et est-ce extractible proprement à build-time ?

### 1. Ce que l'API CSV/JSON expose directement (200 fiches, 28 champs)

Source : `https://opendata.paris.fr/api/explore/v2.1/catalog/datasets/fiches-essences-du-guide-des-essences-de-paris/records`

Champs structurés directement exploitables (modalités fixes, parsing trivial) :

| Champ | Modalités | Pertinence Arboretum |
|---|---|---|
| `nom_latin` | binôme | clé jointure avec `SpeciesIndex` |
| `nom_commun` | texte libre | fallback `nv` |
| `taille_de_developpement` | Petit (<8 m) / Moyen (8-15 m) / Grand (>15 m) | bande Arboretum |
| `type_de_port` | Étalé, Ovoïde, Boule, Conique, Colonnaire, Pleureur | **picto silhouette (6 modalités)** |
| `type_de_feuillage` | Caduc / Persistant / Marcescent | **bande Arboretum + état saisonnier** |
| `arbre_a_fleurs` | Oui / Non | flag floraison visible |
| `indigenat` | Indigène IDF / Indigène national / Exotique / Horticole | badge potentiel |
| `origine_geographique` | texte (ex : « Chine », « Bassin méditerranéen ») | texte court fiche |
| `exposition_possible_{soleil,mi_ombre,ombre,indifferent}` | bool (Oui/vide) | picto exposition |
| `besoins_en_eau_type_de_sol_tolere_{sature,humide,intermediaire_frais,mesophile,sec_tres_sec}` | bool | bandeau écologie |
| `site_de_plantation_possible_*` (10 contextes) | bool | « où la voir » |
| `lien_vers_arbre` | URL vers `arbresremarquables` | déjà couvert ailleurs |

**Limites du CSV** : aucune description textuelle, aucune saisonnalité, aucune photo, aucun picto. Seulement des bool/modalités.

### 2. Ce que les PDF contiennent — beaucoup plus que l'API

Vérifié sur la fiche `Salix babylonica` (PDF 1 MB, 2 pages). **Le template est standardisé** (Guide des Essences de Paris — 2024, édité par CEREMA + Ville de Paris) : les 200 PDF ont la même mise en page, donc une extraction par zones est faisable une fois et marche partout.

Contenu *en plus* de l'API, classé par valeur produit :

#### Saisonnalité (le gros gain)
- **Calendrier de floraison** sur 12 mois (mois colorés vs blancs) — bitfield 12 bits
- **Calendrier de fructification** sur 12 mois — bitfield 12 bits
- `type_de_feuillage` (déjà API) interprétable côté UI comme état saisonnier (caduc → chute automne, persistant → vert toute l'année, marcescent → feuilles sèches restantes hiver)

#### Description botanique structurée
- Famille botanique (ex : Salicacées)
- Hauteur chiffrée (15 m)
- Envergure du houppier (10 m)
- Vitesse de croissance (Lente/Moyenne/Rapide)
- Longévité (Courte / Moyenne 100-200 ans / Longue)
- Description courte du feuillage (« Feuilles caduques lancéolées »)
- Description courte de l'écorce (« Grise fissurée »)
- Description courte de la floraison (« Chatons discrets jaune pâle »)
- Description courte de la fructification (« Capsule »)
- Densité du feuillage (Faible/Moyenne/Dense)
- Potentiel allergisant des pollens (Faible/Moyen/Fort)

#### Contexte parisien (textes courts éditoriaux)
- Encart « L'essence à Paris » (~2 phrases — *ex : « Essence typique des bords d'eau, on la retrouve majoritairement dans les jardins. Présent à l'Arboretum de Paris. »*)
- Encart paysager (1-2 phrases — *« Saule à fort intérêt paysager par sa forme pleureuse… »*)

#### Services écosystémiques (3 scores /10 + descriptions)
- Régulation climat local
- Régulation de la ressource en eau
- Intérêt biodiversité

#### Conditions de plantation détaillées (tableaux gradients)
- Gradient trophique pH (5 niveaux)
- Gradient hydrique (5 niveaux)
- Spécificités racinaires (texte)

#### Adaptation climatique & gestion
- Résistance sécheresse / chaleur
- Rusticité (°C min)
- Tolérance taille / taille architecturée
- Tenue mécanique branches
- Présence d'épines (Oui/Non)
- Pathologies & parasites
- Approvisionnement pépinière / conditionnement / formes culturales

#### Synthèse « À RETENIR »
- **Atouts** (bullet points, ~2-3 puces)
- **Limites et contraintes** (bullet points, ~2-3 puces)

→ C'est probablement le bloc le plus actionnable pour un upgrade éditorial de la fiche-espèce : ton court, déjà rédigé par Ville de Paris.

#### Visuel
- **2 photos botaniques par fiche** (1 gros plan inflorescence/feuille + 1 plan large arbre entier) — JPEG embarquées (~20-25 KB chacune en 1240×~580)
- **1 picto silhouette d'arbre** en en-tête (basé sur le port, donc *6 silhouettes uniques* sur l'ensemble du dataset, pas 200)
- **Crédits photos** : J.E. Michaut, B. Morlon, B. Serres / Ville de Paris (à conserver en mention)

### 3. Faisabilité d'extraction

#### Texte
Le PDF contient une **couche texte vectorielle** par-dessus les images de fond (vérifié via lecture multimodale du PDF). Donc avec `pdfplumber` ou `pymupdf` côté `tools/build_dataset.py`, extraction structurée probable :
- Parser par bounding box / par ordre de lecture (template stable → on peut hard-coder les zones)
- Régex sur labels fixes (`Famille :`, `Hauteur :`, `Floraison :`…)
- Cases cochées : détecter par couleur de fond plutôt qu'OCR (les cases cochées ont un fond coloré → check de pixel sur l'image rasterisée, ou détection geometrique côté pdfplumber rect/fill)

Coût build dev : **demi-journée à 1 jour** pour parser propre + cache local (à l'image du `tools/.wikipedia-cache/`).

#### Photos
- `pymupdf` (`fitz`) → `page.get_images()` rend la liste des XObject JPEG embarqués
- Filtrer : on garde les 2 grosses (>200 KB après extraction brute) et on ignore les pictos décoratifs (logos Ville de Paris, Cerema, Sésame, feuillages déco du template)
- Recompresser en WebP ~80 KB chacune → 200 × 2 × 80 KB ≈ **32 MB** à embarquer (gérable, comparable au GeoJSON 33 MB déjà committé)
- Alternative légère : 1 seule photo (plan large) → ~16 MB

#### Pictos
- **Silhouette de port** : 6 silhouettes uniques (1 par modalité de `type_de_port`). Soit on extrait 6 PNG depuis le PDF, soit on les **redessine en SVG/Compose** (plus propre, scalable, themable). Recommandation : redessin.
- **Calendrier floraison/fructification** : c'est un strip 12 cases coloriées. Redessinable trivialement en Compose à partir d'un `List<Boolean>` 12 éléments. Aucune extraction d'image nécessaire.
- **Pictos sites de plantation** (icônes alignement / place / cimetière…) : présents dans le template mais standardisés. À redessiner en Compose si on veut les afficher, ou à substituer par texte.

→ Globalement, **le PDF n'est pas un bon set d'icônes à extraire tel quel** (icônes décoratives intégrées au layout). Tout ce qui est picto vaut mieux le redessiner.

### 4. Couverture du dataset face à canopaname

- canopaname : 784 espèces identifiées dans `SpeciesIndex` (~217 k arbres `les-arbres`)
- fiches-essences : 200 fiches
- Ratio : **~25 % de couverture nominale**, mais ces 200 = essences candidates au planting parisien, donc concentrent les espèces fréquentes. Couverture *par individus* probablement >85 % (Platane, Tilleul, Marronnier, Érable, Chêne… tous dans les 200).
- Les 600 espèces non couvertes sont des espèces rares ou variétés peu utilisées en plantation. Côté Arboretum, l'enrichissement sera donc partiel mais portera sur les espèces les plus capturées.

### 5. Pistes pour un futur cycle « upgrade Arboretum »

Sans engager de décision, voici ce qu'un cycle dédié pourrait exploiter (rangé par effort croissant) :

#### Niveau 1 — quick wins API seule (0.5j build, 0 fetch PDF)
- `SpeciesInfo` enrichi avec : `taille_de_developpement`, `type_de_port`, `type_de_feuillage`, `arbre_a_fleurs`, `exposition_possible_*`, `indigenat`, `origine_geographique`.
- UI Arboretum : badges/pills pour port (picto Compose), feuillage (caduc/persistant), taille (S/M/L), exposition (soleil/mi-ombre/ombre).
- Aucune photo, aucune saisonnalité fine.

#### Niveau 2 — saisonnalité (1j build, PDF parsing partiel)
- Parser **uniquement** les calendriers floraison/fructification et le bloc « À RETENIR » (Atouts / Limites) du PDF
- Donne : 2 bitfields 12 mois par espèce + 2-3 bullets de texte court
- Permet : indicateur saisonnier dynamique sur la fiche espèce (« en floraison ce mois-ci »), filtre Arboretum par saison vive cohérent avec `SeasonStore` déjà en place
- Risque : 200 PDF × ~1 MB = 200 MB à fetcher au build (cache local indispensable, comme `tools/.wikipedia-cache/`)

#### Niveau 3 — photos de référence (1-2j build, extraction images)
- `pymupdf.get_images()` → WebP recompressé → assets/essence-photos/{nom_latin}-{0|1}.webp
- +16-32 MB APK
- Donne : photo botanique officielle Ville de Paris sur la fiche espèce, en complément des photos utilisateur (pas en remplacement — cohérent avec la décision actuelle « les photos des captures servent d'illustration »)
- Implication CLAUDE.md : revoir la phrase *« Les images Wikipedia sont volontairement absentes — les photos des captures utilisateur servent d'illustration »* (qui resterait vraie pour Wikipedia, faux pour Ville de Paris)

#### Niveau 4 — extraction texte complète (2-3j build)
- Tous les blocs structurés : description botanique, services écosystémiques, conditions de plantation, etc.
- Donne : fiche espèce dense type « guide papier »
- Risque : verbeux, déborde le ton produit actuel (`SpeciesDetailScreen` est aujourd'hui synthétique). À arbitrer côté design avant.

### 6. Points d'attention

- **Licence des contenus** : OpenData Paris est sous licence ODbL. Les photos sont créditées Ville de Paris (auteurs nommés). Réutilisation OK avec mention. À documenter dans `LICENSE` / un crédit en bas de fiche espèce si on importe les visuels.
- **Stabilité du dataset** : édition 2024, ~200 fiches. La dernière maj API date du 27 avril 2026 (vue lors de l'inspection). Pas un dataset volatile mais penser à versionner le fetch dans `build_dataset.py`.
- **Cohérence avec décision produit existante** : « Pas de service externe au runtime. La fiche-espèce ne fait pas exception » (CLAUDE.md). Compatible : tout reste pré-baké à build-time.
- **Pas de Wikipedia ici** : ce cycle n'invalide pas la cascade Wikidata/Wikipedia actuelle pour `summary` ; il l'enrichit côté champs structurés + visuels. Possible cohabitation `SpeciesInfo.summary` (Wikipedia, texte long) ET nouveaux champs Ville de Paris (structuré + atouts/limites).

### 7. Verdict synthétique partie A

- **API CSV seule** : suffisante pour une vague de polish badges/pills dans l'Arboretum (port, feuillage, taille, exposition). Gain visible immédiat, effort minimal.
- **PDF parsing** : trésor de données structurées (calendriers floraison/fructification + atouts/limites). Effort raisonnable. C'est *là* qu'est le gros gain produit, surtout pour l'axe « saisonnalité / états possibles ».
- **Photos** : faisable, mais coût APK et arbitrage design.
- **Pictos extraits du PDF** : pas viable comme set d'icônes universel. Mieux vaut redessiner 6 silhouettes de port + calendriers Compose côté UI.

---

## Partie B — Sources complémentaires

Couvre les deux trous identifiés en Partie A :
1. **Pictos pour toutes les espèces** : les 6 silhouettes de port couvrent un axe mais pas une identité visuelle par espèce.
2. **Fallback pour les ~584 espèces hors fiches-essences** : la longue traîne (variétés rares, cultivars, hybrides) n'a aucune source structurée Ville de Paris.

Investigations menées sur 13 sources externes (6 pictos + 7 photos). Les chiffres ci-dessous sont des **estimations** basées sur sondages 5-10 espèces ; un cycle réel demanderait une mesure sur l'intégralité du `SpeciesIndex`.

### B.1 — Pictos par espèce : pas de source taxon viable

**Sources investiguées** :

| Source | Format | Licence | Granularité | Couverture estimée (784 esp.) | Verdict |
|---|---|---|---|---|---|
| **PhyloPic** (api.phylopic.org) | SVG | CC0 + CC-BY mix | Genre / espèce | <30 % (plantes sous-prioritaires sur la base, 12k silhouettes globales) | ✗ trop lacunaire |
| **Wikidata P18** (image) | PNG/JPG/SVG | Hétérogène (Commons) | Espèce via QID | ~50 % photos (pas silhouettes) | utilisable comme photo, pas comme picto |
| **Wikidata P2910** (icon) | divers | divers | Espèce | quasi-vide pour la flore | ✗ |
| **Wikidata P13696** (taxon aspect image) | divers | divers | À creuser | inconnu | à investiguer si cycle s'ouvre |
| **The Noun Project** | SVG | CC-BY ou payant | Mot-clé | ~20-30 icônes « tree » génériques | ✗ pas par taxon |
| **Wikimedia Commons / catégories silhouettes** | SVG | CC0 / CC-BY | Mot-clé | ~50-100 SVG génériques | ✗ pas par espèce |
| **Köhler / Curtis / USDA PLANTS** | planches couleur PNG | Domaine public | Variable | Faible sur essences parisiennes ornementales | ✗ indexation non-taxonique, fragmenté |
| **FontAwesome / Material Icons** | SVG icon font | OFL / Apache | Mot-clé | 5-10 icônes arbres génériques | ✗ |

**Verdict B.1** : aucune source ne fournit un picto/silhouette **par espèce** avec couverture ≥50 % sur les 784 taxons canopaname.

**Piste recommandée — pictos procéduraux Compose à partir d'attributs structurés** :

À partir des données *déjà* en main via `fiches-essences` (Partie A) :
- **Port** (6 modalités : étalé, ovoïde, boule, conique, colonnaire, pleureur) → 6 silhouettes Compose dessinées une fois
- **Feuillage** (caduc / persistant / marcescent) → variant de teinte / texture
- **Saisonnalité** (mois courant ∈ bitfield floraison ?) → halo / accent visuel
- **Famille botanique** (extractible du PDF — Salicacées, Fagacées, Rosacées…) → palette de couleur dérivée

→ Combinaison `port × feuillage` = matrice ~18 silhouettes Compose pré-dessinées qui couvrent **100 % des 200 espèces fiches-essences**. Pour les 584 hors-fiches, on retombe sur un picto neutre (silhouette « arbre générique ») + attribut feuillage si dérivable de Wikidata/Wikipedia.

Pas un picto-par-espèce, mais une **identité visuelle structurée** suffisante pour différencier les espèces dans des listes denses (Arboretum, Pokédex).

Coût implémentation côté Compose : ~1-2j pour les silhouettes vectorielles + le système de composition.

### B.2 — Photos fallback pour les 584 espèces hors fiches-essences

**Sources investiguées** :

| Source | API | Licence | Granularité | Couverture sondée | Verdict |
|---|---|---|---|---|---|
| **Wikidata P18** | SPARQL `query.wikidata.org/sparql` | Commons (mix CC0/CC-BY/CC-BY-SA/PD) | Espèce via QID (déjà en main) | ~50-75 % sur arbres tempérés | ✓ source primaire |
| **iNaturalist** | `api.inaturalist.org/v1/taxa` | Par photo (`license_code`) : pd / cc-by / cc-by-sa / cc-by-nc / null | Espèce + cultivars | ~80 % brut, ~50 % après filtre licences strictes | ✓ secondaire avec filtre |
| **Wikipedia REST API FR** | `fr.wikipedia.org/api/rest_v1/page/summary/{title}` (déjà utilisé pour `summary`) | Commons mix | Article | théoriquement ~75 %, mais l'agent a observé des `thumbnail: null` inattendus | à reproduire avant de juger |
| **GBIF** | `api.gbif.org/v1/species/{key}/media` | Beaucoup de CC-BY-NC | Espèce | ~50 % | ✗ licences NC bloquantes |
| **INPN (MNHN)** | Pas d'API publique documentée | Variable | Espèces fr | excellent contenu, mais scraping HTML | ✗ pas accessible programmatiquement |
| **PlantNet** | API auth, pas de bulk endpoint | Variable | Photos communauté | inaccessible en bulk | ✗ |
| **Tela Botanica eFlore** | `api.tela-botanica.org/eflore` | Variable | Espèces fr | tests 404 / scraping HTML nécessaire | ✗ pas opérationnel sur l'échantillon |

**Notes licences** :
- Projet sous MIT, donc compatible techniquement avec CC0, CC-BY, CC-BY-SA, et même CC-BY-NC en théorie pour usage perso/family & friends. Mais : filtre strict **CC0 + CC-BY uniquement** recommandé pour rester propre côté redistribution (en cas de fork tiers, ou si l'app sort un jour de l'usage strictement perso).
- CC-BY = obligation d'attribuer → fichier `CREDITS.md` ou écran « crédits » dans Profil, citant photographes + sources par image. Faisable et standard.
- CC-BY-SA = obligation de redistribuer les images sous même licence → OK si on liste explicitement, mais alourdit la doc.

**Cascade recommandée (B.2)** :

```
Pour chaque espèce sans photo Ville de Paris (584 cas) :
  1. Si wikidataQid : SPARQL P18 → first image (filtrer licence Commons CC0/CC-BY)
     → hit ? stocker URL + crédit auteur
  2. Si miss : iNaturalist /taxa?q={nom_latin}
     → filtrer license_code ∈ {'cc0','cc-by'} strictement
     → hit ? stocker URL + crédit
  3. Si miss : pas de photo → UI placeholder explicite
                (« Pas de photo de référence — vos captures servent d'illustration »)
```

**Couverture estimée après cascade** : 60-80 % des 584 (≈350-470 photos supplémentaires).

**Coût build** :
- ~2-3 min pour les 584 fetch (Wikidata batches 50 + iNat throttle 0.2s)
- Cache local indispensable (cf. pattern `tools/.wikipedia-cache/` existant) → `tools/.taxa-images-cache/`
- Recompression WebP ~80 KB chacune → +28-37 MB APK si toutes embarquées

**Risque** : qualité photo hétérogène (iNaturalist = photos terrain communautaires, pas planches botaniques). Visuellement, ça peut ne pas matcher le ton des photos officielles Ville de Paris. À tester sur un sous-ensemble avant de tout intégrer.

**Verdict B.2** : Cascade Wikidata P18 → iNaturalist (avec filtre licences strict) est faisable. Couverture *raisonnable* mais pas complète — accepter une UI placeholder pour la longue traîne ultime.

### B.3 — Fallback pour les autres attributs (saisonnalité, port, feuillage, famille, hauteur, descriptions)

Pour les 584 espèces hors fiches-essences, les attributs structurés et textuels qu'on souhaite afficher demandent une cascade différente de celle des photos. Investigation sur 7 sources, sondage sur 5 espèces obscures (cultivars + hybrides).

**Sources investiguées** :

| Source | Accès | Licence | Force | Faiblesse |
|---|---|---|---|---|
| **Wikidata** (SPARQL) | `query.wikidata.org/sparql` | **CC0** (idéal) | famille (P171), feuillage (P10906 candidate — à reconfirmer), hauteur (P2048), nom commun (P1843), description Wikidata courte | aucune propriété structurée pour floraison/fructification (mois), port faiblement renseigné |
| **POWO** (Kew, via `pykew`) | pas d'API officielle, lib Python tierce | Kew restrictif | famille botanique, distribution textuelle, description botanique | cultivars largement absents (POWO = taxonomie sauvage), pas de saisonnalité ni hauteur chiffrée |
| **Wikipedia FR infobox** | API `parse` + `mwparserfromhell` | **CC-BY-SA** (texte) | famille, hauteur, description premier paragraphe, parfois floraison en texte libre | cultivars rarement notables → article absent, extraction infobox fragile (variations de templates) |
| **Tela Botanica eFlore** | `api.tela-botanica.org/eflore` | CC-BY-SA | théoriquement riche (saisonnalité FR structurée) | endpoints testés en 404 lors de cette prospection — statut API à reconfirmer |
| **INPN / TaxRef** (MNHN) | API présente mais peu documentée | ODbL | distribution FR, statut, indigénat | pas de hauteur/port/saisonnalité, cultivars absents (référentiel taxonomique strict) |
| **EOL (Encyclopedia of Life)** | `api.eol.org/v1/` | mix (par source amont) | agrégateur large, famille + descriptions | qualité hétérogène, sparse sur saisonnalité |
| **TRY Plant Trait Database** | demande académique | CC-BY | **gold pour traits phénologiques** (floraison, fruiting, leaf phenology, hauteur max) | accès non-API → hors portée build-time automatisable |

**Cascade recommandée par attribut** (estimations sur sondage, à recouper sur l'intégralité du `SpeciesIndex` lors d'un cycle réel) :

| Attribut canopaname | Source primaire (200 fiches) | Cascade fallback pour 584 | Couverture cumulée estimée |
|---|---|---|---|
| **Famille botanique** | fiches-essences PDF | Wikidata P171 (remonter rank=family) → POWO → Wikipedia infobox FR | **~90 %** |
| **Feuillage** (caduc / persistant / marcescent) | fiches-essences API | Wikidata propriété candidate (à confirmer le P-number exact) → texte Wikipedia FR (regex « caduque » / « persistant ») | **~80-85 %** |
| **Port / forme** (6 modalités) | fiches-essences API | Wikidata ne fournit pas les 6 modalités → inférable du genre pour cas évidents (Cupressus → colonnaire, Salix babylonica → pleureur via lemme) | **~30-40 %** — souvent silhouette générique |
| **Hauteur adulte (m)** | fiches-essences PDF (chiffrée) | Wikidata P2048 → Wikipedia FR infobox regex | **~40-60 %** |
| **Indigénat** (indigène IDF / national / exotique / horticole) | fiches-essences API | INPN distribution FR → Wikidata habitat (P2974) → fallback texte Wikipedia | **~60-70 %** |
| **Floraison (12 mois)** | fiches-essences PDF (bitfield) | Wikipedia FR texte (regex « floraison en mars-avril » → bitfield), TRY si accès académique, Tela si rétablie | **~25-45 %** — **lacunaire** |
| **Fructification (12 mois)** | fiches-essences PDF | idem floraison, encore plus rare en texte Wikipedia | **~15-30 %** — **lacunaire** |
| **Description courte** (feuillage, écorce, floraison, fructification) | fiches-essences PDF | premier paragraphe Wikipedia FR (déjà dans `summary`) → EOL description | **~75-85 %** |
| **Atouts / limites éditoriaux** | fiches-essences PDF (« À RETENIR ») | **aucune source** | **0 %** hors fiches |

**Points d'attention spécifiques** :

- **Saisonnalité = trou principal** : aucune source open ne fournit aussi proprement que le PDF Ville de Paris les calendriers floraison/fructification en 12 mois. Pour les 584, on retombe sur du parsing NLP de texte Wikipedia FR (regex « floraison en {mois} ») — ça remonte ~25-45 %. Acceptable si l'UI affiche le calendrier **uniquement quand renseigné**, et un placeholder neutre sinon. **Ne pas inventer de calendrier**.
- **Atouts / limites éditoriaux = trou définitif** : ce sont des textes humains rédigés par les experts Ville de Paris pour 200 essences sélectionnées. Pas de source équivalente pour les 584. Soit on accepte que ce bloc soit absent hors-fiches, soit on s'autorise à reformuler à partir du texte Wikipedia FR (verbeux, ton différent).
- **Port / silhouette** : Wikidata ne porte pas les 6 modalités Ville de Paris. Possible dérivation par règles sur le genre/épithète (Salix babylonica → pleureur, Cupressus sempervirens → colonnaire), mais ad hoc. Plus simple : silhouette générique « arbre » pour les 584.
- **Cultivars horticoles** (~50-100 des 584 : *Malus 'Evereste'*, *Prunus 'Nigra'*…) : couverture quasi-nulle sur toutes les sources sauf parfois Wikipedia FR si le cultivar est notable. UX recommandée : afficher les infos du taxon parent (l'espèce) + mention du cultivar, plutôt que créer une fiche vide.
- **Licences** :
  - Wikidata = CC0 → idéal pour le projet
  - Wikipedia / Tela = CC-BY-SA → demande attribution + signalement licence, n'impose pas la propagation virale sur le code MIT, mais alourdit le fichier crédits
  - POWO / INPN = restrictif ou ODbL → vérifier au cas par cas si on intègre

**Coût build estimé** :
- Wikidata SPARQL : ~30s pour 584 espèces en batch
- POWO (`pykew`) : ~1-2 min avec throttle
- Wikipedia FR + `mwparserfromhell` : ~2-3 min (réutilise le cache `tools/.wikipedia-cache/` existant)
- EOL : ~1 min
- Total séquentiel : **~6-8 min** la première fois (mais cache local → builds suivants quasi-instantanés)

**Verdict B.3** : la cascade Wikidata → POWO → Wikipedia FR → EOL permet une couverture **honnête sur les attributs taxonomiques** (famille, feuillage, description, hauteur partielle) mais reste **lacunaire sur la saisonnalité** (floraison/fructification en mois) — qui est précisément le gros gain identifié en Partie A. Conséquence : ce nouvel axe « saisonnalité / états » sera **riche pour les 200 fiches-essences, dégradé pour les 584 autres**. Acceptable dans le ton produit (single-player, family & friends, simplicité > scale) à condition que l'UI ne masque pas cette inégalité — afficher les champs renseignés, omettre les autres, jamais inventer.

### B.4 — Synthèse couverture combinée

Couverture **photos** sur l'ensemble des 784 espèces canopaname, par stratégie cumulée :

| Stratégie | Espèces couvertes | % | Source |
|---|---|---|---|
| État actuel | 0 photo ref | 0 % | (uniquement photos utilisateur) |
| + fiches-essences PDF | ~200 | ~25 % | photos officielles Ville de Paris |
| + Wikidata P18 (cascade 1) | +200-300 | ~50-65 % | Commons CC0/CC-BY filtré |
| + iNaturalist (cascade 2) | +100-150 | ~65-80 % | iNat CC0/CC-BY filtré |
| Reste sans photo | ~150-280 | ~20-35 % | placeholder UI |

Couverture **pictos / identité visuelle** :

| Stratégie | Espèces couvertes | % | Approche |
|---|---|---|---|
| Picto par taxon depuis source externe | trop lacunaire | <30 % | rejeté |
| Composition Compose `port × feuillage × saison` (avec fiches-essences) | 200 nets + reste générique | 100 % (avec dégradation propre) | redessin Compose, ~1-2j |

Couverture **attributs structurés / textuels** (synthèse de B.3) :

| Attribut | fiches-essences (200) | Avec cascade fallback (sur les 784) | Trou résiduel |
|---|---|---|---|
| Famille botanique | 100 % | ~90 % | cultivars obscurs |
| Feuillage (caduc/persistant/marcescent) | 100 % | ~80-85 % | cultivars + variétés |
| Port (6 modalités) | 100 % | ~30-40 % structuré, sinon silhouette générique | longue traîne |
| Hauteur (chiffrée) | 100 % | ~40-60 % | sparse hors Wikidata P2048 |
| Indigénat | 100 % | ~60-70 % | difficile pour exotiques peu documentées |
| Floraison (12 mois) | 100 % | **~25-45 %** | **trou principal — pas de source open équivalente au PDF** |
| Fructification (12 mois) | 100 % | **~15-30 %** | **trou principal** |
| Description courte (feuillage/écorce/floraison/fructification) | 100 % | ~75-85 % | reformulé depuis Wikipedia FR |
| Atouts / limites éditoriaux | 100 % | **0 %** | **trou définitif** — textes humains spécifiques au guide Ville de Paris |

### B.5 — Pistes pour le cycle « upgrade Arboretum » (mise à jour)

Au-delà des Niveaux 1-4 listés en Partie A § 5, deux niveaux émergent :

#### Niveau 5 — système picto Compose procédural (1-2j Compose)
- 6 silhouettes de port en `ImageVector` Compose
- Composition `port + feuillage + accent saisonnier` côté UI
- Fallback « silhouette neutre » pour les 584 hors-fiches
- Aucun nouvel asset, aucun fetch — purement code Compose

#### Niveau 6 — cascade photos fallback (2-3j build)
- Étendre `tools/build_dataset.py` avec un fetcher Wikidata P18 + iNaturalist (filtre licence)
- Cache local versionné
- Fichier `species-photos.json` mappant `speciesIndex` → URL distante OU asset local + crédit + licence
- Décision à prendre : URLs distantes (charger à la 1ère ouverture, simple, mais nécessite réseau) **ou** embarquer dans APK (+30 MB, offline complet)
  - **Recommandation cohérente avec CLAUDE.md** : embarquer pré-baké, comme tout le reste

#### Niveau 7 — cascade attributs structurés fallback (2-3j build)
- Étendre `tools/build_dataset.py` avec un fetcher Wikidata (SPARQL batch) + POWO + Wikipedia FR infobox + EOL
- Réutilise le cache `tools/.wikipedia-cache/` existant, ajoute `tools/.taxa-attributes-cache/`
- Schéma `SpeciesInfo` élargi : tous champs **nullable**, l'UI décide ne rien afficher si absent
- Saisonnalité reste lacunaire (~25-45 % des 584) → UI affiche le calendrier seulement quand renseigné, jamais inventé
- Atouts/limites = 0 % hors-fiches → bloc « À RETENIR » présent uniquement pour les 200 fiches-essences

### B.6 — Décisions à prendre avant ouverture d'un cycle

(Liste à arbitrer le moment venu — pas d'opinion engagée ici.)

1. **Picto vs photo** : prioriser l'un ou l'autre, ou les deux ? Les pictos sont essentiels pour les listes denses (Arboretum, Pokédex) ; les photos pour la fiche détail.
2. **Strictesse licences** : CC0+CC-BY uniquement, ou accepter CC-BY-SA avec gestion crédit ?
3. **Budget APK** : OK pour +30 MB ? Ou alternative URL distante au 1er run (rupture avec « tout pré-baké ») ?
4. **Quel ton pour les 150-280 espèces sans photo ?** Placeholder neutre, ou placeholder positif (« cette espèce attend votre photo, devenez son premier illustrateur ») ?
5. **Saisonnalité** : intégrer Niveau 2 (parsing PDF calendriers) en même temps que le picto/photo, ou phaser ?
6. **Inégalité entre les 200 fiches et les 584 autres** : assumée et visible dans l'UI (richesse différentiée), ou camouflée (n'afficher que le sous-ensemble d'attributs disponible pour tous) ?
7. **Atouts / limites éditoriaux** : autoriser une reformulation depuis Wikipedia pour les 584, ou réserver ce bloc aux 200 fiches-essences uniquement ?

### B.7 — Verdict synthétique (Parties A + B combinées)

L'upgrade Arboretum est **techniquement réalisable** sans dépendance runtime, en combinant :
- **fiches-essences** API → champs structurés (port, feuillage, taille, exposition, indigénat) → 200 espèces enrichies
- **fiches-essences** PDF parsing → saisonnalité floraison/fructification + atouts/limites + descriptions courtes → 200 espèces enrichies
- **Wikidata P18 + iNaturalist** (cascade photos) → photos référence pour ~60-80 % des 584 espèces hors-fiches
- **Wikidata + POWO + Wikipedia FR + EOL** (cascade attributs) → famille, feuillage, hauteur, description pour ~75-85 % des 584
- **Compose procédural** (port × feuillage × saison) → identité visuelle 100 % avec dégradation propre

Effort total estimé : **7-10j dev** (build_dataset + parse PDF + 2 cascades fetch + UI Compose + tests), réparti sur 2-3 cycles si on phase.

Hiérarchie des gains :

1. **Saisonnalité Ville de Paris** (Niveau 2) = le gros gain, base des « états d'espèce ». Données impeccables mais limitées aux 200.
2. **Quick wins API** (Niveau 1) = port/feuillage/taille pour 200 espèces, effort minimal, gain visuel net dans l'Arboretum.
3. **Picto procédural Compose** (Niveau 5) = identité visuelle 100 % couverture, base solide pour Pokédex.
4. **Cascade photos fallback** (Niveau 6) = comble 60-80 % des 584 trous.
5. **Cascade attributs fallback** (Niveau 7) = comble 75-85 % des trous taxonomiques, mais reste lacunaire sur la saisonnalité (qui est précisément ce qu'on veut le plus).

**Conclusion produit** : assumer l'inégalité entre les 200 fiches-essences et les 584 autres est la décision honnête. L'app reste un *Pokédex parisien* — les espèces communes (qui couvrent >85 % des arbres réels) bénéficient de la richesse maximale ; la longue traîne reste capturable mais avec une fiche dégradée. Cohérent avec le ton single-player / family & friends / simplicité > scale.
