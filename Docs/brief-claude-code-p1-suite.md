# Brief Claude Code — Point 1, chaîne de prétraitement et démo

Suite du brief précédent (`Docs/brief-claude-code-p1.md`). Les quatre types fondamentaux
sont écrits. **Échéance : présentation au professeur le lundi 19/10.**

Livrable attendu par le plan de travail, Binôme A :

> Implémenter le prétraitement : canal vert, masque du champ de vue, correction
> d'éclairage, contraste, filtre médian. Appliquer le prétraitement aux deux images
> d'une paire et afficher un avant/après côte à côte pour chacune.

C'est la démo qui est notée, pas la couverture de tests. En cas d'arbitrage de temps,
la chaîne qui tourne de bout en bout passe avant tout le reste.

---

## Tâche 0 — Assainir le build (à faire en premier, ~20 min)

Trois anomalies constatées dans `target/`, à corriger avant d'écrire une ligne.

**0.1 — Classes fantômes dans le jar.** `src/main/java/bj/epac/retine/image/` a été
supprimé, mais `target/classes/bj/epac/retine/image/` contient encore les `.class` de
l'ancien `ImageGris` mutable, et le jar a été empaqueté depuis ce répertoire. Le rapport
JaCoCo liste les deux packages. Maven ne supprime jamais un `.class` orphelin.

```bash
mvn clean test
unzip -l retine-noyau/target/retine-noyau-0.1.0-SNAPSHOT.jar | grep -E 'ImageGris|Masque'
```

Le `grep` ne doit montrer qu'un seul `ImageGris.class`, sous `noyau/image/`. Reporter le
nombre de tests exécutés **après** `clean` : c'est le seul chiffre qui compte, et le seul
qu'un coéquipier obtiendra en clonant.

**0.2 — Même problème côté tests.** `bj.epac.retine.image.ImageGrisTest` apparaît encore
dans `surefire-reports` alors que sa source est supprimée. Le `clean` règle ça ; vérifier
que le rapport a disparu.

**0.3 — `ImageCouleur` n'a aucun test.** Créer `ImageCouleurTest` avec les mêmes cas que
`ImageGrisTest` : bornes sur les trois canaux, immuabilité du constructeur copiant,
`enveloppe` sans copie, et surtout **un test non-carré (7×13) qui échoue si `x` et `y`
sont inversés dans l'un des trois canaux**. Trois tableaux, trois occasions d'inverser
l'indexation, et aucun test ne le détecterait aujourd'hui. Plus un test de `canalVert()` :
sur une image dont les trois canaux diffèrent, vérifier que c'est bien le vert qui sort.

---

## Décisions tranchées (ne pas rouvrir, les appliquer)

**D1 — Convention de coordonnées pixel.** Le centre du pixel `(x, y)` est en
`(x + 0.5, y + 0.5)`. À appliquer dans `Masque.cercle` (un pixel est actif si
`(x+0.5-cx)² + (y+0.5-cy)² ≤ rayon²`) et dans toute future interpolation. À écrire en
Javadoc sur `Masque.cercle` et dans `Docs/decisions.md`. Raison : les points de contrôle
de FIRE et le centrage de la fenêtre de Zernike dépendent de cette convention ; un
décalage d'un demi-pixel se retrouverait comme biais systématique dans le RMSE, sans
être détectable par la suite.

**D2 — Sortie 32 bits.** `AdaptateurImageJ` rejette aujourd'hui les images 32 bits.
Ajouter :
- `static ImagePlus versImagePlus32bits(ImageGris img, String titre)` — écrit les valeurs
  en `float` sans quantification,
- l'acceptation du 32 bits en entrée dans `versGris` (pas de division, les valeurs sont
  déjà des flottants).

Raison : après correction d'éclairage, les valeurs sortent de [0,1] et ne sont plus
entières. Sauvegarder un intermédiaire en 8 bits le quantifie sur 256 niveaux et l'étape
suivante repart d'une version dégradée. Le 8 bits reste pour l'affichage et les figures.

