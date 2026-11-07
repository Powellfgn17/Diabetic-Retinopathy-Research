package bj.epac.retine.zernike;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import bj.epac.retine.bifurcation.Bifurcation;
import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.parametres.Parametres;

/**
 * Descripteur de Zernike de la fenêtre ronde autour de chaque bifurcation (Point 2,
 * section 12 de Docs/algorithmes.md).
 *
 * <p>Pour chaque point (centre selon D1/D5), on prend les pixels dont le centre est à moins
 * de R = {@code zernike.rayon} × {@code segmentation.facteurEchelle} du point, on les
 * normalise (moyenne 0, écart-type 1 : insensible à l'éclairage et au contraste de la visite),
 * puis on calcule les moments</p>
 * <pre>
 *            n + 1
 *   Z(n,m) = ───── × Σ f(x, y) × R(n,m)(ρ) × e^(−j m θ)      (ρ = distance / R ≤ 1)
 *            π R²
 * </pre>
 * <p>pour n de 1 à {@code zernike.ordre}, 0 ≤ m ≤ n, n − m pair. Le descripteur est la liste
 * des modules |Z(n,m)| dans cet ordre : une rotation de la fenêtre multiplie Z(n,m) par
 * e^(−j m α), de module 1, donc les modules ne changent pas. n = 0 est omis : après
 * normalisation, Z(0,0) vaut toujours 0.</p>
 *
 * <p>Ordre 4 : 8 valeurs, (1,1) (2,0) (2,2) (3,1) (3,3) (4,0) (4,2) (4,4). Ordre 8 : 24.</p>
 */
public final class DescripteurZernike {

	/** Un point et son vecteur descripteur. Immuable. */
	public static final class Descripteur {
		private final Bifurcation point;
		private final double[] valeurs;

		public Descripteur(Bifurcation point, double[] valeurs) {
			this.point = point;
			this.valeurs = valeurs.clone();
		}

		public Bifurcation point() {
			return point;
		}

		/** @return copie des modules |Z(n,m)|, dans l'ordre de {@link DescripteurZernike#couples} */
		public double[] valeurs() {
			return valeurs.clone();
		}
	}

	/**
	 * Calcule le descripteur de chaque point.
	 *
	 * @param image  image prétraitée (la même pour les deux visites d'une paire)
	 * @param points bifurcations, déjà écartées du bord du champ de vue
	 * @param ctx    paramètres {@code zernike.rayon}, {@code zernike.ordre},
	 *               {@code segmentation.facteurEchelle}
	 * @return un descripteur par point, dans le même ordre (liste non modifiable)
	 */
	public List<Descripteur> decrire(ImageGris image, List<Bifurcation> points, ContexteExecution ctx) {
		Parametres p = ctx.parametres();
		double rayon = p.reel("zernike.rayon") * p.reel("segmentation.facteurEchelle");
		int ordre = p.entier("zernike.ordre");
		if (!(rayon >= 1)) {
			throw new IllegalArgumentException("zernike.rayon doit valoir au moins 1 px : " + rayon);
		}
		if (ordre < 1) {
			throw new IllegalArgumentException("zernike.ordre doit être >= 1 : " + ordre);
		}
		int[][] couples = couples(ordre);
		List<Descripteur> sortie = new ArrayList<>(points.size());
		for (Bifurcation b : points) {
			double[][] z = moments(image, b.x(), b.y(), rayon, couples);
			double[] modules = new double[couples.length];
			for (int k = 0; k < couples.length; k++) {
				modules[k] = Math.hypot(z[k][0], z[k][1]);
			}
			sortie.add(new Descripteur(b, modules));
		}
		return Collections.unmodifiableList(sortie);
	}

	/** @return les couples (n, m) avec 1 ≤ n ≤ ordre, 0 ≤ m ≤ n, n − m pair, par n puis m croissants */
	public static int[][] couples(int ordre) {
		List<int[]> c = new ArrayList<>();
		for (int n = 1; n <= ordre; n++) {
			for (int m = n % 2; m <= n; m += 2) {
				c.add(new int[] { n, m });
			}
		}
		return c.toArray(new int[0][]);
	}

