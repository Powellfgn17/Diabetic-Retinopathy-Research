package bj.epac.retine.plugin.visualiseur;

import bj.epac.retine.bifurcation.DetecteurBifurcations;
import bj.epac.retine.pretraitement.ChainePretraitement;
import bj.epac.retine.segmentation.SegmentationHessienne;
import bj.epac.retine.squelette.AmincissementZhangSuen;

/**
 * Textes « Qu'est-ce que je regarde ? » affichés pour chaque étape. Version courte de
 * Docs/algorithmes.md : garder les deux cohérents.
 */
final class Explications {

	private Explications() {
	}

	static String pour(String nom) {
		if ("Image d'origine".equals(nom)) {
			return "La photo telle qu'elle est lue, valeurs ramenées entre 0 et 1 (division par 255).\n\n"
					+ "À regarder : l'éclairage n'est pas uniforme (centre plus clair), le fond noir autour "
					+ "du disque ne contient aucune information, et les vaisseaux sont plus visibles dans le vert.";
		}
		if (ChainePretraitement.CANAL_VERT.equals(nom)) {
			return "On ne garde que le canal vert : le sang absorbe le vert, donc vaisseaux et "
					+ "microanévrismes y sont les plus sombres et les plus contrastés. Le rouge est saturé, le "
					+ "bleu très bruité.\n\nAucun paramètre.";
		}
		if (ChainePretraitement.MASQUE.equals(nom)) {
			return "En rouge : ce qui est HORS du champ de vue, exclu de tous les calculs.\n\n"
					+ "Le masque = canal rouge au-dessus du seuil, plus grande zone d'un seul tenant, trous "
					+ "bouchés, puis rogné de quelques pixels (érosion) pour exclure la bande de transition du "
					+ "bord, qui ressemblerait à un vaisseau faisant le tour de l'image.\n\n"
					+ "Paramètres : seuil du rouge (0,08), érosion (7 px).";
		}
		if (ChainePretraitement.ECLAIRAGE.equals(nom)) {
			return "On estime l'éclairage par un très gros flou (σ ≈ 146 px sur FIRE), puis on le retire : "
					+ "image − fond + moyenne. Le flou ne fait la moyenne qu'avec les pixels du champ de vue "
					+ "(convolution normalisée), pour ne pas assombrir le bord.\n\n"
					+ "À regarder : le centre et les bords ont maintenant le même niveau moyen. Un liseré clair "
					+ "subsiste au bord (halo mesuré : 0,82 contre 0,49 à l'intérieur) : la photo s'éclaircit "
					+ "sur ses 60 derniers pixels, une variation plus étroite que le flou.\n\n"
					+ "Paramètre : σ / largeur (0,05). Trop petit : disque optique et gros vaisseaux pâlissent.";
		}
		if (ChainePretraitement.CONTRASTE.equals(nom)) {
			return "Étirement linéaire : le percentile 1 % devient noir, le percentile 99 % devient blanc, le "
					+ "reste est étalé entre les deux. Calculé dans le champ de vue seulement.\n\n"
					+ "Ne change pas OÙ sont les vaisseaux, seulement l'échelle des valeurs.\n\n"
					+ "Paramètres : percentiles noir (1) et blanc (99).";
		}
		if (ChainePretraitement.MEDIAN.equals(nom)) {
			return "Chaque pixel prend la valeur du milieu de sa fenêtre 5 × 5 triée : le bruit isolé disparaît, "
					+ "les bords des vaisseaux restent nets.\n\n"
					+ "Comparez avec l'étape précédente (bouton « Avant / après ») en zoomant fort : le grain a "
					+ "disparu.\n\n"
					+ "Paramètre : rayon (2). Attention : la détection des microanévrismes exigera au plus 3 × 3.";
		}
		if (SegmentationHessienne.ECRETAGE.equals(nom)) {
			return "Tout ce qui est plus clair que la médiane de son voisinage (fenêtre de 50 px de rayon) "
					+ "est plafonné : exsudats, reflets et disque optique deviennent des plateaux plats.\n\n"
					+ "Pourquoi : le pied d'une tache claire ressemble à une vallée, et le Hessien l'aurait pris "
					+ "pour un vaisseau (anneaux de faux vaisseaux autour des exsudats). Les vaisseaux, plus "
					+ "sombres que leur voisinage, ne sont pas touchés.\n\n"
					+ "Pourquoi « locale » : avec la médiane de tout le champ, un vaisseau pâle qui traverse une "
					+ "zone claire était aplati, donc invisible pour le Hessien.\n\n"
					+ "Paramètres : écrêtage (50 %, 100 = désactivé), rayon (50 px, 0 = médiane du champ entier).";
		}
		if (nom.startsWith("Réponse à σ")) {
			return "Réponse du Hessien à UNE échelle : σ² × λ2 là où λ2 > 0 (une vallée, donc un vaisseau "
					+ "sombre), 0 ailleurs. Clair = forte réponse.\n\n"
					+ "Chaque σ voit surtout les vaisseaux d'environ σ / 0,6 pixels de large : faites défiler les "
					+ "échelles pour voir les petits vaisseaux répondre aux petits σ, et les gros troncs aux "
					+ "grands σ. Les grands σ donnent aussi des réponses plus floues.\n\n"
					+ "Paramètre : échelles σ (2, 3, 4, 6, 8 px).";
		}
		if (SegmentationHessienne.REPONSE.equals(nom)) {
			return "Maximum des réponses sur toutes les échelles : chaque vaisseau répond à l'échelle qui lui "
					+ "convient.\n\n"
					+ "À regarder : le bord du champ ne répond pas (la marche vers le noir est courbée dans le "
					+ "mauvais sens pour une vallée). Les petites taches sombres répondent presque autant que les "
					+ "vaisseaux : le filtre d'aire s'en chargera.";
		}
		if (SegmentationHessienne.GERMES.equals(nom)) {
			return "En rouge : les germes de l'hystérésis, c'est-à-dire les pixels au-dessus du seuil haut "
					+ "(les 6 % les plus forts du champ). On est sûr que ce sont des vaisseaux.\n\n"
					+ "Paramètre : seuil haut (94 %). Plus haut = moins de germes, réseau plus coupé.";
		}
		if (SegmentationHessienne.HYSTERESIS.equals(nom)) {
			return "En jaune : les germes, plus les pixels au-dessus du seuil bas qui leur sont reliés.\n\n"
					+ "Comparez avec les germes (« Avant / après ») : les vaisseaux fins, trop faibles pour être "
					+ "des germes, sont récupérés parce qu'ils partent d'un tronc solide.\n\n"
					+ "Paramètre : seuil bas (88 %). Plus bas = réseau plus continu, mais plus de barbes.";
		}
		if (SegmentationHessienne.VAISSEAUX.equals(nom)) {
			return "En vert : le masque final des vaisseaux, après retrait des morceaux isolés de moins de "
					+ "150 px.\n\n"
					+ "C'est l'entrée du squelette (Point 2). À vérifier : réseau continu, pas de trous dans les "
					+ "vaisseaux (ils feraient des boucles dans le squelette), peu de barbes (fausses "
					+ "bifurcations), rien le long du bord.\n\n"
					+ "Sur FIRE, environ 10 à 11 % du champ est classé vaisseau.";
		}
		if (AmincissementZhangSuen.ZHANG_SUEN.equals(nom)) {
			return "En violet : le squelette, c'est-à-dire la ligne centrale des vaisseaux (en vert pâle). Il est "
					+ "épaissi à 3 px à l'affichage pour rester visible : la valeur sous la souris donne le vrai "
					+ "pixel.\n\n"
					+ "Zhang-Suen ronge le masque couche par couche, en ne retirant que les pixels dont la "
					+ "disparition ne coupe rien et ne raccourcit pas les extrémités, en alternant deux "
					+ "sous-passes (bords sud-est, puis nord-ouest) pour rester centré.\n\n"
					+ "À regarder en zoomant fort : là où la ligne tourne ou descend en diagonale, il reste des "
					+ "« marches d'escalier » (deux pixels là où un seul suffirait). Comparez avec l'étape "
					+ "suivante (« Avant | après »).\n\nAucun paramètre.";
		}
		if (AmincissementZhangSuen.MARCHES.equals(nom)) {
			return "Le squelette final : les marches d'escalier ont été retirées, sans couper ni raccourcir "
					+ "aucune ligne. Chaque pixel a maintenant exactement un voisin par branche (environ 12 % des "
					+ "pixels retirés sur FIRE).\n\n"
					+ "C'est l'entrée de la détection des bifurcations : un pixel avec 3 branches (nombre de "
					+ "croisements CN = 3) est une bifurcation, 4 un croisement, 1 une extrémité.\n\n"
					+ "À vérifier : les petites branches courtes le long des vaisseaux viennent des barbes du "
					+ "masque ; l'élagage (12 px) les retirera. Une boucle signale un trou dans un vaisseau du "
					+ "masque.";
		}
		if (DetecteurBifurcations.ELAGAGE.equals(nom)) {
			return "Le squelette nettoyé avant de chercher les bifurcations.\n\n"
					+ "1. Petites boucles remplies : un trou dans un vaisseau du masque fait une boucle, donc deux "
					+ "fausses bifurcations. Les zones entourées de moins de 400 px sont bouchées, puis le squelette "
					+ "est recalculé.\n"
					+ "2. Élagage : chaque branche qui part d'une extrémité et atteint une jonction en moins de "
					+ "12 px est une barbe ; elle est retirée (jusqu'à 3 passes, pour les barbes fourchues).\n\n"
					+ "Comparez avec l'étape précédente (« Avant | après ») : les petites pointes le long des "
					+ "vaisseaux ont disparu.\n\n"
					+ "Paramètres : longueur d'élagage (12 px), aire des boucles (400 px).";
		}
		if (VisualiseurChaine.POINTS.equals(nom)) {
			return "Anneau bleu : bifurcation (un vaisseau se sépare en deux). Anneau orange : croisement "
					+ "(deux vaisseaux se croisent). La barre du bas donne le point le plus proche de la souris.\n\n"
					+ "Détection : nombre de croisements CN ≥ 3 sur le squelette élagué ; les pixels jonctions "
					+ "à moins de 3 px sont fusionnés ; le type vient du nombre de branches qui sortent d'un petit "
					+ "carré autour du point (3 = bifurcation, 4 = croisement). Deux bifurcations à moins de 8 px "
					+ "deviennent un croisement si 4 branches en sortent.\n\n"
					+ "Les points trop près du bord (fenêtre de Zernike hors du champ) sont écartés.\n\n"
					+ "Sur FIRE : environ 200 points par image. Après une rotation de 30°, 90 % sont retrouvés, "
					+ "à 2 px près en moyenne.\n\n"
					+ "Paramètres : fusion (3 px), croisement (8 px).";
		}
		if (VisualiseurChaine.FENETRES.equals(nom)) {
			return "Cercle vert : la fenêtre ronde que décrit le descripteur de Zernike, de rayon R = 48 px.\n\n"
					+ "Pour chaque point, la fenêtre de l'image prétraitée est normalisée (moyenne 0, écart-type 1), "
					+ "puis décomposée sur les polynômes de Zernike ; on garde les modules |Z(n,m)|, qui ne changent "
					+ "pas si l'œil tourne. Ordre 4 : 8 valeurs par point.\n\n"
					+ "Mesuré sur FIRE S01 tourné de 30° : le même point est reconnu en premier parmi tous les autres "
					+ "dans 66 % des cas avec R = 32, 86 % avec R = 48, 95 % avec R = 64.\n\n"
					+ "Paramètres : rayon R, ordre.";
		}
		return "";
	}
}