Les piles multi-tranches et les images indexées restent rejetées.

---

## Tâche 1 — Les cinq étapes de prétraitement

Les stubs existent dans `bj.epac.retine.pretraitement` et lèvent
`UnsupportedOperationException`. **Se conformer aux signatures déjà présentes**
(`Etape`, `ContexteExecution`, `Parametres`) plutôt que de les redéfinir. Si une
signature existante rend une étape impossible à écrire proprement, **s'arrêter et le
signaler** au lieu de modifier l'interface unilatéralement : elle est partagée avec
le binôme B.

Règles communes, déjà fixées : aucune modification en place, chaque étape alloue sa
sortie et la rend via `ImageGris.enveloppe(...)` ; accumulateurs en `double` ;
valeurs normalisées [0,1] ; tout paramètre numérique vient de `Parametres`, aucune
constante magique dans le code.

### 1.1 `CanalVert` — `ImageCouleur → ImageGris`

Appelle `ImageCouleur.canalVert()`. L'étape existe pour porter la justification
méthodologique (chapitre 8 : meilleur contraste vaisseaux/fond dans le vert), pas pour
calculer. Javadoc explicite là-dessus.

### 1.2 `MasqueChampVision` — `ImageCouleur → Masque`

Le fond d'œil est un disque clair sur fond noir. Approche :

1. Seuiller sur la luminance (ou sur le canal rouge, le plus lumineux dans le disque)
   avec un seuil bas, paramétré, de l'ordre de 0,05–0,1.
2. Garder la plus grande composante connexe, pour éliminer les reflets isolés et le
   texte éventuellement incrusté dans un coin.
