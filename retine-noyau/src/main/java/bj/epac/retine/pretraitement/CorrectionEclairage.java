package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.ImageMasquee;

/**
 * Retire l'éclairage non uniforme : fond estimé par lissage gaussien de grand rayon,
 * puis soustrait.
 *
 * <p>Un flou gaussien ordinaire ferait entrer le noir extérieur dans l'estimation du
 * fond et produirait un halo sur tout le pourtour du champ de vision. On utilise donc la
 * convolution normalisée, avec M le masque vu comme 0/1 :</p>
 * <pre>
 * fond(x,y) = flou(I·M)(x,y) / flou(M)(x,y)        pour M(x,y) vrai
 * I_corrigée = I - fond + moyenne(I dans M)
 * </pre>
 * <p>Hors masque, la sortie vaut 0. Les valeurs sortent de [0,1], c'est normal (voir D2).
 * Le sigma vaut {@code correctionEclairage.sigmaRelatif} × largeur de l'image.</p>
 *
 * <p>Réalisation : le fond étant très lisse, les sommes I·M et M sont accumulées par
 * blocs de f pixels (f ≈ sigma/6), floutées par un gaussien séparable exact (deux passes
 * 1D) sur cette grille réduite, puis rééchantillonnées bilinéairement avant la division
 * pixel par pixel. Le coût ne dépend plus de sigma.</p>
 */
public final class CorrectionEclairage implements Etape<ImageMasquee, ImageMasquee> {

	/** Nombre de blocs par sigma sur la grille réduite : compromis précision / coût. */
	private static final double BLOCS_PAR_SIGMA = 6.0;

	@Override
	public ImageMasquee executer(ImageMasquee entree, ContexteExecution ctx) {
		double sigmaRelatif = ctx.parametres().reel("correctionEclairage.sigmaRelatif");
		if (sigmaRelatif <= 0) {
			throw new IllegalArgumentException("correctionEclairage.sigmaRelatif doit être > 0 : " + sigmaRelatif);
		}
		int l = entree.largeur();
		int h = entree.hauteur();
		float[] src = entree.image().donneesBrutes();
		boolean[] m = entree.masque().donneesBrutes();

		double sigma = sigmaRelatif * l;
		int f = Math.max(1, (int) Math.floor(sigma / BLOCS_PAR_SIGMA));
		int bl = (l + f - 1) / f;
		int bh = (h + f - 1) / f;

		double[] num = new double[bl * bh];
		double[] den = new double[bl * bh];
		double somme = 0;
		long n = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				int i = y * l + x;
				if (m[i]) {
					int b = (y / f) * bl + (x / f);
					num[b] += src[i];
					den[b] += 1;
					somme += src[i];
					n++;
				}
			}
		}
		if (n == 0) {
			throw new IllegalStateException("Masque vide : rien à corriger.");
		}
		double moyenne = somme / n;

		double sigmaBlocs = sigma / f;
		flouter(num, bl, bh, sigmaBlocs);
		flouter(den, bl, bh, sigmaBlocs);

		float[] sortie = new float[l * h];
		for (int y = 0; y < h; y++) {
			double v = (y + 0.5) / f - 0.5;
			int y0 = (int) Math.floor(v);
			double ty = v - y0;
			int ya = Math.max(0, Math.min(bh - 1, y0));
			int yb = Math.max(0, Math.min(bh - 1, y0 + 1));
			for (int x = 0; x < l; x++) {
				int i = y * l + x;
				if (!m[i]) {
					continue;
				}
				double u = (x + 0.5) / f - 0.5;
				int x0 = (int) Math.floor(u);
				double tx = u - x0;
				int xa = Math.max(0, Math.min(bl - 1, x0));
				int xb = Math.max(0, Math.min(bl - 1, x0 + 1));
				double n00 = num[ya * bl + xa];
				double n01 = num[ya * bl + xb];
				double n10 = num[yb * bl + xa];
				double n11 = num[yb * bl + xb];
				double d00 = den[ya * bl + xa];
				double d01 = den[ya * bl + xb];
				double d10 = den[yb * bl + xa];
				double d11 = den[yb * bl + xb];
				double nn = (1 - ty) * ((1 - tx) * n00 + tx * n01) + ty * ((1 - tx) * n10 + tx * n11);
				double dd = (1 - ty) * ((1 - tx) * d00 + tx * d01) + ty * ((1 - tx) * d10 + tx * d11);
				double fond = dd > 1e-12 ? nn / dd : moyenne;
				sortie[i] = (float) (src[i] - fond + moyenne);
			}
		}
		return new ImageMasquee(ImageGris.enveloppe(sortie, l, h), entree.masque());
	}

	/** Gaussien séparable exact en place (zéro hors grille, noyau tronqué à 3 sigma). */
	private static void flouter(double[] a, int l, int h, double sigma) {
		int r = Math.max(1, (int) Math.ceil(3 * sigma));
		double[] noyau = new double[2 * r + 1];
		double s = 0;
		for (int k = -r; k <= r; k++) {
			noyau[k + r] = Math.exp(-0.5 * k * k / (sigma * sigma));
			s += noyau[k + r];
		}
		for (int k = 0; k < noyau.length; k++) {
			noyau[k] /= s;
		}
		double[] tmp = new double[a.length];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				double acc = 0;
				int k0 = Math.max(-r, -x);
				int k1 = Math.min(r, l - 1 - x);
				for (int k = k0; k <= k1; k++) {
					acc += noyau[k + r] * a[y * l + x + k];
				}
				tmp[y * l + x] = acc;
			}
		}
		for (int y = 0; y < h; y++) {
			int k0 = Math.max(-r, -y);
			int k1 = Math.min(r, h - 1 - y);
			for (int x = 0; x < l; x++) {
				double acc = 0;
				for (int k = k0; k <= k1; k++) {
					acc += noyau[k + r] * tmp[(y + k) * l + x];
				}
				a[y * l + x] = acc;
			}
		}
	}
}
