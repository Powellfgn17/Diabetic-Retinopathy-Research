package bj.epac.retine.plugin;

import bj.epac.retine.image.ImageGris;
import ij.process.ImageProcessor;

/**
 * Conversion ImageProcessor <-> ImageGris, dans les deux sens.
 */
public final class AdaptateurImageJ {

	private AdaptateurImageJ() {
	}

	public static ImageGris versImageGris(ImageProcessor ip) {
		throw new UnsupportedOperationException("À implémenter (étape 3)");
	}

	public static ImageProcessor versImageProcessor(ImageGris image) {
		throw new UnsupportedOperationException("À implémenter (étape 3)");
	}
}
