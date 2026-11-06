# Suivi de la rétinopathie diabétique sans intelligence artificielle

Recalage d'images du fond d'œil par bifurcations vasculaires, moments de Zernike et RANSAC, et détection automatique des nouveaux microanévrismes entre deux visites.

*Groupe 2 : BONI Wéri, FAGNON Powell, MOUNIROU Aremou, SOSSOU Elnis · GIT 4e année, EPAC/UAC · Cours de traitement numérique de l'image, Pr Tahirou DJARA · Encadrant : Dr ASSOUMA Abdoul Kamal.*

## Contexte

La rétinopathie diabétique est une complication fréquente du diabète et l'une des principales causes de cécité évitable. Le dépistage annuel impose de comparer, visite après visite, deux photographies du fond d'œil prises avec un cadrage et un éclairage différents, afin de repérer des lésions parfois réduites à quelques pixels (microanévrismes). Ce suivi manuel est difficile et peu reproductible.

## Problème résolu

Aligner automatiquement deux images du fond d'œil d'un même patient prises à des visites différentes, puis signaler les lésions apparues ou disparues, avec une méthode **explicable, sans apprentissage**, exécutable sur un simple ordinateur.

## Question de recherche

Quelle erreur de recalage, exprimée en fraction du diamètre de la lésion, la détection des nouveaux microanévrismes tolère-t-elle entre deux visites, et dans quelles conditions d'acquisition une chaîne classique (bifurcations vasculaires, moments de Zernike, RANSAC) atteint-elle cette précision ?

Cette formulation remplace celle d'origine depuis le Point 1 : elle cible la lésion la plus exigeante (le microanévrisme), exprime l'erreur dans une unité indépendante de la caméra, et attend comme réponse des conditions d'acquisition plutôt qu'un score global.

## Objectifs

**Objectif général :** concevoir et évaluer une chaîne explicable de recalage et de détection des changements pour le suivi de la rétinopathie diabétique, et la livrer sous forme de logiciel.

**Objectifs spécifiques :**

1. Extraire le réseau vasculaire et ses bifurcations de manière robuste aux variations d'éclairage.
2. Décrire chaque bifurcation par un vecteur de moments de Zernike et apparier les bifurcations entre deux visites.
3. Estimer la transformation de recalage avec RANSAC, qui écarte les mauvais appariements.
4. Détecter les microanévrismes nouveaux ou disparus après recalage.
5. Mesurer la tolérance de la détection à l'erreur de recalage, puis la comparer à la précision obtenue sur FIRE.
6. Évaluer la robustesse et le coût de calcul de la chaîne.
7. Livrer un logiciel autonome qui produit la carte des changements et un rapport PDF.

## Méthodologie

1. **Données :** FIRE pour le recalage (134 paires avec 10 points de contrôle chacune), IDRiD pour la détection (81 images avec microanévrismes annotés), HRF pour la segmentation (45 images avec vaisseaux tracés à la main). e-ophtha seulement s'il reste du temps.
2. **Prétraitement :** mise à l'échelle commune, canal vert, masque du champ de vue, correction de l'éclairage, rehaussement du contraste, filtre médian.
3. **Segmentation des vaisseaux :** Hessien multi-échelle comparé au filtre de Gabor, puis seuillage.
4. **Points d'intérêt :** squelette du réseau (Zhang-Suen), puis bifurcations et croisements.
5. **Description :** modules de moments de Zernike sur une fenêtre circulaire centrée sur chaque bifurcation.
6. **Appariement :** coefficient de corrélation entre descripteurs, appariement croisé et seuil de décision.
7. **Transformation :** RANSAC avec un modèle rigide puis affine, et interpolation bilinéaire.
8. **Détection des changements :** normalisation d'intensité, différence, Laplacien de Gaussienne (LoG), seuillage et classement ; comparée à une méthode qui détecte d'abord sur chaque image, puis compare les lésions.

