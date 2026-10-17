package bj.epac.retine.noyau.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class ImageGrisTest {

	@Test
	public void nonCarreeSansInversionXY() {
		int l = 7, h = 13;
		float[] d = new float[l * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				d[y * l + x] = (y * 100 + x) / 1000f;
			}
		}
		ImageGris img = new ImageGris(d, l, h);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				assertEquals((y * 100 + x) / 1000f, img.get(x, y), 0.0);
				assertEquals(img.get(x, y), img.getIndex(y * l + x), 0.0);
			}
		}
	}

	@Test
	public void constructeurCopieLeTableau() {
		float[] src = { 0.1f, 0.2f, 0.3f, 0.4f };
		ImageGris img = new ImageGris(src, 2, 2);
		src[0] = 0.9f;
		assertEquals(0.1f, img.get(0, 0), 0.0);
		assertNotSame(src, img.donneesBrutes());
	}

	@Test
	public void enveloppeNeCopiePas() {
		float[] src = new float[6];
		assertSame(src, ImageGris.enveloppe(src, 3, 2).donneesBrutes());
	}

	@Test
	public void bornesLeveException() {
		ImageGris img = ImageGris.vide(4, 3);
		int[][] hors = { { -1, 0 }, { 4, 0 }, { 0, 3 }, { 0, -1 } };
		for (int[] p : hors) {
			try {
				img.get(p[0], p[1]);
				org.junit.Assert.fail("Exception attendue pour " + p[0] + "," + p[1]);
			} catch (IndexOutOfBoundsException e) {
				org.junit.Assert.assertTrue(e.getMessage().contains("4x3"));
			}
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void tailleIncoherenteRefusee() {
		new ImageGris(new float[5], 2, 3);
	}

	@Test(expected = IllegalArgumentException.class)
	public void dimensionsNulleRefusees() {
		ImageGris.vide(0, 3);
	}

	@Test
	public void minMaxEtDimensions() {
		ImageGris a = new ImageGris(new float[] { 0.2f, 0.9f, 0.5f, 0f }, 2, 2);
		assertEquals(0.0, a.min(), 0.0);
		assertEquals(0.9f, a.max(), 0.0);
		assertEquals(4, a.nombrePixels());
		org.junit.Assert.assertTrue(a.memesDimensions(ImageGris.vide(2, 2)));
		org.junit.Assert.assertFalse(a.memesDimensions(ImageGris.vide(4, 1)));
	}
}
