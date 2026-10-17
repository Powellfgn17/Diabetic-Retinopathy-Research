package bj.epac.retine.plugin;

import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.Masque;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * Transport pur entre ImageJ et le noyau : aucun traitement, aucune décision
 * méthodologique. Entrée : division par 255 (8 bits) ou 65535 (16 bits).
 * Sortie : multiplication par 255, arrondi, bornage dans [0,255].
 *
 * <p>Cas non pris en charge, volontairement rejetés (décision à trancher par
 * le projet) : images 32 bits, images indexées, piles de plusieurs tranches.</p>
 */
public final class AdaptateurImageJ {

	private AdaptateurImageJ() {
	}

	/**
	 * Convertit une image RGB d'ImageJ en {@link ImageCouleur} normalisée.
	 *
	 * @param imp image de type RGB, à une seule tranche
	 * @return l'image couleur, canaux dans [0,1]
	 * @throws IllegalArgumentException si l'image n'est pas RGB ou a plusieurs tranches
	 */
	public static ImageCouleur versCouleur(ImagePlus imp) {
		exigerUneTranche(imp);
		if (imp.getType() != ImagePlus.COLOR_RGB) {
			throw new IllegalArgumentException(
					"versCouleur attend une image RGB, reçu : " + nomType(imp) + ".");
		}
		int l = imp.getWidth();
		int h = imp.getHeight();
		int[] argb = (int[]) imp.getProcessor().getPixels();
		float[] r = new float[l * h];
		float[] v = new float[l * h];
		float[] b = new float[l * h];
		for (int i = 0; i < argb.length; i++) {
			r[i] = ((argb[i] >> 16) & 0xff) / 255f;
			v[i] = ((argb[i] >> 8) & 0xff) / 255f;
			b[i] = (argb[i] & 0xff) / 255f;
		}
		return ImageCouleur.enveloppe(r, v, b, l, h);
	}

	/**
	 * Convertit une image 8 ou 16 bits d'ImageJ en {@link ImageGris} normalisée.
	 *
	 * @param imp image 8 ou 16 bits, à une seule tranche
	 * @return l'image en niveaux de gris, valeurs dans [0,1]
	 * @throws IllegalArgumentException si l'image est RGB (utiliser {@link #versCouleur}, la
	 *                                  fusion des canaux étant une décision du noyau), 32 bits,
	 *                                  indexée, ou a plusieurs tranches
	 */
	public static ImageGris versGris(ImagePlus imp) {
		exigerUneTranche(imp);
		int type = imp.getType();
		if (type == ImagePlus.COLOR_RGB) {
			throw new IllegalArgumentException("versGris refuse une image RGB : utiliser versCouleur(), "
					+ "le choix du canal (vert) appartient au noyau, pas à l'adaptateur.");
		}
		if (type != ImagePlus.GRAY8 && type != ImagePlus.GRAY16) {
			throw new IllegalArgumentException(
					"versGris prend en charge les images 8 et 16 bits, reçu : " + nomType(imp) + ".");
		}
		int l = imp.getWidth();
		int h = imp.getHeight();
		ImageProcessor ip = imp.getProcessor();
		float echelle = type == ImagePlus.GRAY8 ? 255f : 65535f;
		float[] pixels = new float[l * h];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = ip.get(i) / echelle;
		}
		return ImageGris.enveloppe(pixels, l, h);
	}

	/**
	 * Dénormalise une image en niveaux de gris vers 8 bits (×255, arrondi, bornage).
	 *
	 * @param img   image source
	 * @param titre titre de l'{@link ImagePlus}
	 * @return une image 8 bits
	 */
	public static ImagePlus versImagePlus(ImageGris img, String titre) {
		return new ImagePlus(titre, new ByteProcessor(img.largeur(), img.hauteur(), versOctets(img.donneesBrutes())));
	}

	/**
	 * Dénormalise une image couleur vers RGB 8 bits par canal (×255, arrondi, bornage).
	 *
	 * @param img   image source
	 * @param titre titre de l'{@link ImagePlus}
	 * @return une image RGB
	 */
	public static ImagePlus versImagePlus(ImageCouleur img, String titre) {
		float[] r = img.rougeBrut();
		float[] v = img.vertBrut();
		float[] b = img.bleuBrut();
		int[] argb = new int[r.length];
		for (int i = 0; i < argb.length; i++) {
			argb[i] = 0xff000000 | (borner(r[i]) << 16) | (borner(v[i]) << 8) | borner(b[i]);
		}
		return new ImagePlus(titre, new ColorProcessor(img.largeur(), img.hauteur(), argb));
	}

	/**
	 * Convertit un masque en image 8 bits (0 / 255) pour contrôle visuel.
	 *
	 * @param masque masque source
	 * @param titre  titre de l'{@link ImagePlus}
	 * @return une image 8 bits, 255 là où le masque est actif
	 */
	public static ImagePlus versImagePlus(Masque masque, String titre) {
		boolean[] m = masque.donneesBrutes();
		byte[] octets = new byte[m.length];
		for (int i = 0; i < m.length; i++) {
			octets[i] = m[i] ? (byte) 255 : 0;
		}
		return new ImagePlus(titre, new ByteProcessor(masque.largeur(), masque.hauteur(), octets));
	}

	private static byte[] versOctets(float[] valeurs) {
		byte[] octets = new byte[valeurs.length];
		for (int i = 0; i < octets.length; i++) {
			octets[i] = (byte) borner(valeurs[i]);
		}
		return octets;
	}

	private static int borner(float valeur) {
		long v = Math.round(valeur * 255.0);
		return (int) Math.max(0, Math.min(255, v));
	}

	private static void exigerUneTranche(ImagePlus imp) {
		if (imp.getStackSize() != 1) {
			throw new IllegalArgumentException(
					"Piles non prises en charge (" + imp.getStackSize() + " tranches).");
		}
	}

	private static String nomType(ImagePlus imp) {
		switch (imp.getType()) {
			case ImagePlus.GRAY8: return "8 bits";
			case ImagePlus.GRAY16: return "16 bits";
			case ImagePlus.GRAY32: return "32 bits";
			case ImagePlus.COLOR_256: return "8 bits indexé";
			case ImagePlus.COLOR_RGB: return "RGB";
			default: return "type " + imp.getType();
		}
	}
}
