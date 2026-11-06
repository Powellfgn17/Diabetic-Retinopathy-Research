package bj.epac.retine.plugin.visualiseur;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Locale;

import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.Masque;

/**
 * Un résultat intermédiaire prêt à afficher : les données (immuables, gardées pour lire
 * la valeur sous la souris) et leur rendu en image écran, calculé une fois.
 *
 * <p>Rendu selon le genre :</p>
 * <ul>
 * <li>{@link Genre#COULEUR} : l'image d'origine ;</li>
 * <li>{@link Genre#GRIS} : niveaux de gris étirés entre les percentiles 0,5 et 99,5 des
 * valeurs dans le masque (l'échelle est rappelée sous l'image) ;</li>
 * <li>{@link Genre#CARTE} : idem avec une palette « chaleur » (réponses du Hessien) ;</li>
 * <li>{@link Genre#CHAMP} : l'image, avec l'extérieur du masque teinté de rouge ;</li>
 * <li>{@link Genre#SUPERPOSE} : l'image en gris, avec les pixels du masque colorés.</li>
 * </ul>
 */
final class EtapeVisible {

	enum Genre {
		COULEUR, GRIS, CARTE, CHAMP, SUPERPOSE
	}

	private static final double P_BAS = 0.5;
	private static final double P_HAUT = 99.5;
	/** Palette « chaleur » (proche de magma) : du noir au jaune pâle. */
	private static final int[][] PALETTE = { { 0, 0, 4 }, { 81, 18, 124 }, { 183, 55, 121 }, { 252, 137, 97 },
			{ 252, 253, 191 } };

	private final int numero;
	private final String nom;
	private final Genre genre;
	private final ImageCouleur couleur;
	private final ImageGris image;
	private final Masque masque;
	private final Color teinte;
	private final double secondes;
	private BufferedImage rendu;
	private String echelle = "";

	private EtapeVisible(int numero, String nom, Genre genre, ImageCouleur couleur, ImageGris image, Masque masque,
			Color teinte, double secondes) {
		this.numero = numero;
		this.nom = nom;
		this.genre = genre;
		this.couleur = couleur;
		this.image = image;
		this.masque = masque;
		this.teinte = teinte;
		this.secondes = secondes;
	}

	static EtapeVisible couleur(int numero, String nom, ImageCouleur c, double secondes) {
		return new EtapeVisible(numero, nom, Genre.COULEUR, c, null, null, null, secondes);
	}

	static EtapeVisible gris(int numero, String nom, ImageGris g, Masque m, boolean carte, double secondes) {
		return new EtapeVisible(numero, nom, carte ? Genre.CARTE : Genre.GRIS, null, g, m, null, secondes);
	}

	static EtapeVisible champ(int numero, String nom, ImageGris fond, Masque m, double secondes) {
		return new EtapeVisible(numero, nom, Genre.CHAMP, null, fond, m, null, secondes);
	}

	static EtapeVisible superpose(int numero, String nom, ImageGris fond, Masque m, Color teinte, double secondes) {
		return new EtapeVisible(numero, nom, Genre.SUPERPOSE, null, fond, m, teinte, secondes);
	}

	int numero() {
		return numero;
	}

	String nom() {
		return nom;
	}

	double secondes() {
		return secondes;
	}

	int largeur() {
		return couleur != null ? couleur.largeur() : image.largeur();
	}

	int hauteur() {
		return couleur != null ? couleur.hauteur() : image.hauteur();
	}

	/** @return l'échelle d'affichage (« noir = …, blanc = … »), vide pour la couleur */
	String echelle() {
		return echelle;
	}

	/** Calcule le rendu s'il ne l'est pas encore (à appeler hors du fil de l'interface). */
	synchronized BufferedImage rendu() {
		if (rendu == null) {
			rendu = calculerRendu();
		}
		return rendu;
	}

	/** @return la description du pixel (x, y) pour la barre d'état */
	String valeurA(int x, int y) {
		if (x < 0 || y < 0 || x >= largeur() || y >= hauteur()) {
			return "";
		}
		if (couleur != null) {
			return String.format(Locale.FRANCE, "R = %.3f   V = %.3f   B = %.3f", couleur.rougeBrut()[y * largeur() + x],
					couleur.vertBrut()[y * largeur() + x], couleur.bleuBrut()[y * largeur() + x]);
		}
		String v = String.format(Locale.FRANCE, "valeur = %.4f", image.get(x, y));
		if (masque == null) {
			return v;
		}
		String dans = masque.estDans(x, y) ? "oui" : "non";
		switch (genre) {
		case CHAMP:
			return v + "   dans le champ de vue : " + dans;
		case SUPERPOSE:
			return v + "   coloré : " + dans;
		default:
			return v + (masque.estDans(x, y) ? "" : "   (hors du champ de vue)");
		}
	}

