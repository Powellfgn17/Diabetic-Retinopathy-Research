package bj.epac.retine.noyau.image;

/**
 * Une image en niveaux de gris accompagnée du masque dans lequel elle se mesure.
 * Transporte les deux ensemble pour que « tout ce qui se mesure se mesure dans le
 * masque » ne puisse pas être oublié : un traitement qui a besoin du masque le reçoit
 * avec l'image. Immuable.
 *
 * <p>{@link ImageGris} et {@link Masque} étant eux-mêmes immuables, le constructeur
 * public les référence sans les recopier : le partage est sûr.</p>
 */
public final class ImageMasquee {

	private final ImageGris image;
	private final Masque masque;

	/**
	 * @param image  l'image
	 * @param masque le masque, de mêmes dimensions que l'image
	 * @throws IllegalArgumentException si les dimensions diffèrent
	 */
	public ImageMasquee(ImageGris image, Masque masque) {
		if (image.largeur() != masque.largeur() || image.hauteur() != masque.hauteur()) {
			throw new IllegalArgumentException("Image " + image.largeur() + "x" + image.hauteur()
					+ " et masque " + masque.largeur() + "x" + masque.hauteur() + " de dimensions différentes");
		}
		this.image = image;
		this.masque = masque;
	}

	/**
	 * Fabrique sans copie : <b>cède la propriété</b> des deux tableaux. L'appelant
	 * ne doit plus y toucher. Réservée aux boucles internes du noyau.
	 *
	 * @param pixels  valeurs de l'image ; propriété transférée
	 * @param masque  valeurs du masque ; propriété transférée
	 * @param largeur largeur en pixels
	 * @param hauteur hauteur en pixels
	 * @return l'image masquée
	 */
	public static ImageMasquee enveloppe(float[] pixels, boolean[] masque, int largeur, int hauteur) {
		return new ImageMasquee(ImageGris.enveloppe(pixels, largeur, hauteur),
				Masque.enveloppe(masque, largeur, hauteur));
	}

	/** @return l'image */
	public ImageGris image() {
		return image;
	}

	/** @return le masque */
	public Masque masque() {
		return masque;
	}

	/** @return la largeur en pixels */
	public int largeur() {
		return image.largeur();
	}

	/** @return la hauteur en pixels */
	public int hauteur() {
		return image.hauteur();
	}
}
