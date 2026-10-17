# Brief Claude Code — Point 1, types fondamentaux

Projet : suivi de la rétinopathie diabétique sans IA. Module `retine-noyau` (Java pur,
aucune dépendance ImageJ) et `retine-plugin` (adaptateur ImageJ).

## Périmètre de cette tâche

Implémenter **quatre classes et leurs tests**, rien de plus.

- `bj.epac.retine.noyau.image.ImageGris`
- `bj.epac.retine.noyau.image.ImageCouleur`
- `bj.epac.retine.noyau.image.Masque`
- `bj.epac.retine.plugin.AdaptateurImageJ`

**Hors périmètre, à ne pas créer :** étapes de prétraitement, interface `Stage<I,O>`,
segmentation, descripteurs, commandes de menu supplémentaires, dépendances nouvelles
dans les `pom.xml`. Ne toucher à aucun fichier sous `Donnees/` ou `resultats/`.

## Décisions d'architecture à respecter (non négociables)

1. **Stockage `float[]`, API scalaire `double`.** Un seul tableau `float[]` par image.
   `get(x,y)` retourne un `double` (promotion gratuite et sans perte). Les accumulateurs
   de calcul seront des `double` locaux — règle : *`float` pour stocker, `double` pour calculer*.

2. **Plage de valeurs normalisée 0–1.** Toute image entrant dans le noyau est ramenée
   à [0,1]. Les seuils du projet deviennent ainsi indépendants du format source
   (FIRE 8 bits, IDRiD autre dynamique).

3. **Immuabilité totale.** `ImageGris`, `ImageCouleur` et `Masque` sont des classes
   `final`, tous leurs champs `private final`, aucun mutateur. Une étape ne modifie
   jamais son entrée : elle alloue un tableau vide, le remplit en lisant la source,
   et l'emballe au retour. Pas de `set` public.

