package bj.epac.retine.pretraitement;

import java.util.Arrays;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.ImageMasquee;

/**
 * Étirement linéaire de l'histogramme sur percentiles (traitement ponctuel, chapitre 3).
 *
 * <p>Les percentiles bas et haut ({@code rehaussementContraste.percentileBas} et
 * {@code ...percentileHaut}, en %, 1 et 99 par défaut) sont calculés <b>uniquement dans
 * le masque</b> : avec le fond noir inclus, le percentile bas tomberait à 0 et
 * l'étirement ne ferait plus rien. La sortie est ramenée dans [0,1] (valeurs écrêtées),
 * et vaut 0 hors masque.</p>
 */
public final class RehaussementContraste implements Etape<ImageMasquee, ImageMasquee> {

	@Override
	public ImageMasquee executer(ImageMasquee entree, ContexteExecution ctx) {
		double bas = ctx.parametres().reel("rehaussementContraste.percentileBas");
		double haut = ctx.parametres().reel("rehaussementContraste.percentileHaut");
		if (bas < 0 || haut > 100 || bas >= haut) {
			throw new IllegalArgumentException("Percentiles invalides : bas=" + bas + ", haut=" + haut);
		}
		float[] src = entree.image().donneesBrutes();
		boolean[] m = entree.masque().donneesBrutes();
		int n = entree.masque().nombrePixelsActifs();
		if (n == 0) {
			throw new IllegalStateException("Masque vide : rien à rehausser.");
		}
		float[] dans = new float[n];
		int k = 0;
		for (int i = 0; i < src.length; i++) {
			if (m[i]) {
				dans[k++] = src[i];
			}
		}
		Arrays.sort(dans);
		double lo = dans[(int) Math.floor(bas / 100.0 * (n - 1))];
		double hi = dans[(int) Math.floor(haut / 100.0 * (n - 1))];
		if (hi <= lo) {
			throw new IllegalStateException("Dynamique nulle dans le masque (percentiles " + lo + " et " + hi + ").");
		}
		float[] sortie = new float[src.length];
		for (int i = 0; i < src.length; i++) {
			if (m[i]) {
				double v = (src[i] - lo) / (hi - lo);
				sortie[i] = (float) Math.max(0.0, Math.min(1.0, v));
			}
		}
		return new ImageMasquee(ImageGris.enveloppe(sortie, entree.largeur(), entree.hauteur()), entree.masque());
	}
}
