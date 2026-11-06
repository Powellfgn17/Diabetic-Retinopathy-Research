# Suivi de la rétinopathie diabétique sans intelligence artificielle

Recalage d'images du fond d'œil par bifurcations vasculaires, moments de Zernike et RANSAC, et détection automatique des nouvelles lésions entre deux visites.

## Contexte

La rétinopathie diabétique est une complication fréquente du diabète et l'une des principales causes de cécité évitable. Le dépistage annuel impose de comparer, visite après visite, deux photographies du fond d'œil prises avec un cadrage et un éclairage différents, afin de repérer des lésions parfois réduites à quelques pixels (microanévrismes). Ce suivi manuel est difficile et peu reproductible.

## Problème résolu

Aligner automatiquement deux images du fond d'œil d'un même patient prises à des visites différentes, puis signaler les lésions apparues ou disparues, avec une méthode **explicable, sans apprentissage**, exécutable sur un simple ordinateur.

## Question de recherche

Une chaîne de traitement classique fondée sur les bifurcations vasculaires, les moments de Zernike et RANSAC peut-elle recaler de façon robuste deux fonds d'œil pris à des visites différentes, et détecter de manière fiable les nouvelles lésions de rétinopathie diabétique ?

## Objectifs

**Objectif général :** concevoir et évaluer une chaîne explicable de recalage et de détection des changements pour le suivi de la rétinopathie diabétique.

**Objectifs spécifiques :**
1. Extraire le réseau vasculaire et ses points de bifurcation de manière robuste aux variations d'éclairage.
2. Décrire chaque bifurcation par un vecteur de moments de Zernike et apparier les bifurcations entre deux visites.
3. Estimer la transformation de recalage avec RANSAC pour éliminer les faux appariements.
4. Détecter les lésions nouvelles ou disparues après recalage.
5. Évaluer la précision, la robustesse et le coût de calcul de la chaîne sur des données publiques.

## Méthodologie

1. **Données :** FIRE pour le recalage (134 paires avec points de contrôle de référence) ; un jeu avec lésions annotées (IDRiD ou e-ophtha) pour la détection.
2. **Prétraitement :** extraction du canal vert, masque du champ de vue, correction de l'éclairage non uniforme, rehaussement du contraste, débruitage par filtre médian.
3. **Segmentation des vaisseaux :** renforcement des structures tubulaires (filtre de Gabor, Hessien), puis seuillage.
4. **Points d'intérêt :** squelettisation, puis détection des bifurcations et des croisements.
5. **Description :** vecteur de modules de moments de Zernike sur une fenêtre circulaire centrée sur chaque bifurcation.
6. **Appariement :** coefficient de corrélation entre descripteurs, recherche dans un rayon limité, appariement croisé.
7. **Transformation :** estimation par RANSAC (modèle rigide puis affine), interpolation bilinéaire.
8. **Détection des changements :** normalisation d'intensité entre les deux images recalées, différence, détection des petites taches par Laplacien de Gaussienne, seuillage, classement en lésions nouvelles ou disparues.

## Évaluation

- **Recalage :** erreur sur les points de contrôle de FIRE (RMSE), taux de réussite par catégorie, comparaison avec et sans RANSAC.
- **Pouvoir discriminant des descripteurs :** bifurcations correspondantes contre non correspondantes, distributions GM/IM, courbes FMR/FNMR, ROC et EER.
- **Détection des lésions :** suivis simulés avec lésions synthétiques à positions connues, puis paires réelles ; sensibilité, faux positifs par image, courbe ROC.
- **Robustesse :** dégradation des résultats sous variation d'éclairage, bruit et compression JPEG à plusieurs niveaux.
- **Coût :** temps de traitement par paire sur un ordinateur portable.

## Résultats attendus

- Un plugin ImageJ complet, du fond d'œil brut à la carte des changements.
- Des mesures chiffrées de l'erreur de recalage sur FIRE et de la qualité des descripteurs (ROC, EER).
- Une carte visuelle des lésions nouvelles et disparues, directement lisible par un clinicien.
- Une analyse des limites : à partir de quel niveau d'éclairage, de bruit ou de compression la chaîne échoue.

