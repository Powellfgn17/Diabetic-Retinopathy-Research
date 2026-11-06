package bj.epac.retine.chaine;

import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.Masque;

/**
 * Reçoit les résultats intermédiaires d'une chaîne, au fur et à mesure, pour les afficher
 * ou les enregistrer (visualiseur, figures du rapport). Les objets reçus sont immuables :
 * l'observateur peut les garder sans copie.
 *
 * <p>Appelé depuis le fil de calcul : un observateur graphique doit repasser sur le fil
 * de l'interface avant de toucher aux composants.</p>
 */
@FunctionalInterface
public interface Observateur {

	/** Observateur qui ignore tout : le défaut, sans aucun coût supplémentaire. */
	Observateur AUCUN = (nom, image, masque) -> {
	};

	/**
	 * @param nom    nom lisible de l'étape qui vient de se terminer
	 * @param image  image produite, ou null si l'étape ne produit qu'un masque
	 * @param masque masque produit ou associé, ou null
	 */
	void etape(String nom, ImageGris image, Masque masque);
}