Chaque algorithme, ses paramètres et leur effet sur la suite de la chaîne sont expliqués pas à pas dans [`Docs/algorithmes.md`](Docs/algorithmes.md).

## Évaluation

La réponse à la problématique compare deux mesures, toutes deux exprimées en diamètres de microanévrisme :

| Mesure | Jeu | Méthode |
| --- | --- | --- |
| Précision du recalage | FIRE | MRE (erreur moyenne) aux 10 points de contrôle, erreur maximale, RMSE en complément, résultats par catégorie S, P et A |
| Tolérance de la détection (T) | IDRiD | Visite 1 simulée : lésions effacées, puis décalage de 0 à 2 diamètres. T est le plus petit décalage où la sensibilité, lue à 1 faux positif par image, perd plus de 10 points |
| Résultat final | FIRE et IDRiD | Part des paires de chaque catégorie dont l'erreur reste sous T |

- **Mesures intermédiaires :** Dice de la segmentation sur HRF ; distributions GM et IM, FMR et FNMR, ROC et EER pour l'appariement ; courbe FROC pour la détection.
- **Robustesse et coût :** variation d'éclairage, bruit, compression JPEG à plusieurs niveaux, et temps de traitement par paire sur un ordinateur portable.
- **Limites annoncées :** aucune paire réelle n'a de lésions annotées, donc T vient d'une simulation (les paires réelles ne servent qu'au contrôle visuel) ; le décalage simulé est constant ; chaque jeu vient d'une seule caméra.

Le protocole détaillé (découpage réglage / test, diamètre de référence, critères de réussite du Point 2) est dans `Docs/docs_v2/Protocole_et_Plan_Point2.pdf`.

## Résultats attendus

- Un **logiciel autonome en Java**, à installer sur un ordinateur : deux images en entrée, puis les images alignées, la carte des changements et un rapport PDF. ImageJ y sert de bibliothèque, et un plugin ImageJ reste disponible pour la démonstration.
- Les deux mesures chiffrées et le tableau de réponse par catégorie S, P et A.
- Une analyse des limites : à partir de quel niveau d'éclairage, de bruit ou de compression la chaîne échoue.

Le logiciel est destiné à un usage hospitalier, mais restera un prototype tant qu'il n'aura pas été validé sur de vrais patients avec un accord éthique. Il aide à l'analyse et ne pose pas de diagnostic.

## Outils

- **Développement :** Java, projet Maven (`retine-noyau`, `retine-plugin`) utilisable avec n'importe quel éditeur, ImageJ/Fiji, Jama (algèbre linéaire), JFreeChart (courbes), jpackage pour l'installateur.
- **Données :** FIRE, IDRiD, HRF, et e-ophtha si l'accès est obtenu.
- **Collaboration :** Git/GitHub, dossier partagé pour les images et les résultats, Zotero pour la bibliographie.

## Par où commencer (nouveau contributeur)

1. **Comprendre le sujet :** lire les sections *Contexte* à *Méthodologie* ci-dessus, puis `Docs/docs_v2/LISEZMOI.md` (ce qui a changé depuis le Point 1), `Docs/docs_v2/Projet_Retinopathie_Diabetique_v2.pdf` (problématique), `Docs/docs_v2/Plan_de_travail_Retinopathie_sans_IA_v2.pdf` (découpage en points) et `Docs/docs_v2/Protocole_et_Plan_Point2.pdf` (protocole d'évaluation). `Docs/docs_v1/Revue_bibliographique_retinopathie (1).docx` résume l'état de l'art ; `Docs/docs_v1/` garde les versions du Point 1.
2. **Comprendre les algorithmes et les choix techniques :** lire [`Docs/algorithmes.md`](Docs/algorithmes.md) (chaque étape de la chaîne, ses paramètres et leur effet) et [`Docs/decisions.md`](Docs/decisions.md) (conventions de coordonnées, format 32 bits, squelette, échanges CSV…). Ces décisions s'appliquent à tout le code.
3. **Récupérer le code et les données :**
   ```bash
   git clone https://github.com/Powellfgn17/Diabetic-Retinopathy-Research.git
   cd Diabetic-Retinopathy-Research
   ```
   puis télécharger les jeux de données (voir *Structure du dépôt*).
4. **Compiler et tester :** `mvn package` (voir *Compilation et exécution*).
5. **Voir le résultat :** lancer le visualiseur (`mvn -q -pl retine-plugin exec:java`, voir *Visualiser la chaîne étape par étape*) et choisir une paire FIRE.

## Avancement

Point 1 terminé (16/20, rapport remis le 1er novembre 2026). Point 2 la semaine du 16/11/2026 ; calendrier complet dans `Docs/docs_v2/Plan_de_travail_Retinopathie_sans_IA_v2.pdf`.

| Étape de la chaîne | Module | État (6 novembre 2026) |
| --- | --- | --- |
| Prétraitement (canal vert, masque, éclairage, contraste, médian) | `retine-noyau` / `pretraitement` | Fait, testé |
| Segmentation des vaisseaux (Hessien multi-échelle, hystérésis) | `retine-noyau` / `segmentation` | Faite, testée ; Dice sur HRF à mesurer (objectif ≥ 0,65) |
| Visualiseur de la chaîne (paire de visites, étape par étape) | `retine-plugin` / `visualiseur` | Fait |
| Squelette (Zhang-Suen), bifurcations | — | Point 2, en cours (binôme A) |
| Descripteur de Zernike, appariement | — | Point 2, en cours (binôme A) |
| Lecture des jeux (`JeuFire`, `JeuHrf`), découpage, vérité terrain | `retine-noyau` / `jeux` | Point 2, en cours (binôme B) |
| Mesures Dice, GM/IM, EER | — | Point 2 (binôme B) |
| RANSAC, transformation, interpolation, première détection | — | Point 3 |
| Détection améliorée, tolérance T, tableau S/P/A, robustesse | — | Point 4 |
| Installateur, rapport, article | — | Point 5 |

## Architecture du code

Le code est séparé en deux modules Maven pour que les algorithmes restent testables sans ImageJ :

- **`retine-noyau`** : Java pur. Aucune dépendance à ImageJ.
  - `noyau/image/` : types de base (`ImageGris`, `ImageCouleur`, `Masque`, `ImageMasquee`).
  - `pretraitement/` : une classe par étape (`CanalVert`, `MasqueChampVision`, `CorrectionEclairage`, `RehaussementContraste`, `FiltreMedian`) assemblées par `ChainePretraitement`.
  - `segmentation/` : `SegmentationHessienne` (écrêtage, Hessien multi-échelle, hystérésis, filtre d'aire).
  - `chaine/` : interface `Etape`, `ContexteExecution`, et `Observateur`, prévenu après chaque étape (sert au visualiseur et aux figures).
  - `parametres/` : lecture de `config/defaut.json`.
  - `jeux/` : accès aux données (`JeuFire`, `PaireImages`).
- **`retine-plugin`** : interface et lien avec ImageJ. `AdaptateurImageJ` convertit `ImagePlus` ↔ types du noyau ; `visualiseur/` contient la fenêtre du visualiseur (application seule, base du futur logiciel autonome) ; `RDR_Pretraitement` et `RDR_Visualiseur` sont les commandes de menu Fiji (déclarées dans `plugins.config`).

Règle : toute logique algorithmique va dans `retine-noyau` ; `retine-plugin` ne fait que de l'adaptation et de l'interface.

**Ajouter une étape :** créer une classe implémentant `Etape` dans le noyau, l'ajouter à la chaîne (et prévenir l'`Observateur` pour qu'elle apparaisse dans le visualiseur), ses paramètres dans `config/defaut.json`, `Parametres` et `PanneauParametres`, son explication dans `Explications` et `Docs/algorithmes.md`, écrire un test dans `src/test`.

## Structure du dépôt

```
.
├── pom.xml          POM parent Maven (hérite de pom-scijava)
├── config/          Paramètres de la chaîne (defaut.json)
├── retine-noyau/    Algorithmes en Java pur, sans dépendance à ImageJ
├── retine-plugin/   Visualiseur, adaptateur ImageJ et commandes de menu Fiji
├── resultats/       Tableaux et résultats d'évaluation
├── Docs/            algorithmes.md, decisions.md, puis les documents de cadrage : docs_v2/ (à jour), docs_v1/ (Point 1)
└── Donnees/         Jeux de données publics (non versionnés, voir ci-dessous)
    ├── FIRE/        Fundus Image Registration Dataset (recalage, 134 paires)
    ├── IDRiD/       Microanévrismes annotés (tolérance de la détection)
    └── HRF/         High-Resolution Fundus, vaisseaux tracés à la main (Dice de la segmentation)
```

> Le dossier `Donnees/` n'est pas versionné dans git (archives trop volumineuses pour GitHub). Téléchargez les jeux de données depuis leurs sources officielles et placez-les localement :
> - FIRE : [projects.ics.forth.gr/cvrl/fire](https://projects.ics.forth.gr/cvrl/fire)
> - IDRiD : [idrid.grand-challenge.org](https://idrid.grand-challenge.org)
> - HRF : [www5.cs.fau.de/research/data/fundus-images](https://www5.cs.fau.de/research/data/fundus-images/)
> - Drive : [fire&idrid](https://drive.google.com/drive/folders/1zZWHoVnSXPMCaV4NuxjkuvBCyWTkmIzn?usp=sharing)
>
> Une fois l'archive FIRE extraite, les images se trouvent dans `Donnees/FIRE/FIRE/FIRE/` (sous-dossiers `Images/`, `Ground Truth/`, `Masks/`).

## Compilation et exécution

### Prérequis

- **JDK 11 ou plus récent** (`java -version`).
- **Maven 3.8 ou plus récent** (`mvn -v`). Si Maven n'est pas installé, celui livré avec IntelliJ fonctionne :
  `"C:\Program Files\JetBrains\IntelliJ IDEA <version>\plugins\maven\lib\maven3\bin\mvn.cmd"`
- **Fiji** ([fiji.sc](https://fiji.sc)) pour lancer le plugin.
- **Git** pour cloner le dépôt.

Installation rapide du JDK et de Maven :

| Système | Commande |
| --- | --- |
| Ubuntu / Debian | `sudo apt install openjdk-17-jdk maven` |
| macOS (Homebrew) | `brew install openjdk@17 maven` |
| Windows | Installer un JDK (ex. Temurin 17) et Maven, ou utiliser ceux d'IntelliJ |

Les dépendances (SciJava, ImageJ, Jama, JUnit…) sont téléchargées **automatiquement par Maven** au premier build : il n'y a rien d'autre à installer. Une connexion Internet est nécessaire la première fois.

Les tests unitaires n'ont **pas besoin** des jeux de données ; seuls `JeuFire`, le visualiseur (liste des paires FIRE) et l'essai dans Fiji lisent `Donnees/`.

### Compiler

Depuis la racine du dépôt :

```bash
mvn package
```

Le premier build télécharge les dépendances SciJava et peut prendre plusieurs minutes ; s'il semble bloqué sans aucun téléchargement, l'interrompre et le relancer. Le build exécute aussi les tests et produit :

- `retine-noyau/target/retine-noyau-0.1.0-SNAPSHOT.jar`
- `retine-plugin/target/retine-plugin-0.1.0-SNAPSHOT.jar`

Commandes utiles :

| Commande | Effet |
| --- | --- |
| `mvn test` | Compile et exécute les tests unitaires seulement |
| `mvn package -DskipTests` | Produit les jars sans lancer les tests |
| `mvn clean package` | Repart de zéro (supprime les dossiers `target/`) |
| `mvn -pl retine-noyau test` | Ne teste que le module noyau |

### Visualiser la chaîne étape par étape

Le visualiseur charge **les deux visites d'un même œil** (visite 1 = référence, visite 2 = suivi), les fait passer par le prétraitement et la segmentation en même temps, et affiche **chaque étape au moment où elle est calculée** (16 étapes, environ 20 s pour une paire FIRE). Depuis la racine du dépôt :

```bash
mvn -q install -DskipTests          # une fois, puis après chaque modification du code
mvn -q -pl retine-plugin exec:java  # ouvre la fenêtre
```

Pour charger une paire : choisir un identifiant dans **« paire FIRE »** (A01, P12, S20…), ou **« Ouvrir deux visites… »** (sélectionner les deux images ; pour FIRE, choisir `A01_1.jpg` suffit, `A01_2.jpg` est trouvée seule), ou glisser-déposer les images sur la fenêtre. Une image seule reste possible (HRF, IDRiD) : annuler quand la visite 2 est demandée.

- **Affichage « Visite 1 | Visite 2 »** : les deux visites côte à côte, à l'étape choisie, avec le même zoom. Les visites ne sont pas encore recalées (Point 3) : le même zoom montre la même position en pixels, pas forcément le même endroit de la rétine.
- **Affichage « Avant | après »** (visite 1 ou 2) : deux étapes d'une même visite côte à côte (par défaut, l'étape précédente à gauche).
- **Liste à gauche** : les étapes dans l'ordre, avec leur durée pour chaque visite ; flèches ↑ ↓ pour passer de l'une à l'autre ; en dessous, ce que montre l'étape et les paramètres qui la règlent.
- **Image** : molette pour zoomer autour du curseur, glisser pour se déplacer, double-clic pour tout voir ; la barre du bas donne la position et la valeur du pixel sous la souris.
- **Paramètres à droite**, communs aux deux visites : modifier une valeur puis « Relancer » ; le zoom et l'étape affichée sont conservés ; survoler un paramètre affiche son effet ; « Enregistrer… » sauve un JSON réutilisable.
- **Exporter les étapes…** : toutes les images des deux visites en PNG pleine résolution (`v1_…`, `v2_…`), avec les paramètres utilisés, dans `executions/<date>-visualiseur-<paire>/`.

Mémoire : une paire FIRE occupe environ 1,5 Go. Sur un ordinateur de 8 Go ou moins, lancer avec `MAVEN_OPTS=-Xmx3g mvn -q -pl retine-plugin exec:java`.

Le visualiseur existe aussi dans Fiji : **Plugins › RDR › Visualiseur**. Les algorithmes et leurs paramètres sont expliqués dans [`Docs/algorithmes.md`](Docs/algorithmes.md).

### Installer et lancer le plugin dans Fiji

1. Fermer Fiji.
2. Copier **les deux jars** dans le dossier de Fiji :
   - `retine-plugin-0.1.0-SNAPSHOT.jar` dans `Fiji.app/plugins/`
   - `retine-noyau-0.1.0-SNAPSHOT.jar` dans `Fiji.app/jars/`
3. Relancer Fiji : les commandes apparaissent dans **Plugins › RDR** (Pretraitement, Visualiseur).

Après chaque modification du code : `mvn package`, recopier les jars, redémarrer Fiji.

> Jama n'est pas livré avec Fiji. Dès que le code l'utilise (à partir du Point 3), copier aussi `jama-1.0.3.jar` (dans `~/.m2/repository/gov/nist/math/jama/1.0.3/`) dans `Fiji.app/jars/`.

### Paramètres

Les réglages du prétraitement et de la segmentation sont dans [`config/defaut.json`](config/defaut.json) ; leur effet est expliqué dans [`Docs/algorithmes.md`](Docs/algorithmes.md). Pour essayer d'autres valeurs, le plus simple est le visualiseur (modifier, « Relancer », puis « Enregistrer… » dans un autre fichier) ; ne modifier `defaut.json` qu'une fois les nouvelles valeurs validées par le groupe. Les sorties d'exécution vont dans `executions/`, et les échanges CSV entre binômes dans `resultats/point2/` (décision D6) ; les deux sont hors de Git.

## Contribuer

- Travailler sur une branche (`git checkout -b ma-fonctionnalite`), ne pas pousser directement sur `main`.
- Avant chaque commit : `mvn test` doit passer.
- Ne pas versionner `Donnees/`, `target/`, `executions/` (déjà dans `.gitignore`).
- Toute décision de conception qui affecte plusieurs modules est ajoutée à [`Docs/decisions.md`](Docs/decisions.md).
- Code et commentaires en français, comme le reste du dépôt.

## Dépannage

| Problème | Piste |
| --- | --- |
| `mvn: command not found` | Maven non installé ou absent du `PATH` |
| `release version 11 not supported` | JDK trop ancien : `java -version` |
| La commande n'apparaît pas dans Fiji | Les **deux** jars sont-ils copiés ? Fiji a-t-il été redémarré ? |
| `NoClassDefFoundError: Jama` | Copier `jama-1.0.3.jar` dans `Fiji.app/jars/` |
| Images FIRE introuvables, menu « paire FIRE » vide | Vérifier le chemin `Donnees/FIRE/FIRE/FIRE/Images/` ; lancer le visualiseur depuis la racine du dépôt |
| `OutOfMemoryError` dans le visualiseur | Lancer avec `MAVEN_OPTS=-Xmx3g` (une paire FIRE occupe environ 1,5 Go) |

## Valorisation

- Possibilité de rédiger un article scientifique et de le présenter dans une conférence.
- Code publié en open source.
- Démonstrateur présentable à un service d'ophtalmologie, pour obtenir de vraies paires de suivi avec un accord éthique.

## Références principales

1. Patankar S. S., Kulkarni J. V. *Orthogonal moments for determining correspondence between vessel bifurcations for retinal image registration*. Computer Methods and Programs in Biomedicine, 119(3), 2015. DOI 10.1016/j.cmpb.2015.02.009
2. Hernández-Matas C. et al. *FIRE: Fundus Image Registration Dataset*. Modeling and Artificial Intelligence in Ophthalmology, 2017. [projects.ics.forth.gr/cvrl/fire](https://projects.ics.forth.gr/cvrl/fire)
3. Adal K. M. et al. *An Automated System for the Detection and Classification of Retinal Changes Due to Red Lesions in Longitudinal Fundus Images*. IEEE Transactions on Biomedical Engineering, 2017. DOI 10.1109/TBME.2017.2752701
4. Bhaskaranand M. et al. *Automated Diabetic Retinopathy Screening and Monitoring Using Retinal Fundus Image Analysis*. Journal of Diabetes Science and Technology, 10(2), 2016. DOI 10.1177/1932296816628546
5. Qomariah D. U. N. et al. International Journal of Intelligent Engineering and Systems, 14(3), 2021. DOI 10.22266/ijies2021.0630.30
6. Zhou J. et al. *Color fundus photograph registration based on feature and intensity for longitudinal evaluation of diabetic retinopathy progression*. Frontiers in Physics, 10, 2022. DOI 10.3389/fphy.2022.978392
7. Veturi Y. A. et al. *EyeLiner: A Deep Learning Pipeline for Longitudinal Image Registration Using Fundus Landmarks*. Ophthalmology Science, 5(2), 2025. DOI 10.1016/j.xops.2024.100664
8. Porwal P. et al. *Indian Diabetic Retinopathy Image Dataset (IDRiD): A Database for Diabetic Retinopathy Screening Research*. Data, 3(3), 2018. DOI 10.3390/data3030025
9. Budai A., Bock R., Maier A., Hornegger J., Michelson G. *Robust Vessel Segmentation in Fundus Images*. International Journal of Biomedical Imaging, 2013, 154860. DOI 10.1155/2013/154860
