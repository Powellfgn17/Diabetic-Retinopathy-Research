package bj.epac.retine.image;

/**
 * Image en niveaux de gris, valeurs normalisées entre 0 et 1.
 *
 * Convention de coordonnées : x est la colonne (0 à largeur-1), y la ligne
 * (0 à hauteur-1), origine en haut à gauche, stockage ligne par ligne
 * (indice = y * largeur + x).
 *
 * PROPOSITION — à figer à quatre (étape 2) avant d'écrire la suite.
 */
public final class ImageGris {

	private final int largeur;
	private final int hauteur;
	private final float[] donnees;

	public ImageGris(int largeur, int hauteur) {
		this(largeur, hauteur, new float[largeur * hauteur]);
	}

	public ImageGris(int largeur, int hauteur, float[] donnees) {
		if (largeur <= 0 || hauteur <= 0) {
			throw new IllegalArgumentException("Dimensions invalides : " + largeur + "x" + hauteur);
		}
		if (donnees.length != largeur * hauteur) {
			throw new IllegalArgumentException("Taille des données incohérente avec " + largeur + "x" + hauteur);
		}
		this.largeur = largeur;
		this.hauteur = hauteur;
		this.donnees = donnees;
	}

	public int largeur() {
		return largeur;
	}

	public int hauteur() {
		return hauteur;
	}

	public float valeur(int x, int y) {
		return donnees[indice(x, y)];
	}

	public void fixer(int x, int y, float v) {
		donnees[indice(x, y)] = v;
	}

	/** Accès direct au tableau, pour les boucles rapides. */
	public float[] donnees() {
		return donnees;
	}

	private int indice(int x, int y) {
		if (x < 0 || x >= largeur || y < 0 || y >= hauteur) {
			throw new IndexOutOfBoundsException("(" + x + ", " + y + ") hors de " + largeur + "x" + hauteur);
		}
		return y * largeur + x;
	}
}
