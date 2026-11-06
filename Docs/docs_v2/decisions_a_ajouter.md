## Décisions à ajouter à la suite de D1 et D2 (Point 2, 5 novembre 2026)

Copier ces lignes à la fin de `Docs/decisions.md`. Elles restent à valider par le groupe à la réunion du 06/11.

- **D3 — Squelette.** Codé en Java avec Zhang-Suen (amincissement), dans `retine-noyau`, plutôt qu'avec `AnalyzeSkeleton`, pour que le cœur du traitement ne dépende pas d'ImageJ.
- **D4 — Paramètres de taille.** Stockés en degrés, puis convertis en pixels avec le ppd (pixels par degré) de chaque jeu.
- **D5 — Coordonnées.** Les coordonnées de bifurcation suivent D1 : centre du pixel à (x+0,5 ; y+0,5).
- **D6 — Échange entre binômes.** Par fichiers CSV dans `resultats/point2/`, hors de Git :
  - `bifurcations/<paire>_<visite>.csv` : `x, y, type`
  - `descripteurs/<paire>_<visite>_R<rayon>.csv` : `x, y, z1 … zk`
  - `appariements/<paire>.csv` : `x_ref, y_ref, x_suivi, y_suivi, score`