4. **Deux voies de construction.**
   - Constructeur public : **copie** le tableau reçu (sûr par défaut ; utilisé par
     l'adaptateur et les tests).
   - Fabrique statique `enveloppe(float[] donnees, int largeur, int hauteur)` :
     **cède la propriété**, sans copie. Javadoc explicite : l'appelant ne doit plus
     toucher son tableau après l'appel. Réservée aux boucles internes du noyau.

5. **Accès.** `get(x,y)` / `getIndex(i)` comme API par défaut. Plus
   `donneesBrutes()` retournant le tableau interne, documenté **lecture seule**,
   réservé aux boucles chaudes (convolution, Gabor, Hessien). Classes `final` et pas
   d'interface, pour que la JVM continue d'inliner `get`.

6. **Politique de bord.** `get(x,y)` hors limites lève `IndexOutOfBoundsException`
   avec un message nommant les coordonnées et les dimensions. Aucun retour silencieux
   à zéro, aucun miroir implicite : la gestion de bord sera explicite dans chaque
   convolution, plus tard.

7. **Indexation.** Toujours `y * largeur + x`. Une seule méthode privée `index(x,y)`
   dans chaque classe, utilisée partout, pour qu'aucun site d'appel ne puisse inverser
   ligne et colonne.

8. **Masque séparé de l'image.** `Masque` n'est pas un champ d'`ImageGris` : une image
   peut avoir plusieurs masques (champ de vue, zones exclues, région d'intérêt). Il
   circulera dans le contexte d'exécution.

## Spécifications

### `ImageGris`

Champs : `float[] pixels`, `int largeur`, `int hauteur`, tous `final`.

- `ImageGris(float[] pixels, int largeur, int hauteur)` — copie, vérifie que
  `pixels.length == largeur * hauteur`, rejette les dimensions ≤ 0.
- `static ImageGris enveloppe(float[] pixels, int largeur, int hauteur)` — sans copie.
- `static ImageGris vide(int largeur, int hauteur)`.
- `double get(int x, int y)` — avec contrôle de bornes.
- `double getIndex(int i)`.
- `int largeur()`, `int hauteur()`, `int nombrePixels()`.
- `float[] donneesBrutes()` — Javadoc : lecture seule, ne pas écrire.
- `boolean memesDimensions(ImageGris autre)`.
- `double min()`, `double max()` — utiles pour les tests et la normalisation.

### `ImageCouleur`

Trois canaux `float[] rouge, vert, bleu`, mêmes dimensions, mêmes règles
(constructeur copiant, `enveloppe`, accès `getRouge(x,y)` etc., accesseurs bruts
en lecture seule). Plus `ImageGris canalVert()` qui retourne une vue copiée du canal
vert — simple extraction, sans traitement : la justification méthodologique du choix
du canal vert appartient à l'étape `CanalVert` du noyau, qui viendra plus tard et
consommera cette méthode.

### `Masque`

Champs : `boolean[] valeurs`, `int largeur`, `int hauteur`, `final`.

- Constructeur copiant + `enveloppe(...)`.
- `static Masque cercle(int largeur, int hauteur, double cx, double cy, double rayon)`
  — fabrique pour le champ de vue circulaire ; conserve `cx`, `cy`, `rayon` en
  métadonnée accessible (le centre du champ de vue servira d'initialisation grossière
  au recalage), avec `boolean aMetadonneeCercle()` puisque tous les masques n'en ont pas.
- `static Masque plein(int largeur, int hauteur)`.
- `boolean estDans(int x, int y)` — contrôle de bornes comme `ImageGris`.
- `int nombrePixelsActifs()`.
- `boolean[] donneesBrutes()` — lecture seule.

Pas d'érosion ni d'opération morphologique dans cette tâche : ce sera une étape du noyau.

### `AdaptateurImageJ` (module `retine-plugin`)

Transport pur, aucun traitement, aucune décision méthodologique.

- `static ImageCouleur versCouleur(ImagePlus imp)` — pour un `ColorProcessor`.
- `static ImageGris versGris(ImagePlus imp)` — pour les images 8 ou 16 bits.
  Sur une image RGB, **lever une exception** avec un message renvoyant vers
  `versCouleur` : fusionner les canaux ici reviendrait à prendre à la place du noyau
  la décision du canal vert.
- `static ImagePlus versImagePlus(ImageGris img, String titre)` — dénormalise vers 8 bits.
- `static ImagePlus versImagePlus(ImageCouleur img, String titre)`.
- `static ImagePlus versImagePlus(Masque masque, String titre)` — 0 / 255, pour contrôle visuel.

Normalisation : division par 255 (8 bits) ou 65535 (16 bits) à l'entrée ; à la sortie,
multiplication, `Math.round`, puis bornage dans [0,255]. Le bornage est obligatoire :
après correction d'éclairage les valeurs pourront sortir de [0,1].

## Tests (JUnit, `src/test/java`)

L'aller-retour exact est le critère qui valide toute la tâche.

1. **Aller-retour 8 bits exact.** Image synthétique contenant les 256 valeurs
   possibles → `ImageGris` → `ImagePlus` → comparaison pixel à pixel avec l'original.
   Égalité stricte attendue, pas une tolérance.
2. **Aller-retour RGB exact**, même principe via `ImageCouleur`.
3. **Non-carré.** Test sur une image 7×13 avec des valeurs distinctes à chaque position,
   qui échoue si `x` et `y` sont inversés quelque part. Les images FIRE étant carrées,
   une transposition y passerait inaperçue — ce test est le seul garde-fou.
4. **Immuabilité du constructeur copiant.** Construire depuis un tableau, modifier
   ensuite le tableau source, vérifier que l'image n'a pas changé.
5. **`enveloppe` ne copie pas** — vérifier que `donneesBrutes()` est bien la même
   référence que le tableau passé.
6. **Bornes.** `get(-1,0)`, `get(largeur,0)`, `get(0,hauteur)` lèvent bien l'exception.
7. **`Masque.cercle`** : un point au centre est dedans, un point au coin est dehors,
   et `nombrePixelsActifs()` approche πr² à quelques pourcents près.
8. **`versGris` sur une image RGB** lève bien l'exception attendue.

## Critères d'acceptation

- `mvn package` passe à la racine, sans warning nouveau.
- Les huit tests passent.
- `retine-noyau` ne contient aucun `import ij.` (le `pom.xml` doit déjà l'interdire —
  le vérifier, ne pas le contourner).
- Javadoc en français sur chaque méthode publique, en indiquant explicitement pour
  `enveloppe` et `donneesBrutes` le contrat de propriété et de lecture seule.

## À signaler plutôt qu'à décider seul

Si une ambiguïté apparaît (comportement sur les images 32 bits, `ImagePlus` à plusieurs
tranches, valeurs hors [0,1] à la sortie), **s'arrêter et poser la question** plutôt
que de choisir. Ces types seront manipulés par seize packages pendant quatre mois ;
une décision implicite prise ici coûtera un refactor global plus tard.
