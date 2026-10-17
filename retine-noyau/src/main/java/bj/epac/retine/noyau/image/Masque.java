package bj.epac.retine.noyau.image;

/**
 * Masque binaire immuable (vrai = pixel actif). Séparé de l'image : une image
 * peut avoir plusieurs masques (champ de vue, zones exclues, région d'intérêt).
 * Mêmes conventions de coordonnées que {@link ImageGris}.
 */
public final class Masque {

	private final boolean[] valeurs;
	private final int largeur;
	private final int hauteur;
	private final boolean metadonneeCercle;
	private final double cx;
	private final double cy;
	private final double rayon;

	private Masque(boolean[] valeurs, int largeur, int hauteur, boolean copier,
			boolean metadonneeCercle, double cx, double cy, double rayon) {
		if (largeur <= 0 || hauteur <= 0) {
			throw new IllegalArgumentException("Dimensions invalides : " + largeur + "x" + hauteur);
		}
		if (valeurs.length != largeur * hauteur) {
			throw new IllegalArgumentException("Taille du tableau (" + valeurs.length
					+ ") incohérente avec " + largeur + "x" + hauteur);
		}
		this.valeurs = copier ? valeurs.clone() : valeurs;
		this.largeur = largeur;
		this.hauteur = hauteur;
		this.metadonneeCercle = metadonneeCercle;
		this.cx = cx;
		this.cy = cy;
		this.rayon = rayon;
	}

	/**
	 * Construit un masque en copiant le tableau reçu.
	 *
	 * @param valeurs valeurs, ligne par ligne ; copiées
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @throws IllegalArgumentException si les dimensions sont invalides ou incohérentes avec la taille du tableau
	 */
	public Masque(boolean[] valeurs, int largeur, int hauteur) {
		this(valeurs, largeur, hauteur, true, false, 0, 0, 0);
	}

	/**
	 * Construit un masque sans copie : <b>cède la propriété</b> du tableau.
	 * L'appelant ne doit plus y toucher après l'appel. Réservée aux boucles
	 * internes du noyau.
	 *
	 * @param valeurs valeurs ; propriété transférée
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @return le masque enveloppant directement le tableau
	 * @throws IllegalArgumentException si les dimensions sont invalides ou incohérentes avec la taille du tableau
	 */
	public static Masque enveloppe(boolean[] valeurs, int largeur, int hauteur) {
		return new Masque(valeurs, largeur, hauteur, false, false, 0, 0, 0);
	}

	/**
	 * Masque circulaire (champ de vue). Convention : le centre du pixel (x, y)
	 * est en (x + 0.5, y + 0.5) ; le pixel est actif si
	 * (x+0.5-cx)² + (y+0.5-cy)² ≤ rayon². Le cercle (cx, cy, rayon, dans cette
	 * même convention) est conservé en métadonnée.
	 *
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @param cx      abscisse du centre
	 * @param cy      ordonnée du centre
	 * @param rayon   rayon, en pixels
	 * @return le masque avec métadonnée de cercle
	 */
	public static Masque cercle(int largeur, int hauteur, double cx, double cy, double rayon) {
		if (largeur <= 0 || hauteur <= 0) {
			throw new IllegalArgumentException("Dimensions invalides : " + largeur + "x" + hauteur);
		}
		boolean[] v = new boolean[largeur * hauteur];
		double r2 = rayon * rayon;
		for (int y = 0; y < hauteur; y++) {
			double dy = y + 0.5 - cy;
			for (int x = 0; x < largeur; x++) {
				double dx = x + 0.5 - cx;
				v[y * largeur + x] = dx * dx + dy * dy <= r2;
			}
		}
		return new Masque(v, largeur, hauteur, false, true, cx, cy, rayon);
	}

	/**
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @return un masque dont tous les pixels sont actifs
	 */
	public static Masque plein(int largeur, int hauteur) {
		if (largeur <= 0 || hauteur <= 0) {
			throw new IllegalArgumentException("Dimensions invalides : " + largeur + "x" + hauteur);
		}
		boolean[] v = new boolean[largeur * hauteur];
		java.util.Arrays.fill(v, true);
		return new Masque(v, largeur, hauteur, false, false, 0, 0, 0);
	}

	/**
	 * @param x colonne
	 * @param y ligne
	 * @return vrai si le pixel (x, y) est actif
	 * @throws IndexOutOfBoundsException si (x, y) est hors du masque
	 */
	public boolean estDans(int x, int y) {
		return valeurs[index(x, y)];
	}

	/** @return le nombre de pixels actifs */
	public int nombrePixelsActifs() {
		int n = 0;
		for (boolean b : valeurs) {
			if (b) {
				n++;
			}
		}
		return n;
	}

	/** @return la largeur en pixels */
	public int largeur() {
		return largeur;
	}

	/** @return la hauteur en pixels */
	public int hauteur() {
		return hauteur;
	}

	/** @return vrai si ce masque porte la métadonnée (cx, cy, rayon) */
	public boolean aMetadonneeCercle() {
		return metadonneeCercle;
	}

	/**
	 * @return l'abscisse du centre du cercle
	 * @throws IllegalStateException si le masque n'a pas de métadonnée de cercle
	 */
	public double cx() {
		exigerCercle();
		return cx;
	}

	/**
	 * @return l'ordonnée du centre du cercle
	 * @throws IllegalStateException si le masque n'a pas de métadonnée de cercle
	 */
	public double cy() {
		exigerCercle();
		return cy;
	}

	/**
	 * @return le rayon du cercle
	 * @throws IllegalStateException si le masque n'a pas de métadonnée de cercle
	 */
	public double rayon() {
		exigerCercle();
		return rayon;
	}

	/**
	 * Tableau interne, sans copie. <b>Lecture seule : ne jamais y écrire.</b>
	 * Réservé aux boucles chaudes.
	 *
	 * @return le tableau interne, ligne par ligne
	 */
	public boolean[] donneesBrutes() {
		return valeurs;
	}

	private void exigerCercle() {
		if (!metadonneeCercle) {
			throw new IllegalStateException("Ce masque n'a pas de métadonnée de cercle");
		}
	}

	private int index(int x, int y) {
		if (x < 0 || x >= largeur || y < 0 || y >= hauteur) {
			throw new IndexOutOfBoundsException(
					"Pixel (" + x + ", " + y + ") hors du masque " + largeur + "x" + hauteur);
		}
		return y * largeur + x;
	}
}
