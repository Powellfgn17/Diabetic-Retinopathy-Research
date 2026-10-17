package bj.epac.retine.noyau.image;

/**
 * Image couleur RGB, trois canaux {@code float} normalisés dans [0,1], immuable.
 * Mêmes conventions que {@link ImageGris} (indice = y * largeur + x).
 */
public final class ImageCouleur {

	private final float[] rouge;
	private final float[] vert;
	private final float[] bleu;
	private final int largeur;
	private final int hauteur;

	private ImageCouleur(float[] rouge, float[] vert, float[] bleu, int largeur, int hauteur, boolean copier) {
		if (largeur <= 0 || hauteur <= 0) {
			throw new IllegalArgumentException("Dimensions invalides : " + largeur + "x" + hauteur);
		}
		int n = largeur * hauteur;
		if (rouge.length != n || vert.length != n || bleu.length != n) {
			throw new IllegalArgumentException("Taille des canaux incohérente avec " + largeur + "x" + hauteur);
		}
		this.rouge = copier ? rouge.clone() : rouge;
		this.vert = copier ? vert.clone() : vert;
		this.bleu = copier ? bleu.clone() : bleu;
		this.largeur = largeur;
		this.hauteur = hauteur;
	}

	/**
	 * Construit une image en copiant les trois canaux.
	 *
	 * @param rouge   canal rouge, ligne par ligne ; copié
	 * @param vert    canal vert ; copié
	 * @param bleu    canal bleu ; copié
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @throws IllegalArgumentException si les dimensions sont invalides ou incohérentes avec un canal
	 */
	public ImageCouleur(float[] rouge, float[] vert, float[] bleu, int largeur, int hauteur) {
		this(rouge, vert, bleu, largeur, hauteur, true);
	}

	/**
	 * Construit une image sans copie : <b>cède la propriété</b> des trois
	 * tableaux. L'appelant ne doit plus y toucher après l'appel. Réservée aux
	 * boucles internes du noyau.
	 *
	 * @param rouge   canal rouge ; propriété transférée
	 * @param vert    canal vert ; propriété transférée
	 * @param bleu    canal bleu ; propriété transférée
	 * @param largeur largeur en pixels, strictement positive
	 * @param hauteur hauteur en pixels, strictement positive
	 * @return l'image enveloppant directement les tableaux
	 * @throws IllegalArgumentException si les dimensions sont invalides ou incohérentes avec un canal
	 */
	public static ImageCouleur enveloppe(float[] rouge, float[] vert, float[] bleu, int largeur, int hauteur) {
		return new ImageCouleur(rouge, vert, bleu, largeur, hauteur, false);
	}

	/**
	 * @param x colonne
	 * @param y ligne
	 * @return la valeur du canal rouge en (x, y)
	 * @throws IndexOutOfBoundsException si (x, y) est hors de l'image
	 */
	public double getRouge(int x, int y) {
		return rouge[index(x, y)];
	}

	/**
	 * @param x colonne
	 * @param y ligne
	 * @return la valeur du canal vert en (x, y)
	 * @throws IndexOutOfBoundsException si (x, y) est hors de l'image
	 */
	public double getVert(int x, int y) {
		return vert[index(x, y)];
	}

	/**
	 * @param x colonne
	 * @param y ligne
	 * @return la valeur du canal bleu en (x, y)
	 * @throws IndexOutOfBoundsException si (x, y) est hors de l'image
	 */
	public double getBleu(int x, int y) {
		return bleu[index(x, y)];
	}

	/** @return la largeur en pixels */
	public int largeur() {
		return largeur;
	}

	/** @return la hauteur en pixels */
	public int hauteur() {
		return hauteur;
	}

	/** @return le nombre de pixels (largeur * hauteur) */
	public int nombrePixels() {
		return rouge.length;
	}

	/**
	 * Tableau interne du canal rouge. <b>Lecture seule : ne jamais y écrire.</b>
	 * Réservé aux boucles chaudes.
	 *
	 * @return le tableau interne
	 */
	public float[] rougeBrut() {
		return rouge;
	}

	/**
	 * Tableau interne du canal vert. <b>Lecture seule : ne jamais y écrire.</b>
	 * Réservé aux boucles chaudes.
	 *
	 * @return le tableau interne
	 */
	public float[] vertBrut() {
		return vert;
	}

	/**
	 * Tableau interne du canal bleu. <b>Lecture seule : ne jamais y écrire.</b>
	 * Réservé aux boucles chaudes.
	 *
	 * @return le tableau interne
	 */
	public float[] bleuBrut() {
		return bleu;
	}

	/**
	 * Extraction simple du canal vert, sans traitement (copie). Le choix
	 * méthodologique de ce canal relève de l'étape {@code CanalVert}.
	 *
	 * @return une copie du canal vert sous forme d'{@link ImageGris}
	 */
	public ImageGris canalVert() {
		return new ImageGris(vert, largeur, hauteur);
	}

	private int index(int x, int y) {
		if (x < 0 || x >= largeur || y < 0 || y >= hauteur) {
			throw new IndexOutOfBoundsException(
					"Pixel (" + x + ", " + y + ") hors de l'image " + largeur + "x" + hauteur);
		}
		return y * largeur + x;
	}
}
