package bj.epac.retine.plugin.visualiseur;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import bj.epac.retine.bifurcation.Bifurcation;

import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.Masque;

/**
 * Un résultat intermédiaire d'une visite, prêt à afficher : les données (immuables,
 * gardées pour lire la valeur sous la souris) et leur rendu écran, calculé une fois.
 *
 * <p>Rendu selon le genre :</p>
 * <ul>
 * <li>{@link Genre#COULEUR} : l'image d'origine (RGB) ;</li>
 * <li>{@link Genre#GRIS} : niveaux de gris étirés entre les percentiles 0,5 et 99,5 des
 * valeurs dans le masque (l'échelle est rappelée sous l'image) ;</li>
 * <li>{@link Genre#CARTE} : idem avec une palette « chaleur » (réponses du Hessien) ;</li>
 * <li>{@link Genre#CHAMP} : l'image, avec l'extérieur du masque teinté de rouge ;</li>
 * <li>{@link Genre#SUPERPOSE} : l'image en gris, avec les pixels du masque colorés ;</li>
 * <li>{@link Genre#SQUELETTE} : l'image en gris, les vaisseaux en vert pâle et le squelette
 * en trait foncé, épaissi à 3 px à l'affichage seulement pour rester visible ;</li>
 * <li>{@link Genre#POINTS} : l'image en gris, le squelette en violet pâle, un anneau bleu
 * autour de chaque bifurcation et orange autour de chaque croisement ; en option, le cercle
 * de la fenêtre de Zernike autour de chaque point.</li>
 * </ul>
 * <p>Sauf l'image d'origine, les rendus sont en 8 bits (gris ou palette) : 4 fois moins de
 * mémoire qu'en RGB, ce qui compte avec deux visites de 2912 × 2912 et 16 étapes.</p>
 */
final class EtapeVisible {

	enum Genre {
		COULEUR, GRIS, CARTE, CHAMP, SUPERPOSE, SQUELETTE, POINTS
	}

	private static final double P_BAS = 0.5;
	private static final double P_HAUT = 99.5;
	/** Palette « chaleur » (proche de magma) : du noir au jaune pâle. */
	private static final int[][] PALETTE = { { 0, 0, 4 }, { 81, 18, 124 }, { 183, 55, 121 }, { 252, 137, 97 },
			{ 252, 253, 191 } };
	private static final Color ROUGE_HORS_CHAMP = new Color(200, 0, 0);
	private static final Color VERT_PALE = new Color(120, 220, 120);
	private static final Color TRAIT_SQUELETTE = new Color(150, 0, 110);
	private static final Color BIFURCATION = new Color(0, 90, 255);
	private static final Color CROISEMENT = new Color(255, 120, 0);
	private static final Color FENETRE = new Color(0, 170, 160);
	/** Anneau autour d'un point : rayons intérieur et extérieur (px). */
	private static final double ANNEAU_MIN = 7;
	private static final double ANNEAU_MAX = 11;

	private final int numero;
	private final String nom;
	private final Genre genre;
	private final ImageGris image;
	private final Masque masque;
	/** Pour le squelette : le masque des vaisseaux, affiché dessous. */
	private final Masque masqueFond;
	private final Color teinte;
	/** Pour les points : la liste affichée et le rayon de la fenêtre de Zernike (0 : pas de cercle). */
	private List<Bifurcation> points = Collections.emptyList();
	private double rayonFenetre;
	private final double secondes;
	private final int largeur;
	private final int hauteur;
	/** Libérée après le rendu : la valeur sous la souris est relue dans le rendu RGB. */
	private ImageCouleur couleur;
	private BufferedImage rendu;
	private String echelle = "";

	private EtapeVisible(int numero, String nom, Genre genre, ImageCouleur couleur, ImageGris image, Masque masque,
			Color teinte, double secondes) {
		this(numero, nom, genre, couleur, image, masque, null, teinte, secondes);
	}

	private EtapeVisible(int numero, String nom, Genre genre, ImageCouleur couleur, ImageGris image, Masque masque,
			Masque masqueFond, Color teinte, double secondes) {
		this.masqueFond = masqueFond;
		this.numero = numero;
		this.nom = nom;
		this.genre = genre;
		this.couleur = couleur;
		this.image = image;
		this.masque = masque;
		this.teinte = teinte;
		this.secondes = secondes;
		this.largeur = couleur != null ? couleur.largeur() : image.largeur();
		this.hauteur = couleur != null ? couleur.hauteur() : image.hauteur();
	}

