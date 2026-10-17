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
