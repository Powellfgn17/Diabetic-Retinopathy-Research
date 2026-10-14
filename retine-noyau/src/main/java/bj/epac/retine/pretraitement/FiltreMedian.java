package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.image.ImageGris;

/**
 * Débruitage par filtre médian, fenêtre de taille paramétrable.
 */
public final class FiltreMedian implements Etape<ImageGris, ImageGris> {

	@Override
	public ImageGris executer(ImageGris entree, ContexteExecution ctx) {
		throw new UnsupportedOperationException("À implémenter (étape 5)");
	}
}