	static EtapeVisible couleur(int numero, String nom, ImageCouleur c, double secondes) {
		return new EtapeVisible(numero, nom, Genre.COULEUR, c, null, null, null, secondes);
	}

	static EtapeVisible gris(int numero, String nom, ImageGris g, Masque m, boolean carte, double secondes) {
		return new EtapeVisible(numero, nom, carte ? Genre.CARTE : Genre.GRIS, null, g, m, null, secondes);
	}

	static EtapeVisible champ(int numero, String nom, ImageGris fond, Masque m, double secondes) {
		return new EtapeVisible(numero, nom, Genre.CHAMP, null, fond, m, ROUGE_HORS_CHAMP, secondes);
	}

	static EtapeVisible superpose(int numero, String nom, ImageGris fond, Masque m, Color teinte, double secondes) {
		return new EtapeVisible(numero, nom, Genre.SUPERPOSE, null, fond, m, teinte, secondes);
	}

	static EtapeVisible squelette(int numero, String nom, ImageGris fond, Masque vaisseaux, Masque squelette,
			double secondes) {
		return new EtapeVisible(numero, nom, Genre.SQUELETTE, null, fond, squelette, vaisseaux, TRAIT_SQUELETTE, secondes);
	}

	static EtapeVisible points(int numero, String nom, ImageGris fond, Masque squelette, List<Bifurcation> points,
			double rayonFenetre, double secondes) {
		EtapeVisible e = new EtapeVisible(numero, nom, Genre.POINTS, null, fond, squelette, null, secondes);
		e.points = points;
		e.rayonFenetre = rayonFenetre;
		return e;
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
		return largeur;
	}

	int hauteur() {
		return hauteur;
	}

	/** @return l'échelle d'affichage (« noir = …, blanc = … »), vide pour la couleur */
	synchronized String echelle() {
		return echelle;
	}

	/** Calcule le rendu s'il ne l'est pas encore (à appeler hors du fil de l'interface). */
	synchronized BufferedImage rendu() {
		if (rendu == null) {
			rendu = calculerRendu();
			couleur = null;
		}
		return rendu;
	}

	/** @return la description du pixel (x, y) pour la barre d'état */
	String valeurA(int x, int y) {
		if (x < 0 || y < 0 || x >= largeur || y >= hauteur) {
			return "";
		}
		if (genre == Genre.COULEUR) {
			int c = rendu().getRGB(x, y);
			return String.format(Locale.FRANCE, "R = %.3f   V = %.3f   B = %.3f", ((c >> 16) & 0xff) / 255.0,
					((c >> 8) & 0xff) / 255.0, (c & 0xff) / 255.0);
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
		case SQUELETTE:
			return v + "   squelette : " + dans + "   vaisseau : " + (masqueFond.estDans(x, y) ? "oui" : "non");
		case POINTS:
			return v + "   squelette : " + dans + plusProche(x + 0.5, y + 0.5);
		default:
			return v + (masque.estDans(x, y) ? "" : "   (hors du champ de vue)");
		}
	}

	/** @return « point le plus proche » si un point est à moins de 30 px, sinon vide */
	private String plusProche(double x, double y) {
		Bifurcation meilleur = null;
		double d = 30;
		for (Bifurcation b : points) {
			double e = Math.hypot(b.x() - x, b.y() - y);
			if (e < d) {
				d = e;
				meilleur = b;
			}
		}
		return meilleur == null ? ""
				: String.format(Locale.FRANCE, "   %s en (%.1f ; %.1f), à %.1f px", meilleur.type().nomCsv(),
						meilleur.x(), meilleur.y(), d);
	}

