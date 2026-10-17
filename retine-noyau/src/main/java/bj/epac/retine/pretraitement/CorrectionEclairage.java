package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.noyau.image.ImageGris;

/**
 * Retire l'éclairage non uniforme : fond estimé puis soustrait.
 */
public final class CorrectionEclairage implements Etape<ImageGris, ImageGris> {

	@Override
	public ImageGris executer(ImageGris entree, ContexteExecution ctx) {
		throw new UnsupportedOperationException("À implémenter (étape 5)");
	}
}
