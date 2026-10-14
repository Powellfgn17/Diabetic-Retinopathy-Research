package bj.epac.retine.image;

/**
 * Image binaire : vrai si le pixel est dans le champ de vue, faux sinon.
 * Même convention de coordonnées que {@link ImageGris}.
 *
 * PROPOSITION — à figer à quatre (étape 2).
 */
public final class Masque {

	private final int largeur;
	private final int hauteur;
	private final boolean[] dedans;

	public Masque(int largeur, int hauteur) {
		this.largeur = largeur;
		this.hauteur = hauteur;
		this.dedans = new boolean[largeur * hauteur];
	}

	public int largeur() {
		return largeur;
	}

	public int hauteur() {
		return hauteur;
	}

	public boolean dedans(int x, int y) {
		return dedans[y * largeur + x];
	}

	public void fixer(int x, int y, boolean v) {
		dedans[y * largeur + x] = v;
	}
}
