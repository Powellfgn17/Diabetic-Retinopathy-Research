# Brief Claude Code — Vérification avant le Point 1 (lundi 19/10)

**Gel des fonctionnalités.** Le plan de travail impose le gel une semaine avant chaque
point ; on y est. Aucune fonctionnalité nouvelle, aucun refactor, aucune optimisation.
Uniquement les trois tâches ci-dessous. Si une correction demande de toucher à une
signature publique ou à une classe partagée avec le binôme B, **s'arrêter et le
signaler** plutôt que de la faire.

---

## Tâche A — Statistiques du masque sur les 268 images

Le champ de vue est détecté sur les 268 images de FIRE, avec un centre « vers
(1456, 1456) » et un rayon « d'environ 1355 px ». Ces valeurs sont arithmétiquement
cohérentes (π·1355²/2912² = 68 %), mais aucune dispersion n'a été mesurée, et aucune
vérité terrain n'existe. « Aucun échec » ne distingue pas une détection correcte d'une
estimation qui ne dépend pas de l'image.

Écrire un petit exécutable de diagnostic (classe `main` dans `src/test/java` ou un
module jetable — **pas** une nouvelle classe du noyau) qui parcourt les 268 images,
applique `MasqueChampVision`, et écrit un CSV dans `executions/<horodatage>/` avec une
ligne par image :

```
fichier, cx, cy, rayon, nbPixelsActifs, couverturePourcent
```

Puis reporter en console : min, max, moyenne et écart-type de `cx`, `cy` et `rayon`.

**Interprétation à rapporter, pas à corriger :**
- Écart-type de quelques pixels → FIRE est homogène, c'est un résultat présentable.
- Écart-type nul ou quasi nul → l'estimation ne dépend pas du contenu de l'image ;
  le signaler explicitement, ne pas tenter de le réparer avant lundi.

Générer aussi, pour **cinq images choisies dans des catégories différentes de FIRE
(S, P, A)**, une image PNG de contrôle visuel : l'image originale avec le contour du
masque tracé en surimpression. Les déposer dans `executions/<horodatage>/controle/`.
C'est le seul contrôle qui vaut quelque chose sans vérité terrain.

---

## Tâche B — Halo de bord après correction d'éclairage

Un halo clair longe le bord du champ de vue sur A01. Hypothèse de travail : le rayon
d'érosion du masque est trop faible. À 2912 px de large, la transition au bord du champ
de vue fait 20 à 40 px ; une érosion de 5 à 10 px laisse les pixels de pénombre dans le
masque, où ils polluent l'estimation du fond.

1. Vérifier que le rayon d'érosion est bien un paramètre lu depuis `config/defaut.json`
   et non une constante dans le code. Si c'est une constante, l'externaliser — c'est la
   seule modification de code autorisée par cette tâche.
2. Produire, pour A01, le résultat de `CorrectionEclairage` avec des rayons d'érosion de
   10, 20, 30 et 40 px, en PNG dans `executions/<horodatage>/halo/`.
3. Rapporter à partir de quel rayon le halo disparaît, et la perte de surface utile
   correspondante (en pourcentage du champ de vue).

Si le halo persiste à 40 px, l'hypothèse est fausse : c'est alors le sigma du gaussien
d'estimation du fond qui est en cause. Dans ce cas, produire la même série en faisant
varier le sigma (largeur/30, /20, /15, /10) à érosion fixée à 30, et le rapporter.
**Ne pas changer la valeur par défaut sans me le dire** : elle entre dans l'article.

---

## Tâche C — Clés de configuration supprimées

`config/defaut.json` a été réécrit et les clés `rayonFond`, `methode`, `tailleBloc` et
`pente` ont disparu. `tailleBloc` et `pente` sont des paramètres de CLAHE, `methode`
suggère qu'un choix entre méthodes de rehaussement était prévu par quelqu'un.

1. Retrouver dans l'historique Git qui a introduit ces clés et dans quel commit
   (`git log -p -- config/defaut.json`).
2. Chercher dans tout le dépôt, `Docs/` compris, si une classe ou un document y fait
   référence (`git grep -n 'rayonFond\|tailleBloc\|methode\|pente'`).
3. Rapporter le résultat. **Ne pas les remettre d'office** : si personne ne les lit,
   les réintroduire ajoute du bruit ; si quelqu'un les lit, c'est une discussion à
   avoir avec le binôme B, pas une décision de code.

Documenter dans `Docs/decisions.md` les clés désormais lues par le code, avec leur
valeur par défaut et l'étape qui les consomme — une ligne par clé.

---

## Ce qui n'est pas dans ce brief et ne doit pas être fait

- `maven-shade-plugin` / jar fat : après lundi. Shader Gson risque un conflit avec la
  version embarquée par Fiji.
- Optimisation du temps de calcul (6–8 s par image). L'estimation de fond pourrait se
  faire sur une image sous-échantillonnée, mais c'est un changement de résultat
  numérique : après le Point 1.
- CLAHE, segmentation, Hessien, Gabor, bifurcations, Zernike, RANSAC, mode batch.
- `JeuFire` reste intouché.
