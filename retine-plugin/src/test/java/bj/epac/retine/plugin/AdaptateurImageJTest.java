package bj.epac.retine.plugin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import org.junit.Test;

public class AdaptateurImageJTest {

	@Test
	public void allerRetour8BitsExact() {
		byte[] octets = new byte[256];
		for (int i = 0; i < 256; i++) {
			octets[i] = (byte) i;
		}
		ImagePlus imp = new ImagePlus("t", new ByteProcessor(16, 16, octets));
		ImagePlus retour = AdaptateurImageJ.versImagePlus(AdaptateurImageJ.versGris(imp), "r");
		assertArrayEquals(octets, (byte[]) retour.getProcessor().getPixels());
	}

	@Test
	public void allerRetourRgbExact() {
		int[] argb = new int[256 * 3];
		for (int i = 0; i < 256; i++) {
			argb[i] = 0xff000000 | (i << 16) | ((255 - i) << 8) | ((i * 7) & 0xff);
			argb[256 + i] = 0xff000000 | (((i * 13) & 0xff) << 16) | (i << 8) | (255 - i);
			argb[512 + i] = 0xff000000 | ((255 - i) << 16) | (((i * 3) & 0xff) << 8) | i;
		}
		ImagePlus imp = new ImagePlus("t", new ColorProcessor(256, 3, argb.clone()));
		ImageCouleur c = AdaptateurImageJ.versCouleur(imp);
		ImagePlus retour = AdaptateurImageJ.versImagePlus(c, "r");
		assertArrayEquals(argb, (int[]) retour.getProcessor().getPixels());
	}

	@Test
	public void nonCarre7x13() {
		int l = 7, h = 13;
		byte[] octets = new byte[l * h];
		for (int i = 0; i < octets.length; i++) {
			octets[i] = (byte) (i * 2 + 1);
		}
		ImagePlus imp = new ImagePlus("t", new ByteProcessor(l, h, octets));
		ImageGris g = AdaptateurImageJ.versGris(imp);
		assertEquals(l, g.largeur());
		assertEquals(h, g.hauteur());
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				assertEquals(((y * l + x) * 2 + 1) / 255.0, g.get(x, y), 1e-6);
			}
		}
		ImagePlus retour = AdaptateurImageJ.versImagePlus(g, "r");
		assertEquals(l, retour.getWidth());
		assertEquals(h, retour.getHeight());
		assertArrayEquals(octets, (byte[]) retour.getProcessor().getPixels());
	}

	@Test
	public void versGrisSurRgbLeveException() {
		ImagePlus imp = new ImagePlus("t", new ColorProcessor(2, 2, new int[4]));
		try {
			AdaptateurImageJ.versGris(imp);
			fail("Exception attendue");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("versCouleur"));
		}
	}

	@Test
	public void sortieBorneeDansZeroDeuxCentCinquanteCinq() {
		ImageGris g = new ImageGris(new float[] { -0.5f, 1.7f, 0.5f, 1f }, 2, 2);
		byte[] p = (byte[]) AdaptateurImageJ.versImagePlus(g, "r").getProcessor().getPixels();
		assertArrayEquals(new byte[] { 0, (byte) 255, (byte) 128, (byte) 255 }, p);
	}

	@Test
	public void flottant32bitsAllerRetourSansQuantification() {
		float[] d = { -0.25f, 0.123456f, 1.5f, 0.3333333f };
		ImageGris g = new ImageGris(d, 2, 2);
		ImagePlus imp = AdaptateurImageJ.versImagePlus32bits(g, "f");
		assertArrayEquals(d, (float[]) imp.getProcessor().getPixels(), 0f);
		assertArrayEquals(d, AdaptateurImageJ.versGris(imp).donneesBrutes(), 0f);
	}
}
