package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.image.ImageGris;

/**
 * Étirement d'histogramme ou CLAHE, appliqué dans le masque seulement.
 */
public final class RehaussementContraste implements Etape<ImageGris, ImageGris> {

	@Override
	public ImageGris executer(ImageGris entree, ContexteExecution ctx) {
		throw new UnsupportedOperationException("À implémenter (étape 5)");
	}
}
