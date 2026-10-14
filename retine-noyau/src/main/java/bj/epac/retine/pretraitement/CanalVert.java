package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.image.ImageGris;

/**
 * Extrait le canal vert d'une image couleur.
 */
public final class CanalVert implements Etape<ImageGris, ImageGris> {

	@Override
	public ImageGris executer(ImageGris entree, ContexteExecution ctx) {
		throw new UnsupportedOperationException("À implémenter (étape 4)");
	}
}
