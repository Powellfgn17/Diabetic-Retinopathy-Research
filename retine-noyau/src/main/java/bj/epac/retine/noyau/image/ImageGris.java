package bj.epac.retine.noyau.image;

/**
 * Image en niveaux de gris, valeurs normalisées dans [0,1], immuable.
 *
 * <p>Convention : x est la colonne, y la ligne, origine en haut à gauche,
 * stockage ligne par ligne (indice = y * largeur + x). Règle de calcul :
 * {@code float} pour stocker, {@code double} pour calculer.</p>
 */
public final class ImageGris {

	private final float[] pixels;
	private final int largeur;
	private final int hauteur;

	private ImageGris(float[] pixels, int largeur, int hauteur, boolean copier) {
		verifier(pixels, largeur, hauteur);
		this.pixels = copier ? pixels.clone() : pixels;
		this.largeur = largeur;
		this.hauteur = hauteur;
	}

	/**
	 * Construit une image en copiant le tableau reçu (sûr par défaut).
	 *
	 * @param pixels  valeurs, ligne par ligne ; copiées
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @throws IllegalArgumentException si les dimensions sont invalides ou incohérentes avec la taille du tableau
	 */
	public ImageGris(float[] pixels, int largeur, int hauteur) {
		this(pixels, largeur, hauteur, true);
	}

	/**
	 * Construit une image sans copie : <b>cède la propriété</b> du tableau.
	 * L'appelant ne doit plus lire ni écrire son tableau après l'appel.
	 * Réservée aux boucles internes du noyau.
	 *
	 * @param pixels  valeurs, ligne par ligne ; propriété transférée à l'image
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @return l'image enveloppant directement le tableau
	 * @throws IllegalArgumentException si les dimensions sont invalides ou incohérentes avec la taille du tableau
	 */
	public static ImageGris enveloppe(float[] pixels, int largeur, int hauteur) {
		return new ImageGris(pixels, largeur, hauteur, false);
	}

	/**
	 * Crée une image de zéros.
	 *
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @return une image dont tous les pixels valent 0
	 */
	public static ImageGris vide(int largeur, int hauteur) {
		verifierDimensions(largeur, hauteur);
		return new ImageGris(new float[largeur * hauteur], largeur, hauteur, false);
	}

	/**
	 * Valeur du pixel (x, y).
	 *
	 * @param x colonne
	 * @param y ligne
	 * @return la valeur, promue en double
	 * @throws IndexOutOfBoundsException si (x, y) est hors de l'image
	 */
	public double get(int x, int y) {
		return pixels[index(x, y)];
	}

	/**
	 * Valeur du pixel à l'indice linéaire donné (y * largeur + x).
	 *
	 * @param i indice linéaire
	 * @return la valeur, promue en double
	 * @throws IndexOutOfBoundsException si l'indice est hors du tableau
	 */
	public double getIndex(int i) {
		return pixels[i];
	}

	/** @return la largeur en pixels */
	public int largeur() {
		return largeur;
	}

	/** @return la hauteur en pixels */
	public int hauteur() {
		return hauteur;
	}

	/** @return le nombre total de pixels (largeur * hauteur) */
	public int nombrePixels() {
		return pixels.length;
	}

	/**
	 * Tableau interne, sans copie. <b>Lecture seule : ne jamais y écrire</b>,
	 * l'immuabilité de l'image en dépend. Réservé aux boucles chaudes
	 * (convolution, Gabor, Hessien).
	 *
	 * @return le tableau interne, ligne par ligne
	 */
	public float[] donneesBrutes() {
		return pixels;
	}

	/**
	 * @param autre une autre image
	 * @return vrai si largeur et hauteur sont identiques
	 */
	public boolean memesDimensions(ImageGris autre) {
		return largeur == autre.largeur && hauteur == autre.hauteur;
	}

	/** @return la plus petite valeur de l'image */
	public double min() {
		float m = pixels[0];
		for (float v : pixels) {
			if (v < m) {
				m = v;
			}
		}
		return m;
	}

	/** @return la plus grande valeur de l'image */
	public double max() {
		float m = pixels[0];
		for (float v : pixels) {
			if (v > m) {
				m = v;
			}
		}
		return m;
	}

	private int index(int x, int y) {
		if (x < 0 || x >= largeur || y < 0 || y >= hauteur) {
			throw new IndexOutOfBoundsException(
					"Pixel (" + x + ", " + y + ") hors de l'image " + largeur + "x" + hauteur);
		}
		return y * largeur + x;
	}

	private static void verifierDimensions(int largeur, int hauteur) {
		if (largeur <= 0 || hauteur <= 0) {
			throw new IllegalArgumentException("Dimensions invalides : " + largeur + "x" + hauteur);
		}
	}

	private static void verifier(float[] pixels, int largeur, int hauteur) {
		verifierDimensions(largeur, hauteur);
		if (pixels.length != largeur * hauteur) {
			throw new IllegalArgumentException("Taille du tableau (" + pixels.length
					+ ") incohérente avec " + largeur + "x" + hauteur);
		}
	}
}
