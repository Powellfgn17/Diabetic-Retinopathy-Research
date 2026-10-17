package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.ImageMasquee;

/**
 * Débruitage par filtre médian de rayon {@code filtreMedian.rayon} (fenêtre carrée de
 * côté 2r+1, chapitre 5).
 *
 * <p>Au bord du masque, seuls les voisins <b>à l'intérieur du masque</b> entrent dans la
 * médiane (calculée sur ce sous-ensemble, moyenne des deux valeurs centrales si leur
 * nombre est pair) : compléter par des zéros réintroduirait le bord noir que
 * {@link MasqueChampVision} vient d'éroder. Hors masque, la sortie vaut 0.
 * Implémentation naïve, tri par insertion, suffisante à ce rayon.</p>
 */
public final class FiltreMedian implements Etape<ImageMasquee, ImageMasquee> {

	@Override
	public ImageMasquee executer(ImageMasquee entree, ContexteExecution ctx) {
		int r = ctx.parametres().entier("filtreMedian.rayon");
		if (r < 0) {
			throw new IllegalArgumentException("filtreMedian.rayon doit être >= 0 : " + r);
		}
		int l = entree.largeur();
		int h = entree.hauteur();
		float[] src = entree.image().donneesBrutes();
		boolean[] m = entree.masque().donneesBrutes();
		float[] sortie = new float[src.length];
		float[] fenetre = new float[(2 * r + 1) * (2 * r + 1)];
		for (int y = 0; y < h; y++) {
			int y0 = Math.max(0, y - r);
			int y1 = Math.min(h - 1, y + r);
			for (int x = 0; x < l; x++) {
				if (!m[y * l + x]) {
					continue;
				}
				int x0 = Math.max(0, x - r);
				int x1 = Math.min(l - 1, x + r);
				int n = 0;
				for (int yy = y0; yy <= y1; yy++) {
					for (int xx = x0; xx <= x1; xx++) {
						int j = yy * l + xx;
						if (m[j]) {
							float v = src[j];
							int k = n++;
							while (k > 0 && fenetre[k - 1] > v) {
								fenetre[k] = fenetre[k - 1];
								k--;
							}
							fenetre[k] = v;
						}
					}
				}
				sortie[y * l + x] = n % 2 == 1
						? fenetre[n / 2]
						: (float) (((double) fenetre[n / 2 - 1] + fenetre[n / 2]) / 2.0);
			}
		}
		return new ImageMasquee(ImageGris.enveloppe(sortie, l, h), entree.masque());
	}
}