	private BufferedImage calculerRendu() {
		int l = largeur();
		int h = hauteur();
		BufferedImage b = new BufferedImage(l, h, BufferedImage.TYPE_INT_RGB);
		int[] px = new int[l * h];
		if (genre == Genre.COULEUR) {
			float[] r = couleur.rougeBrut();
			float[] v = couleur.vertBrut();
			float[] bl = couleur.bleuBrut();
			for (int i = 0; i < px.length; i++) {
				px[i] = (octet(r[i]) << 16) | (octet(v[i]) << 8) | octet(bl[i]);
			}
		} else {
			float[] d = image.donneesBrutes();
			boolean[] m = masque != null ? masque.donneesBrutes() : null;
			// pour les masques superposés, l'échelle se calcule dans le champ de vue, pas dans le masque coloré
			double[] bornes = percentiles(d, genre == Genre.SUPERPOSE ? null : m);
			double lo = bornes[0];
			double hi = bornes[1] > bornes[0] ? bornes[1] : bornes[0] + 1e-9;
			echelle = String.format(Locale.FRANCE, "affichage : noir = %.4g   %s = %.4g", lo,
					genre == Genre.CARTE ? "jaune" : "blanc", hi);
			int tr = teinte != null ? teinte.getRed() : 0;
			int tg = teinte != null ? teinte.getGreen() : 0;
			int tb = teinte != null ? teinte.getBlue() : 0;
			for (int i = 0; i < px.length; i++) {
				double t = (d[i] - lo) / (hi - lo);
				t = t < 0 ? 0 : (t > 1 ? 1 : t);
				switch (genre) {
				case CARTE:
					px[i] = (m == null || m[i]) ? palette(t) : 0;
					break;
				case CHAMP: {
					int g = (int) Math.round(t * 255);
					px[i] = m[i] ? gris(g) : rgb((g + 2 * 200) / 3, g / 3, g / 3);
					break;
				}
				case SUPERPOSE: {
					int g = (int) Math.round(t * 255);
					px[i] = m[i] ? rgb((g + 3 * tr) / 4, (g + 3 * tg) / 4, (g + 3 * tb) / 4) : gris(g);
					break;
				}
				default:
					px[i] = gris((int) Math.round(t * 255));
				}
			}
		}
		b.setRGB(0, 0, l, h, px, 0, l);
		return b;
	}

	/** Percentiles 0,5 et 99,5 des valeurs (dans le masque s'il est donné), par histogramme. */
	private static double[] percentiles(float[] d, boolean[] m) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		long n = 0;
		for (int i = 0; i < d.length; i++) {
			if (m == null || m[i]) {
				min = Math.min(min, d[i]);
				max = Math.max(max, d[i]);
				n++;
			}
		}
		if (n == 0 || !(max > min)) {
			return new double[] { n == 0 ? 0 : min, n == 0 ? 1 : min + 1e-9 };
		}
		int bins = 4096;
		long[] hist = new long[bins];
		double pas = (max - min) / bins;
		for (int i = 0; i < d.length; i++) {
			if (m == null || m[i]) {
				int k = (int) ((d[i] - min) / pas);
				hist[k >= bins ? bins - 1 : k]++;
			}
		}
		return new double[] { borne(hist, n, P_BAS, min, pas), borne(hist, n, P_HAUT, min, pas) };
	}

	private static double borne(long[] hist, long n, double p, double min, double pas) {
		long cible = (long) Math.ceil(p / 100.0 * n);
		long cumul = 0;
		for (int k = 0; k < hist.length; k++) {
			cumul += hist[k];
			if (cumul >= cible) {
				return min + (k + 1) * pas;
			}
		}
		return min + hist.length * pas;
	}

	private static int palette(double t) {
		double x = t * (PALETTE.length - 1);
		int k = Math.min(PALETTE.length - 2, (int) x);
		double f = x - k;
		int[] a = PALETTE[k];
		int[] c = PALETTE[k + 1];
		return rgb((int) (a[0] + f * (c[0] - a[0])), (int) (a[1] + f * (c[1] - a[1])), (int) (a[2] + f * (c[2] - a[2])));
	}

	private static int octet(float v) {
		int x = Math.round(v * 255f);
		return x < 0 ? 0 : (x > 255 ? 255 : x);
	}

	private static int gris(int g) {
		return rgb(g, g, g);
	}

	private static int rgb(int r, int g, int b) {
		return (r << 16) | (g << 8) | b;
	}

	@Override
	public String toString() {
		return String.format(Locale.FRANCE, "%2d. %s   (%.1f s)", numero, nom, secondes);
	}
}