	private BufferedImage calculerRendu() {
		int n = largeur * hauteur;
		if (genre == Genre.COULEUR) {
			BufferedImage b = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
			int[] px = new int[n];
			float[] r = couleur.rougeBrut();
			float[] v = couleur.vertBrut();
			float[] bl = couleur.bleuBrut();
			for (int i = 0; i < n; i++) {
				px[i] = (octet(r[i]) << 16) | (octet(v[i]) << 8) | octet(bl[i]);
			}
			b.setRGB(0, 0, largeur, hauteur, px, 0, largeur);
			return b;
		}
		float[] d = image.donneesBrutes();
		boolean[] m = masque != null ? masque.donneesBrutes() : null;
		// pour les masques superposés, l'échelle se calcule sur toute l'image, pas dans le masque coloré
		double[] bornes = percentiles(d,
				genre == Genre.SUPERPOSE || genre == Genre.SQUELETTE || genre == Genre.POINTS ? null : m);
		double lo = bornes[0];
		double hi = bornes[1] > bornes[0] ? bornes[1] : bornes[0] + 1e-9;
		echelle = String.format(Locale.FRANCE, "affichage : noir = %.4g   %s = %.4g", lo,
				genre == Genre.CARTE ? "jaune" : "blanc", hi);

		BufferedImage b;
		switch (genre) {
		case GRIS:
			b = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_BYTE_GRAY);
			break;
		case CARTE:
			b = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_BYTE_INDEXED, paletteChaleur());
			break;
		case SQUELETTE:
			b = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_BYTE_INDEXED, paletteSquelette());
			break;
		case POINTS:
			b = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_BYTE_INDEXED, palettePoints());
			break;
		default: // CHAMP, SUPERPOSE : 128 niveaux de gris, puis les mêmes 128 niveaux teintés
			b = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_BYTE_INDEXED, paletteTeintee(teinte));
		}
		byte[] px = ((DataBufferByte) b.getRaster().getDataBuffer()).getData();
		boolean[] trait = genre == Genre.SQUELETTE || genre == Genre.POINTS ? epaissir(m, largeur, hauteur) : null;
		boolean[] fond = genre == Genre.SQUELETTE ? masqueFond.donneesBrutes() : null;
		for (int i = 0; i < n; i++) {
			double t = (d[i] - lo) / (hi - lo);
			t = t < 0 ? 0 : (t > 1 ? 1 : t);
			switch (genre) {
			case GRIS:
				px[i] = (byte) Math.round(t * 255);
				break;
			case CARTE:
				px[i] = (byte) ((m == null || m[i]) ? Math.round(t * 255) : 0);
				break;
			case SQUELETTE: // 0–84 gris, 85–169 vaisseau (vert pâle), 255 squelette
				px[i] = (byte) (trait[i] ? 255 : Math.round(t * 84) + (fond[i] ? 85 : 0));
				break;
			case POINTS: // 0–84 gris, 85–169 squelette (violet pâle), puis les anneaux dessinés ensuite
				px[i] = (byte) (Math.round(t * 84) + (trait[i] ? 85 : 0));
				break;
			case CHAMP: // teinté = hors du champ
				px[i] = (byte) (Math.round(t * 127) + (m[i] ? 0 : 128));
				break;
			default: // SUPERPOSE : teinté = dans le masque
				px[i] = (byte) (Math.round(t * 127) + (m[i] ? 128 : 0));
			}
		}
		if (genre == Genre.POINTS) {
			for (Bifurcation p : points) {
				if (rayonFenetre > 0) {
					anneau(px, p.x(), p.y(), rayonFenetre - 1, rayonFenetre + 1, (byte) 172);
				}
			}
			for (Bifurcation p : points) {
				anneau(px, p.x(), p.y(), ANNEAU_MIN, ANNEAU_MAX,
						(byte) (p.type() == Bifurcation.Type.BIFURCATION ? 170 : 171));
			}
		}
		return b;
	}

	/** Colorie avec l'indice {@code c} les pixels dont le centre est entre {@code r0} et {@code r1} de (x, y). */
	private void anneau(byte[] px, double x, double y, double r0, double r1, byte c) {
		for (int j = (int) Math.floor(y - r1); j <= y + r1; j++) {
			for (int i = (int) Math.floor(x - r1); i <= x + r1; i++) {
				if (i < 0 || j < 0 || i >= largeur || j >= hauteur) {
					continue;
				}
				double d = Math.hypot(i + 0.5 - x, j + 0.5 - y);
				if (d >= r0 && d <= r1) {
					px[j * largeur + i] = c;
				}
			}
		}
	}

	/** 0–84 : gris ; 85–169 : gris mêlé de violet pâle (squelette) ; 170, 171, 172 : bifurcation, croisement, fenêtre. */
	private static IndexColorModel palettePoints() {
		byte[] r = new byte[256];
		byte[] g = new byte[256];
		byte[] bl = new byte[256];
		for (int k = 0; k < 85; k++) {
			int v = Math.round(k * 255f / 84f);
			r[k] = g[k] = bl[k] = (byte) v;
			r[85 + k] = (byte) ((v + 2 * 200) / 3);
			g[85 + k] = (byte) ((v + 2 * 120) / 3);
			bl[85 + k] = (byte) ((v + 2 * 210) / 3);
		}
		Color[] c = { BIFURCATION, CROISEMENT, FENETRE };
		for (int k = 0; k < 3; k++) {
			r[170 + k] = (byte) c[k].getRed();
			g[170 + k] = (byte) c[k].getGreen();
			bl[170 + k] = (byte) c[k].getBlue();
		}
		return new IndexColorModel(8, 256, r, g, bl);
	}

	private static IndexColorModel paletteChaleur() {
		byte[] r = new byte[256];
		byte[] g = new byte[256];
		byte[] bl = new byte[256];
		for (int i = 0; i < 256; i++) {
			double x = i / 255.0 * (PALETTE.length - 1);
			int k = Math.min(PALETTE.length - 2, (int) x);
			double f = x - k;
			int[] a = PALETTE[k];
			int[] c = PALETTE[k + 1];
			r[i] = (byte) (a[0] + f * (c[0] - a[0]));
			g[i] = (byte) (a[1] + f * (c[1] - a[1]));
			bl[i] = (byte) (a[2] + f * (c[2] - a[2]));
		}
		return new IndexColorModel(8, 256, r, g, bl);
	}

	/** Indices 0–84 : gris ; 85–169 : gris mêlé de vert pâle ; 170–255 : trait du squelette. */
	private static IndexColorModel paletteSquelette() {
		byte[] r = new byte[256];
		byte[] g = new byte[256];
		byte[] bl = new byte[256];
		for (int k = 0; k < 85; k++) {
			int v = Math.round(k * 255f / 84f);
			r[k] = g[k] = bl[k] = (byte) v;
			r[85 + k] = (byte) ((v + VERT_PALE.getRed()) / 2);
			g[85 + k] = (byte) ((v + VERT_PALE.getGreen()) / 2);
			bl[85 + k] = (byte) ((v + VERT_PALE.getBlue()) / 2);
		}
		for (int k = 170; k < 256; k++) {
			r[k] = (byte) TRAIT_SQUELETTE.getRed();
			g[k] = (byte) TRAIT_SQUELETTE.getGreen();
			bl[k] = (byte) TRAIT_SQUELETTE.getBlue();
		}
		return new IndexColorModel(8, 256, r, g, bl);
	}

	/** Dilatation 3 × 3 pour l'affichage : un trait d'un pixel disparaîtrait dès qu'on dézoome. */
	private static boolean[] epaissir(boolean[] m, int l, int h) {
		boolean[] e = new boolean[m.length];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				if (!m[y * l + x]) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						int xx = x + dx, yy = y + dy;
						if (xx >= 0 && yy >= 0 && xx < l && yy < h) {
							e[yy * l + xx] = true;
						}
					}
				}
			}
		}
		return e;
	}

	/** Indices 0–127 : gris ; 128–255 : le même gris mélangé à 75 % avec la teinte. */
	private static IndexColorModel paletteTeintee(Color t) {
		byte[] r = new byte[256];
		byte[] g = new byte[256];
		byte[] bl = new byte[256];
		for (int k = 0; k < 128; k++) {
			int v = Math.round(k * 255f / 127f);
			r[k] = g[k] = bl[k] = (byte) v;
			r[128 + k] = (byte) ((v + 3 * t.getRed()) / 4);
			g[128 + k] = (byte) ((v + 3 * t.getGreen()) / 4);
			bl[128 + k] = (byte) ((v + 3 * t.getBlue()) / 4);
		}
		return new IndexColorModel(8, 256, r, g, bl);
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

	private static int octet(float v) {
		int x = Math.round(v * 255f);
		return x < 0 ? 0 : (x > 255 ? 255 : x);
	}
}
