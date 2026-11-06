# Informations à jour du projet — 5 novembre 2026

Ce dossier rassemble les informations à jour du groupe 2, après le Point 1. Il a été préparé sans le contenu de `01.zip`, qui n'a pas pu être ouvert : il ne remplace donc pas ce zip, et rien n'en a été repris.

## Contenu

| Fichier | Rôle |
| --- | --- |
| `Projet_Retinopathie_Diabetique_v2.pdf` | Description du projet à jour |
| `Plan_de_travail_Retinopathie_sans_IA_v2.pdf` | Plan des Points 1 à 5 à jour |
| `Protocole_et_Plan_Point2.pdf` | Protocole d'évaluation corrigé, instructions de chaque binôme, calendrier jusqu'au 16/11 |
| `decisions_a_ajouter.md` | Décisions D3 à D6, à copier dans `Docs/decisions.md` |

## Ce qui a changé

| Sujet | Avant | Maintenant |
| --- | --- | --- |
| Question de recherche | La chaîne peut-elle recaler et détecter de façon robuste ? | Quelle erreur de recalage la détection tolère-t-elle, et dans quelles conditions la chaîne l'atteint-elle ? |
| Lésion ciblée | Nouvelles lésions en général | Microanévrismes |
| Critère de tolérance | Sensibilité seule, 10 points de perte | Sensibilité à 1 faux positif par image, 10 points de perte |
| Simulation | Décalage ou lésions synthétiques | Lésions effacées sur la visite 1, puis décalage |
| Erreur de recalage | RMSE | MRE, avec la RMSE en complément pour le chapitre 9 |
| Données | FIRE et IDRiD ou e-ophtha | FIRE, IDRiD, HRF ; e-ophtha seulement s'il reste du temps |
| Paires réelles | Mesure de sensibilité | Contrôle visuel seulement |
| Livrable | Plugin ImageJ | Logiciel autonome en Java, avec plugin de démonstration |
| Conférence | CIITA citée | Aucune conférence citée |
| Références | 7 références | 9 références, années corrigées (2015 et 2025), HRF ajouté |
| Article [5] | Revue sur les microanévrismes (2021) | Qomariah et al. (2021), comme dans le rapport du Point 1 |
| Environnement | Eclipse | Projet Maven, utilisable avec n'importe quel éditeur |

## À valider par le groupe le 06/11

- [ ] Niveau de faux positifs : 1 par image pour le critère principal, 4 par image en contrôle.
- [ ] Seuil de perte : 10 points de sensibilité.
- [ ] Lésion ciblée : microanévrismes seulement, avec l'accord du binôme B.
- [ ] Découpage des données.
- [ ] Décisions D3 à D6.
- [ ] Remplacement de CIITA par « une conférence ».

## À demander au professeur

- [ ] La date exacte du Point 2.
- [ ] Ce qu'il attend de « exploitable dans les hôpitaux » : un prototype testé ou un outil utilisé sur de vrais patients.