3. **Éroder de N pixels** (N paramétré, de l'ordre de 5–10). C'est l'étape à ne pas
   sauter : le bord du champ de vue est une transition progressive, et sans érosion
   l'anneau de transition se comportera comme une structure tubulaire que la détection
   de vaisseaux prendra pour un vaisseau, sur toute la circonférence.
4. Calculer le centre et le rayon équivalent (`r = sqrt(aire/π)`) et les stocker comme
   métadonnée du `Masque` — ils serviront d'initialisation grossière au recalage.

Retourner un `Masque` par `boolean[]`, pas un cercle idéal : le champ de vue réel est
souvent tronqué en haut et en bas par le capteur.

### 1.3 `CorrectionEclairage` — `ImageGris + Masque → ImageGris`

Soustraction du fond estimé par lissage gaussien de grand rayon (chapitre 5,
chapitre 1 pour la justification).

**Le piège à traiter explicitement :** un flou gaussien ordinaire fait entrer le noir
extérieur dans l'estimation du fond et produit un halo sur tout le pourtour du champ
de vue. Utiliser la convolution normalisée :

```
fond(x,y) = flou(I · M)(x,y) / flou(M)(x,y)      pour M(x,y) vrai
I_corrigée = I - fond + moyenne(I dans M)
```

où `M` est le masque vu comme 0/1 et `flou` un gaussien séparable (deux passes 1D,
sigma paramétré, de l'ordre de 1/15 à 1/30 de la largeur de l'image). Hors masque, la
sortie est mise à 0.

Les valeurs sortent de [0,1] après cette étape, c'est normal et c'est la raison de D2.

### 1.4 `RehaussementContraste` — `ImageGris + Masque → ImageGris`

Étirement linéaire sur percentiles (chapitre 3 : traitement ponctuel, histogramme,
contraste), percentiles bas et haut paramétrés, par défaut 1 % et 99 %.

**Les percentiles se calculent uniquement sur les pixels à l'intérieur du masque.** Avec
le fond noir inclus, le percentile bas tombe à 0 et l'étirement ne fait plus rien. C'est
la même règle que pour les métriques du projet, et elle vaut partout : *tout ce qui se
mesure se mesure dans le masque*.

Sortie ramenée dans [0,1], hors masque à 0.

### 1.5 `FiltreMedian` — `ImageGris + Masque → ImageGris`

Médian de rayon paramétré (1 ou 2 par défaut), chapitre 5. Implémentation naïve
acceptable à ce rayon (9 ou 25 valeurs, tri par insertion).

Au bord du masque, ne prendre dans la fenêtre que les voisins **à l'intérieur du
masque**, et calculer la médiane sur ce sous-ensemble. Ne pas compléter par des zéros :
ce serait réintroduire le bord noir que `MasqueChampVision` vient d'éroder. Hors masque,
sortie à 0.

### Ordre de la chaîne

`CanalVert → MasqueChampVision → CorrectionEclairage → RehaussementContraste →
FiltreMedian`, conforme au plan de travail. (`MasqueChampVision` consomme l'image
couleur d'origine, pas la sortie de `CanalVert`.)

### Tests des étapes

Pas de vérité terrain disponible : tester les **invariants**, pas les valeurs.

- Chaque étape renvoie une image aux mêmes dimensions que son entrée.
- Chaque étape laisse son entrée inchangée (comparer `donneesBrutes()` avant/après).
- Sur une image synthétique uniforme à l'intérieur d'un disque, `MasqueChampVision`
  retrouve le disque à l'érosion près, et le centre estimé est proche du centre réel.
- Sur une image synthétique avec un gradient d'éclairage linéaire ajouté,
  `CorrectionEclairage` réduit l'écart-type intra-masque d'au moins un facteur 2.
- `RehaussementContraste` sur une image dont la dynamique utile est [0.4, 0.6] ressort
  avec min ≈ 0 et max ≈ 1 dans le masque.
- `FiltreMedian` sur une image constante avec un pixel aberrant supprime l'aberration.

---

## Tâche 2 — La commande ImageJ avant/après

C'est le livrable visible. Compléter `RDR_Pretraitement` (ou ajouter une commande
dédiée déclarée dans `plugins.config`) :

1. Charger les deux images d'une paire FIRE (référence + suivi) — utiliser `PaireImages`
   et `JeuFire` s'ils le permettent déjà ; sinon, un sélecteur de deux fichiers suffit.
2. Appliquer la chaîne complète aux deux.
3. Afficher, **pour chacune des deux images**, l'originale et la prétraitée côte à côte.
   Un `ImageStack` à deux tranches ou un montage via `ij.plugin.MontageMaker` fait
   l'affaire ; inutile de construire une interface.
4. Les paramètres viennent du fichier JSON via `LecteurParametres`, avec des valeurs par
   défaut utilisables si le fichier est absent — la démo ne doit pas dépendre d'un
   fichier de config bien rempli.

Vérifier la commande dans Fiji, pas seulement en test : jar du noyau dans
`Fiji.app/jars/`, jar du plugin dans `Fiji.app/plugins/`, relance, menu `Plugins > RDR`.

---

## Ordre d'exécution

0. Tâche 0 (build propre + `ImageCouleurTest`).
1. D1 et D2 appliquées.
2. `CanalVert`, `MasqueChampVision` — les deux dont tout le reste dépend.
3. Tâche 2 avec ces deux étapes seulement, pour que la démo existe tôt même incomplète.
4. `CorrectionEclairage`, `RehaussementContraste`, `FiltreMedian`, ajoutées à la chaîne
   au fur et à mesure.
5. Tests d'invariants.

Commiter après chaque étape qui compile et passe, pas en un seul commit final.

## Hors périmètre

Segmentation des vaisseaux, Hessien, Gabor, bifurcations, Zernike, RANSAC, mode batch,
aperçu temps réel, CLAHE. Rien de tout cela n'est attendu au Point 1.

## À signaler plutôt qu'à décider seul

- Une signature existante de `Etape`, `ContexteExecution`, `Parametres`, `PaireImages`
  ou `JeuFire` qui ne permet pas d'écrire une étape proprement.
- Une image FIRE dont le champ de vue n'est pas détecté par le seuillage (cas tronqué,
  très sombre) — le signaler avec le nom du fichier plutôt que d'ajuster le seuil par
  défaut jusqu'à ce que ça passe.
- Tout besoin d'une dépendance nouvelle.
