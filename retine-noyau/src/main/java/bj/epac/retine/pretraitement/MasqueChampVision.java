package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.image.ImageGris;
import bj.epac.retine.image.Masque;

/**
 * Seuille le fond noir et rend le masque du disque rétinien.
 */
public final class MasqueChampVision implements Etape<ImageGris, Masque> {

	@Override
	public Masque executer(ImageGris entree, ContexteExecution ctx) {
		throw new UnsupportedOperationException("À implémenter (étape 4)");
	}
}
