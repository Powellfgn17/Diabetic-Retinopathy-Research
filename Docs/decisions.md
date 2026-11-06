# Décisions d'architecture

## D1 — Convention de coordonnées pixel

Le centre du pixel `(x, y)` est en `(x + 0.5, y + 0.5)`. Appliquée dans
`Masque.cercle` (pixel actif si `(x+0.5-cx)² + (y+0.5-cy)² ≤ rayon²`) et à toute
future interpolation. Les points de contrôle de FIRE et le centrage de la fenêtre
de Zernike en dépendent : un décalage d'un demi-pixel deviendrait un biais
systématique dans le RMSE, indétectable par la suite.

## D2 — Sortie 32 bits

`AdaptateurImageJ.versImagePlus32bits` écrit les `float` sans quantification, et
`versGris` accepte le 32 bits en entrée (sans division). Après correction
d'éclairage les valeurs sortent de [0,1] et ne sont plus entières : un
intermédiaire sauvé en 8 bits serait quantifié sur 256 niveaux. Le 8 bits reste
réservé à l'affichage et aux figures. Piles multi-tranches et images indexées
restent rejetées.

## D3 — Squelette en Java pur (Zhang-Suen)

*Proposée le 05/11/2026, à valider par le groupe le 06/11.*

Le squelette est calculé par amincissement de Zhang-Suen, codé en Java dans
`retine-noyau` (`squelette/AmincissementZhangSuen`), plutôt qu'avec le plugin
`AnalyzeSkeleton` de Fiji. Le cœur du traitement ne doit pas dépendre d'ImageJ, pour
servir tel quel au logiciel autonome.

## D4 — Paramètres de taille en degrés

*Proposée le 05/11/2026, à valider par le groupe le 06/11.*

Les paramètres de taille (échelles σ du Hessien, rayon R de la fenêtre de Zernike,
longueur d'élagage, distance de fusion des bifurcations…) sont stockés en degrés de
champ, puis convertis en pixels avec le ppd (pixels par degré) de chaque jeu :
diamètre du champ de vue en pixels divisé par l'angle du champ. Un même réglage
s'applique ainsi à FIRE, IDRiD et HRF malgré leurs résolutions différentes.

## D5 — Coordonnées des bifurcations

*Proposée le 05/11/2026, à valider par le groupe le 06/11.*

Les coordonnées de bifurcation suivent D1 : le centre du pixel `(x, y)` est en
`(x + 0.5, y + 0.5)`. Cela vaut pour les CSV échangés (D6), le centrage de la fenêtre
de Zernike et la comparaison avec les points de contrôle de FIRE.

## D6 — Échange entre binômes par fichiers CSV

*Proposée le 05/11/2026, à valider par le groupe le 06/11.*

Les binômes échangent leurs résultats par fichiers CSV dans `resultats/point2/`, hors
de Git :

| Fichier | Colonnes |
| --- | --- |
| `bifurcations/<paire>_<visite>.csv` | `x, y, type` |
| `descripteurs/<paire>_<visite>_R<rayon>.csv` | `x, y, z1 … zk` |
| `appariements/<paire>.csv` | `x_ref, y_ref, x_suivi, y_suivi, score` |

La commande de lot lit la liste des paires de `JeuFire` et produit ces fichiers sans
intervention.