	/**
	 * Moments complexes Z(n,m) de la fenêtre normalisée de rayon {@code rayon} centrée en
	 * (cx, cy) (coordonnées D1). Les pixels hors de l'image sont ignorés.
	 *
	 * @return pour chaque couple, {partie réelle, partie imaginaire}
	 */
	static double[][] moments(ImageGris image, double cx, double cy, double rayon, int[][] couples) {
		int l = image.largeur();
		int h = image.hauteur();
		float[] px = image.donneesBrutes();
		int x0 = Math.max(0, (int) Math.floor(cx - rayon));
		int x1 = Math.min(l - 1, (int) Math.ceil(cx + rayon));
		int y0 = Math.max(0, (int) Math.floor(cy - rayon));
		int y1 = Math.min(h - 1, (int) Math.ceil(cy + rayon));
		double r2 = rayon * rayon;
		// 1) moyenne et écart-type de la fenêtre
		double s = 0;
		double ss = 0;
		int n = 0;
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				double dx = x + 0.5 - cx;
				double dy = y + 0.5 - cy;
				if (dx * dx + dy * dy <= r2) {
					double v = px[y * l + x];
					s += v;
					ss += v * v;
					n++;
				}
			}
		}
		double[][] z = new double[couples.length][2];
		if (n == 0) {
			return z;
		}
		double moyenne = s / n;
		double ecart = Math.sqrt(Math.max(0, ss / n - moyenne * moyenne));
		if (ecart < 1e-12) {
			return z; // fenêtre uniforme : rien à décrire
		}
		// 2) moments de la fenêtre normalisée ; puissances de ρ, cos mθ et sin mθ par récurrence
		int nMax = 0;
		double[][] coef = new double[couples.length][];
		for (int k = 0; k < couples.length; k++) {
			coef[k] = coefficientsRadiaux(couples[k][0], couples[k][1]);
			nMax = Math.max(nMax, couples[k][0]);
		}
		double[] puissance = new double[nMax + 1];
		double[] cosM = new double[nMax + 1];
		double[] sinM = new double[nMax + 1];
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				double dx = x + 0.5 - cx;
				double dy = y + 0.5 - cy;
				double d2 = dx * dx + dy * dy;
				if (d2 > r2) {
					continue;
				}
				double f = (px[y * l + x] - moyenne) / ecart;
				double d = Math.sqrt(d2);
				double rho = d / rayon;
				double c1 = d > 0 ? dx / d : 1; // cos θ
				double s1 = d > 0 ? dy / d : 0; // sin θ
				puissance[0] = 1;
				cosM[0] = 1;
				sinM[0] = 0;
				for (int i = 1; i <= nMax; i++) {
					puissance[i] = puissance[i - 1] * rho;
					cosM[i] = cosM[i - 1] * c1 - sinM[i - 1] * s1;
					sinM[i] = sinM[i - 1] * c1 + cosM[i - 1] * s1;
				}
				for (int k = 0; k < couples.length; k++) {
					int n0 = couples[k][0];
					double r = 0;
					for (int q = 0; q < coef[k].length; q++) {
						r += coef[k][q] * puissance[n0 - 2 * q];
					}
					double fr = f * r;
					int m = couples[k][1];
					z[k][0] += fr * cosM[m];
					z[k][1] -= fr * sinM[m]; // e^(−j m θ)
				}
			}
		}
		for (int k = 0; k < couples.length; k++) {
			double facteur = (couples[k][0] + 1) / (Math.PI * r2);
			z[k][0] *= facteur;
			z[k][1] *= facteur;
		}
		return z;
	}

	/**
	 * Coefficients du polynôme radial R(n,m) : coef[s] multiplie ρ^(n − 2s), pour
	 * s = 0 … (n − m) / 2.
	 */
	static double[] coefficientsRadiaux(int n, int m) {
		if (m < 0 || m > n || (n - m) % 2 != 0) {
			throw new IllegalArgumentException("Couple (n, m) invalide : (" + n + ", " + m + ")");
		}
		int smax = (n - m) / 2;
		double[] c = new double[smax + 1];
		for (int s = 0; s <= smax; s++) {
			double v = factorielle(n - s) / (factorielle(s) * factorielle((n + m) / 2 - s) * factorielle((n - m) / 2 - s));
			c[s] = s % 2 == 0 ? v : -v;
		}
		return c;
	}

	/** R(n,m)(ρ) à partir de ses coefficients. */
	static double radial(double[] coef, int n, double rho) {
		double v = 0;
		for (int s = 0; s < coef.length; s++) {
			v += coef[s] * Math.pow(rho, n - 2 * s);
		}
		return v;
	}

	private static double factorielle(int k) {
		double f = 1;
		for (int i = 2; i <= k; i++) {
			f *= i;
		}
		return f;
	}

	/**
	 * Écrit les descripteurs au format d'échange D6 : en-tête {@code x,y,z1,…,zk}, une ligne
	 * par point (point décimal). Crée les dossiers manquants.
	 */
	public static void ecrireCsv(Path fichier, List<Descripteur> descripteurs) throws IOException {
		if (fichier.getParent() != null) {
			Files.createDirectories(fichier.getParent());
		}
		int k = descripteurs.isEmpty() ? 0 : descripteurs.get(0).valeurs.length;
		try (Writer w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
			StringBuilder entete = new StringBuilder("x,y");
			for (int i = 1; i <= k; i++) {
				entete.append(",z").append(i);
			}
			w.write(entete.append('\n').toString());
			for (Descripteur d : descripteurs) {
				StringBuilder ligne = new StringBuilder(
						String.format(Locale.ROOT, "%.2f,%.2f", d.point.x(), d.point.y()));
				for (double v : d.valeurs) {
					ligne.append(String.format(Locale.ROOT, ",%.6g", v));
				}
				w.write(ligne.append('\n').toString());
			}
		}
	}
}