## Outils

- **Développement :** Java, Maven, ImageJ/Fiji, Eclipse ou IntelliJ, Jama (algèbre linéaire), JFreeChart (courbes).
- **Données :** FIRE, IDRiD ou e-ophtha.
- **Collaboration :** Git/GitHub.
- **Rédaction :** LaTeX (Overleaf) ou Word, selon le modèle de la conférence CIITA.

## Par où commencer (nouveau contributeur)

1. **Comprendre le sujet :** lire les sections *Contexte* à *Méthodologie* ci-dessus, puis `Docs/docs_v2/LISEZMOI.md` (ce qui a changé depuis le Point 1), `Docs/docs_v2/Projet_Retinopathie_Diabetique_v2.pdf` (problématique), `Docs/docs_v2/Plan_de_travail_Retinopathie_sans_IA_v2.pdf` (découpage en points) et `Docs/docs_v2/Protocole_et_Plan_Point2.pdf` (protocole d'évaluation). `Docs/docs_v1/Revue_bibliographique_retinopathie (1).docx` résume l'état de l'art ; `Docs/docs_v1/` garde les versions du Point 1.
2. **Comprendre les choix techniques :** lire [`Docs/decisions.md`](Docs/decisions.md) (conventions de coordonnées, format 32 bits, etc.). Ces décisions s'appliquent à tout le code.
3. **Récupérer le code et les données :**
   ```bash
   git clone https://github.com/Powellfgn17/Diabetic-Retinopathy-Research.git
   cd Diabetic-Retinopathy-Research
   ```
   puis télécharger les jeux de données (voir *Structure du dépôt*).
4. **Compiler et tester :** `mvn package` (voir *Compilation et exécution*).
5. **Voir le résultat :** installer le plugin dans Fiji et lancer **Plugins › RDR › Pretraitement** sur une image FIRE.

## Avancement

| Étape de la chaîne | Module | État |
| --- | --- | --- |
| Prétraitement (canal vert, masque, éclairage, contraste, médian) | `retine-noyau` / `pretraitement` | Fait, testé, commande Fiji disponible |
| Segmentation des vaisseaux, bifurcations | — | À faire |
| Descripteurs de Zernike, appariement | — | À faire |
| RANSAC, transformation, interpolation | — | À faire |
| Détection des changements | — | À faire |
| Évaluation (RMSE FIRE, ROC/EER, robustesse) | `resultats/` | À faire |

## Architecture du code

Le code est séparé en deux modules Maven pour que les algorithmes restent testables sans ImageJ :

- **`retine-noyau`** : Java pur. Aucune dépendance à ImageJ.
  - `noyau/image/` : types de base (`ImageGris`, `ImageCouleur`, `Masque`, `ImageMasquee`).
  - `pretraitement/` : une classe par étape (`CanalVert`, `MasqueChampVision`, `CorrectionEclairage`, `RehaussementContraste`, `FiltreMedian`) assemblées par `ChainePretraitement`.
  - `chaine/` : interface `Etape` et `ContexteExecution` communs à toutes les étapes.
  - `parametres/` : lecture de `config/defaut.json`.
  - `jeux/` : accès aux données (`JeuFire`, `PaireImages`).
- **`retine-plugin`** : colle avec Fiji. `AdaptateurImageJ` convertit `ImagePlus` ↔ types du noyau ; `RDR_Pretraitement` est la commande de menu (déclarée dans `plugins.config`).

Règle : toute logique algorithmique va dans `retine-noyau` ; `retine-plugin` ne fait que de l'adaptation et de l'interface.

**Ajouter une étape :** créer une classe implémentant `Etape` dans le noyau, l'ajouter à la chaîne, ses paramètres dans `config/defaut.json` et `Parametres`, écrire un test dans `src/test`, puis l'exposer dans le plugin si besoin.

## Structure du dépôt

```
.
├── pom.xml          POM parent Maven (hérite de pom-scijava)
├── config/          Paramètres de la chaîne (defaut.json)
├── retine-noyau/    Algorithmes en Java pur, sans dépendance à ImageJ
├── retine-plugin/   Plugin Fiji : adaptateur ImageJ et commandes de menu
├── resultats/       Tableaux et résultats d'évaluation
├── Docs/            decisions.md, puis les documents de cadrage : docs_v2/ (à jour), docs_v1/ (Point 1)
└── Donnees/         Jeux de données publics (non versionnés, voir ci-dessous)
    ├── FIRE/        Fundus Image Registration Dataset (recalage, 134 paires)
    └── IDRiD/       Jeu annoté pour la segmentation des lésions
```

> Le dossier `Donnees/` n'est pas versionné dans git (archives trop volumineuses pour GitHub). Téléchargez les jeux de données depuis leurs sources officielles et placez-les localement :
> - FIRE : [projects.ics.forth.gr/cvrl/fire](https://projects.ics.forth.gr/cvrl/fire)
> - IDRiD : [idrid.grand-challenge.org](https://idrid.grand-challenge.org)
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

Les tests unitaires n'ont **pas besoin** des jeux de données ; seuls `JeuFire` et l'essai dans Fiji lisent `Donnees/`.

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

### Installer et lancer le plugin dans Fiji

1. Fermer Fiji.
2. Copier **les deux jars** dans le dossier de Fiji :
   - `retine-plugin-0.1.0-SNAPSHOT.jar` dans `Fiji.app/plugins/`
   - `retine-noyau-0.1.0-SNAPSHOT.jar` dans `Fiji.app/jars/`
3. Relancer Fiji : la commande apparaît dans **Plugins › RDR › Pretraitement**.

Après chaque modification du code : `mvn package`, recopier les jars, redémarrer Fiji.

> Jama n'est pas livré avec Fiji. Dès que le code l'utilise (à partir du Point 3), copier aussi `jama-1.0.3.jar` (dans `~/.m2/repository/gov/nist/math/jama/1.0.3/`) dans `Fiji.app/jars/`.

### Paramètres

Les réglages du prétraitement sont dans [`config/defaut.json`](config/defaut.json). Pour essayer d'autres valeurs, copier ce fichier plutôt que de le modifier. Les sorties d'exécution vont dans `executions/`, non versionné.

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
| Images FIRE introuvables | Vérifier le chemin `Donnees/FIRE/FIRE/FIRE/Images/` |

## Valorisation

- Article et présentation à la conférence CIITA.
- Code publié en open source sous forme de plugin ImageJ.
- Démonstrateur présentable à un service d'ophtalmologie, avec possibilité de collaboration pour obtenir de vraies paires de suivi (avec accord éthique).

## Références principales

1. *Orthogonal moments for determining correspondence between vessel bifurcations for retinal image registration*, 2014. PubMed 25837489.
2. Hernandez-Matas C. et al. *FIRE: Fundus Image Registration Dataset*. Modeling and Artificial Intelligence in Ophthalmology, 2017. [projects.ics.forth.gr/cvrl/fire](https://projects.ics.forth.gr/cvrl/fire)
3. *An Automated System for the Detection and Classification of Retinal Changes Due to Red Lesions in Longitudinal Fundus Images*. IEEE Trans. Biomed. Eng. doi:10.1109/TBME.2017.2752701
4. *Automated Diabetic Retinopathy Screening and Monitoring Using Retinal Fundus Image Analysis*. PMC4773978.
5. *Automated microaneurysms detection for early diagnosis of diabetic retinopathy: A comprehensive review*. ScienceDirect, 2021.
6. *Color fundus photograph registration based on feature and intensity for longitudinal evaluation of diabetic retinopathy progression*. Frontiers in Physics, 2022.
7. *EyeLiner: A Deep Learning Pipeline for Longitudinal Image Registration Using Fundus Landmarks*. Ophthalmology Science, 2024.
