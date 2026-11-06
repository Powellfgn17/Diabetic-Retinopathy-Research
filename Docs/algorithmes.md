# Les algorithmes de la chaîne, du début à la fin

*Groupe 2 — suivi de la rétinopathie diabétique sans IA. Version du 6 novembre 2026.*

Ce document explique **tous les algorithmes de la chaîne, dans l'ordre où ils
s'exécutent** : ce que fait chacun, pourquoi on l'a choisi, ses paramètres, et ce qui se
passe dans la suite de la chaîne quand on les change. Il est écrit pour être lu sans
ouvrir le code ; les noms de classes sont donnés pour ceux qui veulent aller voir.

Chaque étape porte un état :

| Pastille | Signification |
| --- | --- |
| ✅ **Codé** | Implémenté et testé dans `retine-noyau`. Les chiffres donnés ont été mesurés. |
| 🔨 **Point 2** | En cours (semaine du 16/11/2026). Décrit d'après le protocole. |
| 📅 **Point 3 / 4** | Prévu plus tard. Décrit d'après le protocole ; les valeurs marquées *proposition* ne viennent pas du protocole et sont à discuter. |

Ce document est à mettre à jour à chaque étape codée.

---

## Sommaire

1. [Vue d'ensemble](#1-vue-densemble)
2. [Ce qu'il faut savoir avant de lire](#2-ce-quil-faut-savoir-avant-de-lire)

**Partie A — Préparer l'image**

3. [Lecture et normalisation](#3-lecture-et-normalisation--codé) ✅
4. [Canal vert](#4-canal-vert--codé) ✅
5. [Masque du champ de vue](#5-masque-du-champ-de-vue--codé) ✅
6. [Correction de l'éclairage](#6-correction-de-léclairage--codé) ✅
7. [Rehaussement du contraste](#7-rehaussement-du-contraste--codé) ✅
8. [Filtre médian](#8-filtre-médian--codé) ✅

**Partie B — Trouver les points de repère**

9. [Segmentation des vaisseaux (Hessien)](#9-segmentation-des-vaisseaux-hessien--codé) ✅
10. [Squelette (Zhang-Suen)](#10-squelette-zhang-suen--point-2) 🔨
11. [Bifurcations et croisements](#11-bifurcations-et-croisements--point-2) 🔨
12. [Descripteur de Zernike](#12-descripteur-de-zernike--point-2) 🔨

**Partie C — Aligner les deux visites**

13. [Appariement des bifurcations](#13-appariement-des-bifurcations--point-2) 🔨
14. [Estimation de la transformation (RANSAC)](#14-estimation-de-la-transformation-ransac--point-3) 📅
15. [Image recalée (interpolation bilinéaire)](#15-image-recalée-interpolation-bilinéaire--point-3) 📅

**Partie D — Détecter les changements**

16. [Détection des nouveaux microanévrismes](#16-détection-des-nouveaux-microanévrismes--point-3-et-4) 📅

**Partie E — Mesurer**

17. [Les mesures d'évaluation](#17-les-mesures-dévaluation)

**Annexes**

18. [Tableau récapitulatif des paramètres](#18-tableau-récapitulatif-des-paramètres)
19. [Guide de réglage : symptôme → paramètre](#19-guide-de-réglage--symptôme--paramètre)
20. [Petit glossaire](#20-petit-glossaire)

---

## 1. Vue d'ensemble

Le but : aligner deux photos du fond d'œil d'un même patient (visite 1 et visite 2), puis
repérer les microanévrismes apparus entre les deux. Pour aligner, on se sert des
**bifurcations des vaisseaux** comme points de repère : elles ne bougent pas d'une visite
à l'autre. Il faut donc d'abord trouver les vaisseaux.

```
                Visite 1 (référence)            Visite 2 (suivi)
                        │                              │
 A. PRÉPARER   ┌────────┴────────┐            ┌────────┴────────┐
               │ lecture, canal  │            │   même chaîne   │
               │ vert, masque,   │            │                 │
               │ éclairage,      │            │                 │
               │ contraste,      │            │                 │
               │ médian          │            │                 │
               └────────┬────────┘            └────────┬────────┘
 B. REPÈRES             ▼                              ▼
               vaisseaux → squelette           vaisseaux → squelette
               → bifurcations → Zernike        → bifurcations → Zernike
                        │                              │
 C. ALIGNER             └──────────────┬───────────────┘
                                       ▼
                         appariement des bifurcations
                                       ▼
                         RANSAC : transformation
                                       ▼
                         visite 1 recalée sur la visite 2
 D. DÉTECTER                           ▼
                         différence, LoG, seuil
                                       ▼
                         carte des lésions nouvelles / disparues
 E. MESURER       erreur de recalage (FIRE), tolérance T (IDRiD),
                  Dice (HRF), GM/IM et EER (appariement)
```

**La question de recherche, en une phrase** : quelle erreur d'alignement la détection des
microanévrismes supporte-t-elle (la tolérance T), et dans quelles conditions notre chaîne
aligne-t-elle assez bien pour rester sous T ? C'est pour cela que toutes les erreurs sont
finalement exprimées en **diamètres de microanévrisme** (section 17).

**Temps de calcul mesuré** (ordinateur portable, 12 cœurs, image FIRE de 2912 × 2912) :
5 à 9 s pour la partie A, environ 3 s pour la segmentation.

---

## 2. Ce qu'il faut savoir avant de lire

### 2.1 Une image, c'est un tableau de nombres

Une image en niveaux de gris est un tableau de `largeur × hauteur` nombres. Chaque nombre
est l'intensité d'un pixel : 0 = noir, 1 = blanc. Le pixel `(x, y)` est à la colonne `x`
et à la ligne `y`, en partant du coin en haut à gauche.

### 2.2 Le centre d'un pixel est en (x + 0,5 ; y + 0,5)

Le pixel `(3, 5)` occupe le carré qui va de 3 à 4 en x et de 5 à 6 en y ; son centre est
en `(3,5 ; 5,5)`. Si un module prenait le coin et un autre le centre, toutes les mesures
de recalage auraient une erreur d'un demi-pixel invisible (décisions **D1** et **D5**).

### 2.3 « Tout se mesure dans le masque »

Une photo du fond d'œil, c'est un disque clair (la rétine) sur un fond noir sans
information. S'il entrait dans les calculs (moyennes, histogrammes, seuils), il fausserait
tout. L'image voyage donc toujours avec son **masque** (classe `ImageMasquee`), et hors du
masque les étapes écrivent 0.

### 2.4 Une étape ne modifie jamais son entrée

Chaque étape lit l'image reçue, en fabrique une nouvelle et la rend. On peut comparer
« avant » et « après » sans risque, et deux traitements peuvent partir de la même image.

### 2.5 Où sont les paramètres

Tous les réglages sont dans **`config/defaut.json`**. Chaque paramètre a une valeur par
défaut dans le code (`Parametres.java`) que le fichier JSON remplace. À chaque exécution,
les valeurs réellement utilisées sont écrites dans
`executions/<date-heure>/parametres_utilises.json` : **pour comparer deux essais, toujours
garder ce fichier avec les résultats.**

### 2.6 Trois notions qui reviennent partout

**Le percentile.** Le percentile 90 d'un ensemble de valeurs est la valeur sous laquelle
se trouvent 90 % d'entre elles. Un seuil en percentile **s'adapte tout seul à chaque
image** : « garder les 6 % de pixels les plus forts » marche sur une photo pâle comme sur
une photo contrastée, alors qu'un seuil fixe non.

**La gaussienne et σ (sigma).** Flouter avec une gaussienne, c'est remplacer chaque pixel
par une moyenne de ses voisins, où les proches comptent beaucoup et les lointains presque
pas. **σ fixe la taille du voisinage** : l'essentiel du poids tient dans un rayon de 3σ.

**La convolution.** Remplacer chaque pixel par une somme pondérée de ses voisins ; les
poids forment le **noyau**. Le flou, les dérivées et la plupart des détecteurs sont des
convolutions avec des noyaux différents.

### 2.7 Les trois jeux de données

| Jeu | Contenu | Sert à |
| --- | --- | --- |
| **FIRE** | 134 paires (2912 × 2912, champ de 45°), 10 points de contrôle par paire ; catégories S (fort recouvrement), P (faible recouvrement), A (changements anatomiques) | Mesurer l'erreur de recalage |
| **IDRiD** | 81 images avec les microanévrismes délimités à la main | Mesurer la tolérance T de la détection |
| **HRF** | 45 images avec les vaisseaux tracés à la main | Mesurer la qualité de la segmentation (Dice) |

Les images sont séparées en **réglage** (on ajuste les paramètres dessus) et **test**
(servent une seule fois à la fin, paramètres figés) : 34 / 100 paires FIRE, 54 / 27
images IDRiD, 15 / 30 images HRF.

---

# Partie A — Préparer l'image

## 3. Lecture et normalisation ✅ Codé

**Classe :** `AdaptateurImageJ` (module `retine-plugin`)
**Entrée :** fichier image (JPEG, PNG, TIFF) · **Sortie :** image couleur en nombres à virgule

### Ce que ça fait

ImageJ ouvre le fichier, puis chaque valeur est **divisée par 255** (images 8 bits) ou par
65 535 (images 16 bits). Toutes les valeurs sont alors entre 0 et 1.

### Pourquoi

- **Les seuils ne dépendent plus du format** : un seuil de 0,08 veut dire la même chose
  pour FIRE (8 bits) et pour un jeu en 16 bits.
- On garde des nombres à virgule 32 bits jusqu'au bout pour ne rien perdre. On ne repasse
  en 8 bits que pour afficher ou enregistrer une figure (décision **D2**). Après certaines
  étapes, les valeurs sortent un peu de [0, 1] : c'est normal.
- Tout ce qui touche à ImageJ reste dans ce module. Le cœur du traitement (`retine-noyau`)
  n'en dépend pas, pour servir tel quel au logiciel autonome.

### Prévu : la mise à l'échelle commune

La méthodologie prévoit de ramener tous les jeux à une échelle commune. Ce n'est pas
encore codé. En attendant, la segmentation a un paramètre `facteurEchelle` (section
9.9), et la décision **D4** prévoit d'exprimer les tailles en degrés, converties en pixels
avec le ppd (pixels par degré) de chaque jeu.

---

## 4. Canal vert ✅ Codé

**Classe :** `CanalVert` · **Cours :** chapitre 8 (couleur)
**Entrée :** image couleur · **Sortie :** image en niveaux de gris · **Paramètres :** aucun

On garde **uniquement le canal vert** de l'image couleur.

- Le sang **absorbe fortement le vert** : vaisseaux et microanévrismes y sont bien sombres
  sur un fond plus clair. C'est le canal le plus contrasté.
- Le **rouge** est souvent saturé (presque blanc partout) ; le **bleu** est sombre et très
  bruité.

C'est le choix standard de la littérature. L'étape ne calcule rien : elle existe pour que
le choix soit visible et justifié dans la chaîne.

---

## 5. Masque du champ de vue ✅ Codé

**Classe :** `MasqueChampVision`
**Entrée :** image couleur d'origine (pas le canal vert) · **Sortie :** masque (oui/non par pixel)

### Pas à pas

1. **Seuillage du canal rouge** : un pixel est « dans la rétine » si son rouge dépasse
   `seuil`. Le rouge est le canal le plus lumineux dans le disque, la frontière avec le
   fond noir y est la plus nette.
2. **Plus grande composante connexe** : on regroupe les pixels retenus qui se touchent par
   un côté, et on ne garde que le plus gros groupe. Cela élimine reflets isolés et texte
   incrusté dans les coins.
3. **Bouchage des trous** : une zone très sombre dans le disque (gros vaisseau,
   hémorragie) pourrait faire un trou ; on bouche tout trou non relié au bord de l'image.
4. **Érosion** : on rogne le disque de `erosion` pixels tout autour (voir 5.2).
5. **Centre et rayon** : centre = moyenne des positions (convention D1) ; rayon équivalent
   = rayon du cercle de même surface, `√(surface / π)`.

Le masque suit la forme réelle du champ, souvent coupée en haut et en bas par le capteur.

### 5.1 Paramètre `masqueChampVision.seuil` (défaut 0,08)

Niveau de rouge au-dessus duquel un pixel compte comme rétine (0,08 ≈ 20 sur 255).

| Si on l'augmente | Si on le diminue |
| --- | --- |
| Le bord sombre de la rétine est exclu : le masque rétrécit. Trop haut, des zones sombres au bord forment des encoches. | Le halo gris autour du disque et les reflets entrent dans le masque. |

**Impact sur la suite :** faible entre 0,05 et 0,15 sur FIRE.

### 5.2 Paramètre `masqueChampVision.erosion` (défaut 7 pixels)

Le passage du disque clair au fond noir est une bande de transition de quelques pixels.
Pour un détecteur de vaisseaux, cette bande en anneau pourrait ressembler à un long
vaisseau qui ferait le tour de l'image. L'érosion l'exclut.

| Si on l'augmente | Si on le diminue |
| --- | --- |
| On perd une couronne de rétine utile : moins de vaisseaux et de bifurcations en périphérie. | La bande de transition entre dans le masque : risque de faux vaisseaux le long du bord. |

**Mesure du 05/11/2026 (FIRE A01) :** avec 7 px, rayon 1355 px, centre (1456, 1456).
Passer à 30 px ne change presque rien au bord (voir 6.3) : **on garde 7 px**.

**Lien avec la suite :** les bifurcations dont la fenêtre de Zernike sort du masque sont
écartées (section 11). Le masque décide donc aussi de la zone où l'on peut avoir des
points de repère.

---

## 6. Correction de l'éclairage ✅ Codé

**Classe :** `CorrectionEclairage` · **Cours :** chapitres 1 et 5
**Entrée :** canal vert + masque · **Sortie :** image corrigée + masque

### Le problème

La lumière du rétinographe n'éclaire pas uniformément. Ces variations sont **lentes**
(sur des centaines de pixels), alors que vaisseaux et lésions sont **petits**. Sans
correction, un même seuil donnerait trop de détections dans les zones sombres et pas assez
dans les zones claires.

### L'idée

On estime le **fond** (l'éclairage) en floutant très fortement l'image : vaisseaux et
lésions disparaissent, il ne reste que les variations lentes. Puis :

```
image corrigée = image − fond + moyenne de l'image dans le masque
```

On rajoute la moyenne pour que l'image reste à son niveau habituel au lieu de tourner
autour de 0.

### Le piège du bord, et sa solution

Un flou ordinaire mélangerait le **noir extérieur** à la rétine près du bord : le fond y
serait trop sombre, et après soustraction le bord deviendrait trop clair. On utilise donc
une **convolution normalisée**, qui ne fait la moyenne qu'avec les voisins **dans le
masque** :

```
fond = flou(image × masque) / flou(masque)
```

`flou(masque)` donne, pour chaque pixel, la part de son voisinage qui est dans la rétine ;
diviser par cette part donne une vraie moyenne des seuls pixels utiles.

**Astuce de calcul :** avec σ = 146 px, un flou direct serait très lent. Le fond étant très
lisse, on le calcule sur une grille réduite (blocs d'environ σ/6, soit 24 px), puis on
revient à la taille d'origine par interpolation bilinéaire. Le temps ne dépend plus de σ.

### 6.1 Paramètre `correctionEclairage.sigmaRelatif` (défaut 0,05)

Taille du flou **en fraction de la largeur de l'image** : σ = 0,05 × 2912 ≈ 146 px sur FIRE.

| Si on l'augmente | Si on le diminue |
| --- | --- |
| Fond plus lisse : les grandes variations sont retirées, les variations moyennes (100 à 200 px) restent. | Le fond suit des détails plus fins. Trop petit, il « voit » le disque optique et les gros vaisseaux : ils sont en partie soustraits, deviennent pâles, avec des auréoles. |

**Règle :** σ doit rester **bien plus grand que le plus gros objet à garder** (gros
vaisseaux ≈ 20 px, disque optique ≈ 400 px sur FIRE).

### 6.2 Impact sur la suite

La détection des changements (section 16) compare les intensités des deux visites : une
correction d'éclairage différente entre les deux créerait de fausses différences. Les deux
visites doivent passer par **exactement les mêmes paramètres**.

### 6.3 Le halo au bord (mesuré)

Mesure du 05/11/2026 sur FIRE A01, selon la distance au bord du masque :

| Distance au bord | Canal vert brut | Après prétraitement |
| --- | --- | --- |
| 1–3 px | 0,36 | 0,82 |
| 21–30 px | 0,34 | 0,74 |
| 46–60 px | 0,33 | 0,64 |
| intérieur (> 60 px) | 0,30 | 0,49 |

La photo s'éclaircit un peu sur ses 60 derniers pixels. Cette variation est **plus étroite
que σ**, donc la correction ne la retire pas, et le rehaussement du contraste l'amplifie :
**un anneau clair au bord**. Pas grave pour les vaisseaux (on cherche des creux sombres,
voir 9.6), mais à surveiller pour la détection des microanévrismes.

---

## 7. Rehaussement du contraste ✅ Codé

**Classe :** `RehaussementContraste` · **Cours :** chapitre 3 (traitement ponctuel)
**Entrée :** image corrigée + masque · **Sortie :** image dans [0, 1] + masque

### Ce que ça fait

Un **étirement linéaire de l'histogramme**. On cherche `bas` (le percentile
`percentileBas`) et `haut` (le percentile `percentileHaut`), puis :

```
nouvelle valeur = (valeur − bas) / (haut − bas)     puis bornée entre 0 et 1
```

`bas` devient noir, `haut` devient blanc, le reste est étalé proportionnellement. Les 1 %
les plus extrêmes de chaque côté sont écrasés à 0 ou à 1.

- **Percentiles plutôt que min et max** : un seul reflet suffirait à fixer le max, et
  l'étirement ne servirait à rien.
- **Calculés dans le masque** : avec le fond noir, le percentile bas serait 0.

### 7.1 Paramètres `percentileBas` (défaut 1) et `percentileHaut` (défaut 99)

| Si on les écarte (0,1 et 99,9) | Si on les rapproche (5 et 95) |
| --- | --- |
| Contraste doux. Un gros exsudat ou le disque optique peut tirer le blanc vers le haut et laisser le reste grisâtre. | Contraste fort, mais exsudats et disque deviennent des taches blanches uniformes, les vaisseaux les plus sombres des traits noirs uniformes. |

**Impact sur la suite :** l'étirement est linéaire, il ne change pas *où* sont les
vaisseaux. La segmentation utilise des seuils en percentiles : elle y est **peu sensible**.
L'effet compte pour l'affichage et pour la comparaison d'intensités entre visites.

---

## 8. Filtre médian ✅ Codé

**Classe :** `FiltreMedian` · **Cours :** chapitre 5
**Entrée :** image + masque · **Sortie :** image débruitée + masque

### Ce que ça fait

Pour chaque pixel, on trie les valeurs de la fenêtre carrée autour de lui (côté
`2 × rayon + 1`) et on garde **celle du milieu**.

- Un pixel de bruit isolé a une valeur extrême : il finit au bout de la liste triée et
  **n'est jamais choisi**. Une moyenne, elle, l'étalerait sur ses voisins.
- Les **bords nets** sont conservés : de chaque côté d'un bord, la majorité des voisins
  appartient au même côté. Une moyenne rendrait les bords flous.

Au bord du masque, seuls les voisins **dans le masque** comptent.

### 8.1 Paramètre `filtreMedian.rayon` (défaut 2, fenêtre de 5 × 5)

| Si on l'augmente | Si on le diminue |
| --- | --- |
| Débruitage plus fort, mais **tout objet plus petit qu'environ la moitié de la fenêtre disparaît** : vaisseaux fins coupés, microanévrismes effacés. Plus lent. | Moins de débruitage : plus de détections parasites dans la segmentation. 0 = pas de filtre. |

### ⚠ À retenir pour la détection

Un microanévrisme fait quelques pixels. Le protocole impose **au plus 3 × 3 (rayon 1) sur
la branche de détection**. Le rayon 2 convient aux vaisseaux, mais la détection (section
16) aura besoin de son propre réglage, et il faudra vérifier que les microanévrismes
d'IDRiD survivent au filtre.

---

# Partie B — Trouver les points de repère

## 9. Segmentation des vaisseaux (Hessien) ✅ Codé

**Classe :** `SegmentationHessienne` · **Cours :** chapitres 6 et 7
**Entrée :** image prétraitée + masque · **Sortie :** masque des vaisseaux + carte de réponse

C'est l'étape la plus risquée du projet : tout le reste en dépend.

### 9.1 L'intuition : un vaisseau est une vallée

Imaginez l'image comme un **paysage en relief** où l'altitude d'un pixel est son
intensité. Les vaisseaux, sombres, sont des **vallées** étroites et allongées. Coupée en
travers, une vallée donne un profil en « U » ; suivie dans sa longueur, elle est presque
plate.

La **courbure** mesure à quel point une ligne se plie :

- en travers d'un vaisseau : **forte et positive** (le « U ») ;
- le long du vaisseau : **presque nulle**.

### 9.2 Le Hessien : la courbure dans toutes les directions

On mesure la courbure avec les **dérivées secondes** : `Ixx` (horizontale), `Iyy`
(verticale) et `Ixy` (terme croisé, pour les directions en biais). Rangées en tableau,
elles forment la **matrice hessienne** :

```
H = | Ixx  Ixy |
    | Ixy  Iyy |
```

Ses deux **valeurs propres** λ1 et λ2 (avec |λ1| ≤ |λ2|) sont les courbures dans les deux
directions principales, quelle que soit l'orientation du vaisseau :

```
t = (Ixx + Iyy) / 2        d = (Ixx − Iyy) / 2
valeurs propres = t ± √(d² + Ixy²)
```

| Ce qu'on voit | λ1 | λ2 |
| --- | --- | --- |
| Vaisseau sombre (vallée) | ≈ 0 | **fortement positive** |
| Ligne claire (crête) | ≈ 0 | fortement négative |
| Tache sombre ronde | positive | positive |
| Zone plate | ≈ 0 | ≈ 0 |

### 9.3 Les dérivées sont prises sur l'image floutée à l'échelle σ

Dériver deux fois une image brute amplifie énormément le bruit. On convolue donc
directement l'image avec **les dérivées d'une gaussienne** de taille σ (flouter et dériver
en une seule opération). Les convolutions sont **séparables** (une passe horizontale puis
une verticale, beaucoup plus rapide qu'en 2D) et réparties sur tous les cœurs du
processeur. Les noyaux couvrent ±4σ et sont corrigés pour donner des dérivées exactes sur
une droite et une parabole (vérifié par un test).

### 9.4 La réponse : σ² × λ2, maximum sur plusieurs échelles

```
réponse(σ) = σ² × λ2   si λ2 > 0   (vallée = vaisseau sombre)
           = 0         sinon
```

- **λ2 > 0 seulement** : les vaisseaux sont sombres ; une valeur négative serait une ligne
  claire (reflet, bord d'exsudat).
- **× σ²** : plus on floute, plus les courbures baissent ; σ² compense pour que les
  réponses des différentes échelles soient **comparables**.
- **Plusieurs σ** : les vaisseaux vont d'environ 3 px à 20 px de large. On calcule la
  réponse pour chaque σ de la liste et **on garde la plus forte** en chaque pixel.

**Règle pratique :** un vaisseau répond le mieux quand σ ≈ **0,6 × sa largeur apparente**.
La liste 2, 3, 4, 6, 8 couvre surtout les vaisseaux de 3 à 13 px ; les plus gros répondent
encore, un peu moins fort. Hors masque, la réponse vaut 0.

### 9.5 Avant le Hessien : l'écrêtage à la médiane

**Problème découvert sur FIRE A01** : chaque exsudat (tache claire) était entouré d'un
**anneau de faux vaisseaux**. Autour d'une bosse claire, le terrain remonte du fond vers la
bosse ; ce « pied » est courbé vers le haut, comme le fond d'une vallée.

**Solution** : avant le Hessien, on **plafonne** l'image à sa médiane dans le masque. Les
bosses claires deviennent des plateaux : plus de pied, plus d'anneau. Les vaisseaux, plus
sombres que la médiane, ne sont pas touchés.

Paramètre **`segmentation.percentileEcretage`** (défaut 50 = la médiane) :

| Si on l'augmente (70) | Si on le diminue (30) |
| --- | --- |
| Les bosses claires gardent une partie de leur pied : les anneaux reviennent. **100 désactive l'écrêtage.** | On aplatit des vaisseaux pâles (fins ou sur fond sombre), qui disparaissent. |

**Limite :** les petits vaisseaux **sur le disque optique**, plus clairs que la médiane de
la rétine, peuvent s'affaiblir. Le plafond s'applique aussi hors masque, sinon un extérieur
plus clair formerait une marche vue comme un creux (cas trouvé par un test).

### 9.6 Et le bord du champ de vue ?

Hors masque, l'image vaut 0 : le bord est une **marche vers le bas**, courbée vers le bas
côté intérieur, donc λ2 < 0 et **pas de faux vaisseau**. Mesuré sur A01 : de 0 à 0,8 % des
pixels de la bande des 60 px du bord dépassent le seuil « vaisseau », contre 5 % à
l'intérieur. Un test le vérifie aussi.

**Le prix :** les vaisseaux à moins d'environ 2σ du bord sont un peu moins bien détectés.
Peu gênant, puisque les bifurcations proches du bord sont écartées de toute façon.
**Attention :** ce comportement repose sur le 0 hors masque. Si on remplit l'extérieur
autrement, il faut revérifier.

### 9.7 Seuillage par hystérésis

Un seuil unique pose un dilemme : haut, les vaisseaux fins sont coupés ; bas, le bruit
passe. L'**hystérésis** utilise deux seuils :

1. Au-dessus du **seuil haut** : des **germes**, sûrement des vaisseaux.
2. Entre les deux seuils : **gardé seulement si relié à un germe** par une chaîne de pixels
   au-dessus du seuil bas (8 voisins, diagonales comprises).
3. Sous le seuil bas : rejeté.

Un vaisseau fin qui faiblit par endroits reste entier tant qu'il part d'un tronc solide ;
une tache de bruit isolée sans germe est rejetée.

| Paramètre | Défaut | Signification |
| --- | --- | --- |
| `segmentation.percentileHaut` | 94 | les 6 % de pixels les plus forts du champ sont des germes |
| `segmentation.percentileBas` | 88 | au plus 12 % des pixels du champ peuvent être retenus |

Ces valeurs partent de l'ordre de grandeur de la place des vaisseaux sur une photo
(environ 10 % du champ). **Mesuré sur 5 images FIRE : 10 à 11 %** classés en vaisseaux.

| Si on augmente les percentiles | Si on les diminue |
| --- | --- |
| Vaisseaux plus fins, petits vaisseaux perdus, réseau coupé → **moins de bifurcations**. | Vaisseaux plus épais, plus de bruit et de barbes → **fausses bifurcations**. |
| **Écarter** haut et bas : réseau plus continu, mais le bruit relié suit aussi. | **Rapprocher** haut et bas : on revient à un seuil unique. |

Un pixel de réponse nulle n'est **jamais** retenu, même si un seuil tombe à 0.

### 9.8 Retrait des petits morceaux

On regroupe les pixels retenus qui se touchent et on **retire tout groupe de moins de
`aireMin` pixels** (défaut 150 px à l'échelle FIRE).

| Si on l'augmente | Si on le diminue |
| --- | --- |
| Masque plus propre, mais on perd des **bouts de vaisseaux fins détachés** du réseau. | Plus de petits morceaux, donc de faux points dans le squelette. 0 = pas de filtre. |

**À savoir :** une tache sombre ronde (microanévrisme, petite hémorragie) répond **presque
aussi fort qu'un vaisseau** (57 % dans notre test) : λ2 y est aussi positif. Seul ce
filtre d'aire retire les taches **isolées**. Une tache collée à un vaisseau reste et fera
une barbe, que l'élagage du squelette devra enlever.

### 9.9 Paramètres d'échelle : `sigmas` et `facteurEchelle`

**`segmentation.sigmas`** (défaut `[2, 3, 4, 6, 8]`, en pixels FIRE) :

| Changement | Effet |
| --- | --- |
| Ajouter σ = 1 ou 1,5 | Vaisseaux très fins mieux vus, mais plus de bruit. |
| Ajouter σ = 12 | Utile si les gros troncs sont **creux ou dédoublés** (deux traits parallèles). Plus lent. |
| Retirer des σ | Plus rapide (le temps suit à peu près la somme des σ), mais trous possibles dans la gamme des largeurs. |

**`segmentation.facteurEchelle`** (défaut 1,0) multiplie tous les σ, et l'aire minimale par
son carré, pour passer d'un jeu à l'autre :

```
facteurEchelle = ppd du jeu / ppd de FIRE
ppd = diamètre du champ en pixels / angle du champ en degrés      (FIRE : ≈ 2710 / 45 ≈ 60)
```

Un jeu à 30 ppd aurait un facteur de 0,5 : σ = 1 à 4, aire minimale ≈ 38 px. Solution
d'attente avant les tailles en degrés de la décision D4.

### 9.10 Comparaison prévue avec le filtre de Gabor

Le protocole demande de comparer le Hessien au **filtre de Gabor** (chapitre 2) : dès
maintenant si le Dice reste sous 0,65, sinon au Point 3. Un filtre de Gabor est une petite
onde orientée ; on le tourne dans plusieurs directions et on garde la meilleure réponse.
Il est souvent plus robuste au bruit, mais plus lent et avec plus de réglages (fréquence,
nombre d'orientations).

### 9.11 Ce que donne la segmentation aujourd'hui

Testé sur A01, S01, P01, S20, P30 :

- **Paires normales (S01, P30)** : réseau continu, petits vaisseaux détectés, rien au bord.
- **A01 (nombreux exsudats)** : plus d'anneaux autour des exsudats isolés, mais un **réseau
  de faux positifs dans la fovéa**, entre les exsudats regroupés (vrais creux sombres).
- **Petites barbes** le long des vaisseaux.

**Pas encore mesuré :** le Dice sur HRF (objectif ≥ 0,65). Les seuils sont des valeurs de
départ, à régler sur les 15 images HRF de réglage.

---

## 10. Squelette (Zhang-Suen) 🔨 Point 2

**Classe prévue :** `squelette/AmincissementZhangSuen` (Elnis) · **Décision :** D3
**Entrée :** masque des vaisseaux · **Sortie :** squelette (lignes d'un pixel d'épaisseur)

### Pourquoi

Pour trouver où les vaisseaux se séparent, il faut d'abord les réduire à leur **ligne
centrale**, d'un pixel d'épaisseur : le squelette. Sur un vaisseau épais de 10 px, on ne
peut pas dire où est « le point » de bifurcation ; sur le squelette, si.

### Comment : l'amincissement

L'algorithme **ronge le masque couche par couche**, comme on épluche un oignon, en ne
retirant que les pixels dont la disparition **ne coupe pas** la forme et **ne raccourcit
pas** ses extrémités. Il s'arrête quand plus aucun pixel ne peut être retiré.

Pour chaque pixel P1 du masque, on regarde ses 8 voisins, numérotés P2 à P9 en tournant
autour de lui à partir du voisin du haut :

```
 P9  P2  P3
 P8  P1  P4
 P7  P6  P5
```

- **B** = nombre de voisins allumés (de 0 à 8) ;
- **A** = nombre de passages « éteint → allumé » en faisant le tour P2, P3, …, P9, P2.

À chaque itération, deux sous-passes. Un pixel est retiré si :

| Condition | Sens |
| --- | --- |
| 2 ≤ B ≤ 6 | ni une extrémité (B = 1), ni un pixel intérieur plein (B ≥ 7) |
| A = 1 | ses voisins allumés forment un seul bloc : le retirer ne coupe rien |
| Sous-passe 1 : P2·P4·P6 = 0 et P4·P6·P8 = 0 | ronge les bords sud et est |
| Sous-passe 2 : P2·P4·P8 = 0 et P2·P6·P8 = 0 | ronge les bords nord et ouest |

On alterne les deux sous-passes pour que le squelette reste **centré**. Tous les pixels à
retirer dans une sous-passe sont marqués d'abord, puis retirés ensemble.

### Pourquoi codé nous-mêmes (D3)

Fiji propose `AnalyzeSkeleton`, mais le cœur du traitement ne doit pas dépendre d'ImageJ
pour servir au logiciel autonome. Zhang-Suen tient en une centaine de lignes.

### Tests prévus

Une ligne, un Y, une croix et une image non carrée de 7 × 13 px.

### Paramètres

Aucun. Mais **la qualité du squelette dépend entièrement de la segmentation** :

| Défaut du masque | Effet sur le squelette |
| --- | --- |
| Trou à l'intérieur d'un vaisseau | Une boucle au lieu d'une ligne, donc deux fausses bifurcations |
| Barbe de bruit sur le bord | Une petite branche parasite, donc une fausse bifurcation |
| Vaisseau coupé | Deux morceaux séparés, une bifurcation perdue |

---

## 11. Bifurcations et croisements 🔨 Point 2

**Classe prévue :** `bifurcation/DetecteurBifurcations` (Elnis)
**Entrée :** squelette · **Sortie :** liste de points `(x, y, type)`

### 11.1 Le nombre de croisements (crossing number)

Pour chaque pixel du squelette, on fait le tour de ses 8 voisins P1 à P8 (avec P9 = P1) et
on compte les changements allumé/éteint :

```
CN = ½ × Σ |Pi − Pi+1|      pour i = 1 à 8
```

| CN | Sens |
| --- | --- |
| 1 | extrémité d'un vaisseau |
| 2 | point ordinaire le long d'un vaisseau |
| **3** | **bifurcation** (le vaisseau se sépare en deux) |
| **4** | **croisement** (deux vaisseaux se croisent) |

### 11.2 Nettoyage, dans l'ordre

| Étape | Pourquoi | Paramètre (départ sur FIRE) |
| --- | --- | --- |
| Fusionner les détections voisines | Au même endroit, le squelette peut donner 2 ou 3 pixels avec CN = 3 | distance de fusion *(proposition : 3 px)* |
| **Élaguer** les petites branches | Une barbe crée une branche courte terminée par une extrémité : on la supprime | **12 px** |
| Regrouper deux bifurcations très proches en **un croisement** | Un croisement de deux vaisseaux apparaît souvent dans le squelette comme deux bifurcations décalées | **8 px** |
| Écarter les points trop près du bord | Leur fenêtre de Zernike sortirait du champ de vue | rayon R de Zernike |

### 11.3 Impact des paramètres

| Paramètre | Trop petit | Trop grand |
| --- | --- | --- |
| Longueur d'élagage (12 px) | Les barbes restent : fausses bifurcations | On coupe de vraies petites branches : vraies bifurcations perdues |
| Distance de croisement (8 px) | Un croisement compte pour deux bifurcations | Deux vraies bifurcations voisines sont fusionnées en un faux croisement |

### 11.4 Test de stabilité

Tourner une image de 30°, relancer toute la chaîne, et compter les bifurcations retrouvées
à moins de 3 px de leur position attendue. **Objectif : au moins 80 %.** Sinon, corriger le
squelette et l'élagage avant de passer au descripteur.

### 11.5 Sortie

Fichier `bifurcations/<paire>_<visite>.csv` avec les colonnes `x, y, type`, coordonnées
selon D1 (centre du pixel à +0,5), dans `resultats/point2/` (décision D6).

---

## 12. Descripteur de Zernike 🔨 Point 2

**Classe prévue :** descripteur de Zernike (Powell)
**Entrée :** image prétraitée + liste de bifurcations · **Sortie :** un vecteur de nombres par bifurcation

### 12.1 Pourquoi un descripteur

Pour savoir que la bifurcation n° 12 de la visite 1 est la même que la n° 37 de la
visite 2, il faut **décrire** chacune par quelques nombres qu'on peut comparer. Problème :
entre deux visites, l'œil a pu **tourner** et l'éclairage changer. Le descripteur doit
donc donner les mêmes nombres même si l'image a tourné.

### 12.2 L'idée

On découpe autour de chaque bifurcation une **fenêtre ronde** de rayon R, et on la décompose
sur une famille de « motifs de base », les **polynômes de Zernike**, un peu comme on
décompose un son en notes. Chaque moment `Z(n, m)` dit « combien » de chaque motif la
fenêtre contient.

- **n** (l'ordre) fixe la finesse du motif : petit n = formes grossières, grand n = détails ;
- **m** fixe le nombre de « tours » du motif autour du centre.

```
          n + 1
Z(n,m) = ─────── × Σ   f(x, y) × R(n,m)(ρ) × e^(−j m θ)
          π R²    ρ≤1
```

où `(ρ, θ)` sont les coordonnées polaires du pixel par rapport au centre (ρ = distance / R,
entre 0 et 1), et `R(n,m)` est un polynôme radial :

```
             (n−m)/2        (−1)^s × (n − s)!
R(n,m)(ρ) =    Σ     ──────────────────────────────── × ρ^(n − 2s)
              s=0     s! × ((n+m)/2 − s)! × ((n−m)/2 − s)!
```

### 12.3 Pourquoi c'est insensible à la rotation

Si la fenêtre tourne d'un angle α, chaque moment est simplement multiplié par `e^(−j m α)`,
un nombre complexe de **module 1**. Son **module** `|Z(n,m)|` ne change donc pas. **On ne
garde que les modules.**

### 12.4 Pas à pas

1. Découper la fenêtre ronde de rayon R, centrée sur la bifurcation (convention D1).
2. **Normaliser** : retirer la moyenne de la fenêtre et diviser par son écart-type. Une
   visite plus claire ou plus contrastée donne alors la même fenêtre normalisée.
3. Calculer chaque `Z(n,m)` pour n de 1 à l'ordre maximal, m ≥ 0 et n − m pair.
4. Garder les modules : c'est le vecteur descripteur.

### 12.5 Paramètres

| Paramètre | Valeur | Effet |
| --- | --- | --- |
| **Ordre maximal** | 4 au Point 2 (**8 valeurs**), 8 testé au Point 3 (**24 valeurs**) | Plus haut : le descripteur distingue mieux les bifurcations entre elles, mais devient plus sensible au bruit et aux petits défauts de centrage. Plus lent. |
| **Rayon R** | à tester : **32, 48 et 64 px** sur FIRE | Petit R : on ne voit que le « Y » de la bifurcation, et beaucoup de bifurcations se ressemblent. Grand R : on voit aussi les vaisseaux voisins, ce qui rend la fenêtre plus unique, mais elle déborde plus souvent du champ (plus de points écartés) et supporte moins bien les déformations entre visites. |

Comptage des valeurs : à l'ordre 4, les couples (n, m) sont (1,1), (2,0), (2,2), (3,1),
(3,3), (4,0), (4,2), (4,4), soit 8 valeurs.

### 12.6 Tests prévus

- Fenêtre tournée de 30° et de 45° : **écart inférieur à 5 %** entre les descripteurs.
- Reconstruction d'une petite image à partir de ses moments : l'erreur doit **baisser
  quand l'ordre monte** (preuve que les moments sont bien calculés).

### 12.7 Sortie

`descripteurs/<paire>_<visite>_R<rayon>.csv` avec les colonnes `x, y, z1 … zk`.

**Plan B** si Zernike échoue aux tests le 11/11 : comparer directement les fenêtres par
corrélation, en l'annonçant comme solution provisoire.

---

# Partie C — Aligner les deux visites

## 13. Appariement des bifurcations 🔨 Point 2

**Classe prévue :** `appariement/ApparieurCorrelation` (Elnis)
**Entrée :** descripteurs des deux visites · **Sortie :** liste de couples `(bifurcation visite 1, bifurcation visite 2, score)`

### 13.1 Le score : coefficient de corrélation

Pour chaque bifurcation `a` de la référence et chaque bifurcation `b` du suivi, on compare
leurs vecteurs descripteurs avec le **coefficient de corrélation** :

```
         Σ (aᵢ − moyenne(a)) × (bᵢ − moyenne(b))
r = ──────────────────────────────────────────────
     √(Σ (aᵢ − moyenne(a))²) × √(Σ (bᵢ − moyenne(b))²)
```

r va de −1 à 1 : **1 = mêmes formes**, 0 = aucun rapport. Il ne dépend pas de l'échelle des
valeurs, ce qui ajoute de la robustesse aux différences de contraste.

### 13.2 L'appariement croisé

On ne garde un couple `(a, b)` que si **a choisit b** comme meilleur score **et b choisit
a** comme meilleur score. Cela élimine beaucoup d'erreurs : une bifurcation ordinaire qui
ressemble vaguement à tout le monde n'est le « meilleur choix mutuel » de personne.

### 13.3 Le seuil de décision

Parmi les couples mutuels, on ne garde que ceux dont le score dépasse un **seuil**. Ce
seuil n'est pas choisi au hasard : il vient des **distributions GM et IM** mesurées par le
binôme B (section 17.3), au point d'**EER** où fausses acceptations et faux rejets sont au
même niveau.

| Si le seuil monte | Si le seuil baisse |
| --- | --- |
| Moins de faux couples, mais moins de couples tout court : risque de passer sous les 6 nécessaires à RANSAC. | Plus de couples, mais plus de faux : RANSAC doit en écarter davantage. |

### 13.4 Pas de limite de distance au Point 2

On ne limite pas la recherche aux bifurcations proches : sur les paires P (faible
recouvrement), le déplacement peut être grand et un rayon trop court éliminerait de bonnes
correspondances. Le binôme B mesurera le déplacement maximal réel par catégorie ; un rayon
limité sera essayé au Point 3.

### 13.5 Objectif et sortie

**Objectif :** au moins 70 % des paires S avec **au moins 6 bonnes correspondances**.
Sortie : `appariements/<paire>.csv` avec `x_ref, y_ref, x_suivi, y_suivi, score`.

---

## 14. Estimation de la transformation (RANSAC) 📅 Point 3

**Entrée :** couples appariés · **Sortie :** la transformation qui envoie la visite 1 sur la visite 2

### 14.1 Les modèles de transformation

| Modèle | Ce qu'il permet | Inconnues | Couples minimum |
| --- | --- | --- | --- |
| **Rigide** | rotation + déplacement | 3 | 2 |
| Similarité *(plan B)* | rigide + changement d'échelle | 4 | 2 |
| **Affine** | similarité + étirement et cisaillement | 6 | 3 |

Exemple, la transformation affine envoie le point `(x, y)` en :

```
x' = a·x + b·y + c
y' = d·x + e·y + f
```

On essaie d'abord le **rigide** (le plus simple, le plus stable), puis l'**affine** (plus
souple, donc potentiellement plus précis, mais plus sensible aux erreurs). Le protocole
garde les deux, puisque la problématique porte sur la précision.

### 14.2 Le problème : les faux couples

Même après l'appariement croisé, une partie des couples est fausse. Un calcul direct par
moindres carrés sur tous les couples serait faussé par ces erreurs : un seul couple très
faux suffit à tordre le résultat.

### 14.3 RANSAC : voter pour la meilleure transformation

RANSAC (*RANdom SAmple Consensus*) répète :

1. **Tirer au hasard** le nombre minimal de couples (2 pour le rigide, 3 pour l'affine).
2. **Calculer** la transformation qui passe exactement par ces couples.
3. **Compter les couples d'accord** (les *inliers*) : ceux que cette transformation envoie
   à moins de `seuil` pixels de leur partenaire.
4. Garder la transformation qui a **le plus de couples d'accord**.

Puis on **recalcule** la transformation par moindres carrés (avec la bibliothèque Jama) sur
tous les couples d'accord de la meilleure. Les faux couples, qui ne sont d'accord avec
personne, ne votent jamais pour la bonne réponse.

**Combien d'essais ?** Si une proportion `w` des couples est bonne et qu'il en faut `s` par
tirage, le nombre d'essais `N` pour tomber au moins une fois sur un tirage tout bon avec
une probabilité `p` est :

```
N = log(1 − p) / log(1 − wˢ)
```

Exemple : 50 % de bons couples, modèle affine (s = 3), p = 0,99 → N ≈ 35 essais. Avec 30 %
de bons couples → N ≈ 169.

### 14.4 Paramètres *(propositions, à régler au Point 3)*

| Paramètre | Proposition | Si on l'augmente | Si on le diminue |
| --- | --- | --- | --- |
| Seuil d'accord | 5 à 10 px | Des faux couples proches sont acceptés et biaisent le calcul final | De bons couples légèrement imprécis sont rejetés ; trop peu d'accords |
| Nombre d'essais | 1000 (graine fixe) | Plus sûr, plus lent (mais ça reste rapide) | Risque de rater la bonne transformation |
| Accords minimum | 6 | Plus de paires déclarées en échec | Transformations peu fiables acceptées |

### 14.5 À montrer au Point 3

MRE par catégorie, **avec et sans RANSAC**, pour prouver son utilité.

---

## 15. Image recalée (interpolation bilinéaire) 📅 Point 3

**Entrée :** image de la visite 1 + transformation · **Sortie :** visite 1 alignée sur la visite 2

### 15.1 Partir de l'image d'arrivée

Si on prenait chaque pixel de la visite 1 pour le « pousser » à sa nouvelle place, on
laisserait des trous. On fait donc l'inverse : pour **chaque pixel de l'image d'arrivée**,
on calcule avec la **transformation inverse** où il se trouvait dans la visite 1. Aucun
trou possible.

### 15.2 L'interpolation bilinéaire

Ce point d'origine tombe en général **entre** quatre pixels. On fait une moyenne de ces
quatre pixels, pondérée par la proximité :

```
 (x0,y0) ●────────● (x0+1,y0)        tx, ty = position du point entre 0 et 1
         │   ○    │                  valeur = (1−tx)(1−ty)·v00 + tx(1−ty)·v10
         │        │                         + (1−tx)ty·v01 + tx·ty·v11
 (x0,y0+1)●───────● (x0+1,y0+1)
```

Attention à la convention D1 : les positions des pixels sont leurs centres.

### 15.3 Ce qu'il faut savoir

L'interpolation **lisse légèrement** l'image (c'est une petite moyenne). Elle peut donc
affaiblir un microanévrisme de 2 ou 3 pixels. C'est pourquoi, dans la simulation de la
tolérance (section 17.4), la visite 1 est décalée **avec la même interpolation que le
logiciel**, y compris au décalage 0 (décaler puis remettre en place), pour que toutes les
mesures subissent le même lissage.

---

# Partie D — Détecter les changements

## 16. Détection des nouveaux microanévrismes 📅 Point 3 et 4

**Entrée :** visite 1 recalée + visite 2 · **Sortie :** carte des lésions nouvelles et disparues

### 16.1 La méthode principale : « différence puis détection »

1. **Normaliser les intensités** des deux visites (par exemple même moyenne et même
   écart-type dans le masque commun), pour qu'un changement d'éclairage ne passe pas pour
   une lésion.
2. **Différence** : `D = visite 1 recalée − visite 2`. Un microanévrisme apparu est sombre
   dans la visite 2 seulement : il donne une **tache positive** dans D. Une lésion disparue
   donne une tache négative.
3. **Laplacien de Gaussienne (LoG)** sur D : un filtre qui répond très fort aux **taches
   rondes d'une taille donnée**. C'est le même principe que le Hessien, mais avec la somme
   des deux courbures (`Ixx + Iyy`) : une tache ronde est courbée dans toutes les
   directions, un vaisseau dans une seule.
4. **Seuillage** de la réponse, puis regroupement en taches.
5. **Classement** de chaque tache : nouvelle (signe positif) ou disparue (signe négatif).

### 16.2 Le réglage du LoG

Le LoG répond le mieux à une tache de rayon r quand `σ = r / √2`, donc pour un diamètre D :

```
σ_LoG = D / (2 × √2) ≈ 0,35 × D
```

D est le **diamètre de référence d'un microanévrisme**, mesuré sur IDRiD par le binôme B
(section 17.4). Le seuil de détection n'est pas fixé une fois pour toutes : on le fait
varier pour tracer la courbe **FROC** (section 17.5).

### 16.3 La méthode de comparaison : « détecter puis comparer » (Point 4)

1. Détecter les microanévrismes **sur chaque visite** séparément.
2. Comparer les deux listes : une lésion de la visite 2 sans partenaire à moins d'un
   diamètre dans la visite 1 est **nouvelle**.

**Pourquoi la comparer ?** Avec la différence, une **lésion déjà présente** mal alignée
laisse une tache positive d'un côté et négative de l'autre : une fausse alerte. Si ces
lésions causent la plupart des faux positifs, la seconde méthode devrait mieux tolérer les
erreurs de recalage.

### 16.4 Ce qui crée des fausses alertes

Le protocole les classe par origine : **lésion déjà présente** (mal superposée), **bord de
vaisseau** (un vaisseau décalé de quelques pixels laisse un liseré dans la différence) et
**autre**. Ce classement dira quoi améliorer.

---

# Partie E — Mesurer

## 17. Les mesures d'évaluation

Chaque mesure répond à une question précise. Les mesures marquées ★ forment la réponse à la
problématique.

### 17.1 Dice : la segmentation est-elle bonne ? (HRF, Point 2)

On compare notre masque A aux vaisseaux tracés à la main B, **dans le champ de vue** :

```
Dice = 2 × |A ∩ B| / (|A| + |B|)
```

`|A ∩ B|` = nombre de pixels « vaisseau » dans les deux. 1 = identiques, 0 = rien en
commun. **Objectif : au moins 0,65** sur les 15 images de réglage.

### 17.2 Stabilité des bifurcations et invariance du descripteur (Point 2)

- **≥ 80 %** des bifurcations retrouvées à moins de 3 px après une rotation de 30°.
- **< 5 %** d'écart du descripteur de Zernike après rotation.

### 17.3 GM, IM, FMR, FNMR, EER : l'appariement est-il fiable ? (FIRE, Point 2)

**La vérité terrain.** FIRE ne dit pas quelles bifurcations se correspondent. Le binôme B
la reconstruit : avec les 10 points de contrôle de chaque paire, il calcule une
transformation affine (moindres carrés), projette chaque bifurcation de la référence sur
le suivi, et classe chaque couple :

| Distance entre le point projeté et la bifurcation du suivi | Classe |
| --- | --- |
| 10 px ou moins | **GM** (*genuine match*) : vrais correspondants |
| entre 10 et 50 px | ignoré, trop ambigu |
| 50 px ou plus | **IM** (*impostor match*) : non correspondants |

Les paires dont l'ajustement affine laisse une erreur de plus de 10 px sont exclues (et
comptées). On tire 5 couples IM pour 1 couple GM, avec une graine fixe.

**Les mesures**, pour chaque seuil possible sur le score de corrélation :

- **FMR** (*false match rate*) : part des couples IM **acceptés** à tort ;
- **FNMR** (*false non-match rate*) : part des couples GM **rejetés** à tort.

Monter le seuil fait baisser la FMR et monter la FNMR. L'**EER** est le point où les deux
sont égales : c'est le seuil donné au binôme A pour l'appariement. **Objectif : EER
inférieur à 25 %** sur les 34 paires de réglage. On trace aussi les histogrammes GM et IM :
plus ils sont séparés, meilleur est le descripteur.

### 17.4 ★ La tolérance T de la détection (IDRiD, Point 3 puis 4)

**Le diamètre de référence.**

1. Séparer les microanévrismes annotés d'IDRiD en taches.
2. Diamètre équivalent de chaque tache : `2 × √(aire / π)`.
3. Médiane : `D_IDRiD` en pixels.
4. Conversion pour FIRE : `D_FIRE = D_IDRiD × ppd_FIRE / ppd_IDRiD` (approximation : on
   suppose que tous les yeux ont la même taille).

**La simulation de deux visites** (aucune vraie paire n'a de lésions annotées) :

1. Répartir au hasard (graine fixe) les microanévrismes en deux moitiés : « nouveaux » et
   « déjà présents ».
2. Visite 2 = l'image d'origine.
3. Visite 1 = la même image, où les « nouveaux » sont **effacés** par remplissage à partir
   des pixels voisins (à vérifier à l'œil sur 10 lésions).
4. Décaler la visite 1 de `d` diamètres dans 4 directions : d = 0 ; 0,1 ; 0,25 ; 0,5 ;
   0,75 ; 1 ; 1,5 ; 2.

**Le comptage.** Une détection à moins d'un diamètre d'une lésion nouvelle est un **vrai
positif** ; toute autre est un **faux positif**.

**La définition de T.** T est le plus petit décalage où la **sensibilité, lue à 1 faux
positif par image**, perd **plus de 10 points** par rapport à d = 0 (contrôle à 4 faux
positifs par image ; marge d'erreur par *bootstrap*). Si T n'est pas atteint à d = 2, on
écrit « T > 2 diamètres ».

**Deux conditions :** *favorable* (seul le décalage change, Point 3) puis *réaliste*
(variation d'éclairage et bruit ajoutés, Point 4). La condition favorable donne une limite
haute : la vraie tolérance sera probablement plus faible.

### 17.5 La courbe FROC

Pour chaque seuil du détecteur, on note la **sensibilité** (part des lésions nouvelles
trouvées) et le **nombre moyen de faux positifs par image**. La courbe relie ces points :
plus elle monte vite à gauche, meilleur est le détecteur. On la trace pour chaque niveau
de décalage d ; la tolérance T se lit dessus.

### 17.6 ★ L'erreur de recalage (FIRE, Point 3 puis 4)

Pour chaque paire FIRE :

1. Transformer les 10 points de contrôle de la référence avec la transformation estimée.
2. Mesurer la distance à leur vraie position dans le suivi.
3. **MRE** (*mean registration error*) = moyenne de ces 10 distances, **divisée par
   `D_FIRE`** pour l'exprimer en diamètres.
4. Garder aussi l'**erreur maximale** : une moyenne correcte peut cacher un coin mal aligné.
5. **RMSE** en complément (chapitre 9) : racine de la moyenne des distances au carré ; elle
   pénalise davantage les grosses erreurs.

On résume par catégorie S, P, A, et on garde la courbe de réussite habituelle de FIRE pour
se comparer aux autres travaux.

### 17.7 ★ La réponse finale (Point 4)

Pour chaque catégorie de FIRE : **la part des paires dont le MRE reste sous T** (et la part
dont l'erreur maximale reste sous T), avec la marge d'erreur.

| Catégorie | Conditions | MRE médian (diamètres) | Paires sous T (MRE) | Paires sous T (erreur max) |
| --- | --- | --- | --- | --- |
| S | recouvrement > 75 % | Point 4 | Point 4 | Point 4 |
| P | recouvrement < 75 % | Point 4 | Point 4 | Point 4 |
| A | changements anatomiques | Point 4 | Point 4 | Point 4 |

### 17.8 Limites à annoncer

- T vient d'une simulation (aucune vraie paire n'a de lésions annotées).
- Le décalage simulé est constant, alors qu'une vraie erreur varie selon la zone.
- Le remplissage peut laisser une trace.
- Chaque jeu vient d'une seule caméra.
- L'outil aide à l'analyse, il ne pose pas de diagnostic.

---

# Annexes

## 18. Tableau récapitulatif des paramètres

| Étape | Paramètre | Défaut | Unité | Rôle | Sensibilité |
| --- | --- | --- | --- | --- | --- |
| Masque ✅ | `masqueChampVision.seuil` | 0,08 | intensité du rouge | sépare rétine et fond noir | faible |
| Masque ✅ | `masqueChampVision.erosion` | 7 | px | rogne la bande de transition | faible |
| Éclairage ✅ | `correctionEclairage.sigmaRelatif` | 0,05 | fraction de la largeur | taille du flou qui estime l'éclairage | moyenne |
| Contraste ✅ | `rehaussementContraste.percentileBas` | 1 | % | ce qui devient noir | faible |
| Contraste ✅ | `rehaussementContraste.percentileHaut` | 99 | % | ce qui devient blanc | faible |
| Médian ✅ | `filtreMedian.rayon` | 2 | px | force du débruitage | **forte pour la détection** |
| Segmentation ✅ | `segmentation.sigmas` | 2, 3, 4, 6, 8 | px FIRE | largeurs de vaisseaux cherchées | moyenne |
| Segmentation ✅ | `segmentation.facteurEchelle` | 1,0 | — | adapte les tailles à un autre jeu | forte si mal réglé |
| Segmentation ✅ | `segmentation.percentileEcretage` | 50 | % | aplatit les taches claires | moyenne |
| Segmentation ✅ | `segmentation.percentileHaut` | 94 | % | germes de l'hystérésis | **forte** |
| Segmentation ✅ | `segmentation.percentileBas` | 88 | % | extension de l'hystérésis | **forte** |
| Segmentation ✅ | `segmentation.aireMin` | 150 | px FIRE | taille minimale d'un morceau | moyenne |
| Bifurcations 🔨 | longueur d'élagage | 12 | px FIRE | supprime les barbes | forte |
| Bifurcations 🔨 | distance de croisement | 8 | px FIRE | fusionne deux bifurcations en croisement | moyenne |
| Zernike 🔨 | ordre maximal | 4 (puis 8) | — | finesse du descripteur | moyenne |
| Zernike 🔨 | rayon R | 32 / 48 / 64 | px FIRE | taille de la fenêtre | **forte** |
| Appariement 🔨 | seuil de score | à l'EER | corrélation | accepte ou rejette un couple | **forte** |
| RANSAC 📅 | seuil d'accord | *5–10* | px | tolérance d'un inlier | forte |
| RANSAC 📅 | nombre d'essais | *1000* | — | sûreté du vote | faible |
| Détection 📅 | σ du LoG | ≈ 0,35 × D | px | taille des taches cherchées | forte |

**À régler en priorité pour le Dice sur HRF :** `percentileHaut`, `percentileBas`, puis
`sigmas`.

---

## 19. Guide de réglage : symptôme → paramètre

| Ce qu'on voit | Cause probable | Que faire |
| --- | --- | --- |
| Anneau de faux vaisseaux autour du champ | Bande de transition dans le masque | Augmenter `erosion` ; vérifier que l'extérieur vaut 0 |
| Anneaux autour des taches claires | Pied des bosses vu comme un creux | Vérifier que `percentileEcretage` n'est pas à 100 ; essayer 40 |
| Petits vaisseaux absents | Seuils trop hauts, médian trop fort | Baisser `percentileHaut` / `percentileBas` ; ajouter σ = 1,5 ; baisser `filtreMedian.rayon` |
| Réseau coupé en morceaux | Écart trop faible entre les deux seuils | Baisser `percentileBas` |
| Petits points parasites | Bruit qui passe le seuil | Augmenter `aireMin` ou `percentileHaut` |
| Barbes le long des vaisseaux | Bruit collé aux vaisseaux | Monter un peu `percentileBas`, ou laisser l'élagage (12 px) |
| Gros vaisseaux creux ou dédoublés | σ trop petits | Ajouter σ = 12 |
| Tout trop épais ou trop fin sur un autre jeu | Mauvaise échelle | `facteurEchelle` = ppd du jeu / ppd de FIRE |
| Boucles dans le squelette | Trous dans les vaisseaux du masque | Revoir la segmentation (seuils, σ) avant le squelette |
| Trop de bifurcations, beaucoup fausses | Barbes, croisements comptés en double | Élagage plus long ; distance de croisement |
| Bifurcations instables après rotation | Squelette ou élagage | Corriger avant de passer à Zernike |
| Descripteur qui change à la rotation | Fenêtre mal centrée ou mal découpée | Vérifier le centre (D1) et la fenêtre ronde |
| Trop peu de couples (< 6) | Seuil d'appariement trop haut, R mal choisi | Baisser le seuil ; essayer un autre R |
| RANSAC instable d'une exécution à l'autre | Trop peu de bons couples, graine non fixée | Fixer la graine ; plus d'essais ; modèle rigide ou similarité |

**Méthode de réglage :** ne régler que sur les images de **réglage**, jamais sur celles de
test. Changer **un seul paramètre à la fois**. Noter à chaque essai la version du code, les
paramètres utilisés (`parametres_utilises.json`) et le résultat dans le tableau partagé.

---

## 20. Petit glossaire

| Mot | Sens simple |
| --- | --- |
| **Affine (transformation)** | Déplacement + rotation + changement d'échelle + étirement : les droites restent droites, les parallèles restent parallèles. |
| **Bifurcation** | Point où un vaisseau se sépare en deux. |
| **Bootstrap** | Estimer la marge d'erreur d'un résultat en le recalculant de nombreuses fois sur des tirages au hasard (avec remise) des images. |
| **Canal** | Une des trois couleurs (rouge, vert, bleu) d'une image, vue comme une image en gris. |
| **Champ de vue** | Le disque de rétine visible sur la photo. |
| **Composante connexe** | Groupe de pixels qui se touchent (4-connexité : par les côtés ; 8-connexité : aussi par les coins). |
| **Convolution normalisée** | Convolution qui ne fait la moyenne qu'avec les voisins valides (dans le masque). |
| **Corrélation (coefficient de)** | Nombre entre −1 et 1 qui dit si deux listes de nombres varient de la même façon. |
| **Courbure / dérivée seconde** | À quel point l'intensité se plie : positive au fond d'une vallée, négative au sommet d'une crête. |
| **Croisement** | Point où deux vaisseaux se croisent sans se rejoindre. |
| **Écrêtage** | Plafonner les valeurs à un niveau. |
| **Érosion** | Rogner une forme sur tout son pourtour. |
| **Exsudat** | Lésion claire (jaunâtre) : tache plus claire que le fond. |
| **Fovéa** | Petite zone centrale de la rétine, plus sombre, sans vaisseaux. |
| **Gaussienne** | Courbe en cloche, sert de noyau de flou ; sa largeur est σ. |
| **Hessien** | Tableau des dérivées secondes ; ses valeurs propres donnent les courbures principales. |
| **Hystérésis** | Seuillage à deux niveaux : sûr au-dessus du haut, accepté entre les deux seulement si relié à du sûr. |
| **Inlier / outlier** | Dans RANSAC : couple d'accord avec la transformation / couple faux. |
| **Interpolation** | Estimer la valeur entre des pixels connus. |
| **LoG** | Laplacien de Gaussienne : détecteur de taches rondes d'une taille donnée. |
| **Médiane** | Valeur du milieu d'une liste triée. |
| **Microanévrisme** | Lésion précoce : petite tache sombre ronde (moins de 125 µm). |
| **Moindres carrés** | Trouver les paramètres qui rendent la somme des erreurs au carré la plus petite possible. |
| **Moment de Zernike** | Mesure de « combien » une fenêtre ronde ressemble à un motif de base donné. |
| **Percentile** | Valeur sous laquelle se trouve un pourcentage donné des données. |
| **ppd** | Pixels par degré de champ visuel ; permet de comparer des jeux de résolutions différentes. |
| **Recalage** | Aligner deux images l'une sur l'autre. |
| **Sensibilité** | Part des vraies lésions que le détecteur trouve. |
| **Squelette** | Ligne centrale d'une forme, d'un pixel d'épaisseur. |
| **Valeur propre** | Pour le Hessien : la courbure dans une des deux directions principales. |
