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

- **Développement :** Java, ImageJ/Fiji, Eclipse, Jama (algèbre linéaire), JFreeChart (courbes).
- **Données :** FIRE, IDRiD ou e-ophtha.
- **Collaboration :** Git/GitHub.
- **Rédaction :** LaTeX (Overleaf) ou Word, selon le modèle de la conférence CIITA.

## Structure du dépôt

```
.
├── Docs/      Documents de cadrage du projet (problématique, plan de travail)
└── Donnees/   Jeux de données publics (non versionnés, voir ci-dessous)
    ├── FIRE/  Fundus Image Registration Dataset (recalage, 134 paires)
    └── IDRiD/ Jeu annoté pour la segmentation des lésions
```

> Le dossier `Donnees/` n'est pas versionné dans git (archives trop volumineuses pour GitHub). Téléchargez les jeux de données depuis leurs sources officielles et placez-les localement :
> - FIRE : [projects.ics.forth.gr/cvrl/fire](https://projects.ics.forth.gr/cvrl/fire)
> - IDRiD : [idrid.grand-challenge.org](https://idrid.grand-challenge.org)
> - Drive : [fire&idrid](https://drive.google.com/drive/folders/1zZWHoVnSXPMCaV4NuxjkuvBCyWTkmIzn?usp=sharing)

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
