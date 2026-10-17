package bj.epac.retine.noyau.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ImageCouleurTest {

	private static final int L = 7;
	private static final int H = 13;

	/** Valeurs distinctes par position et par canal : échoue si x et y sont inversés. */
	private static float[] canal(int decalage) {
		float[] d = new float[L * H];
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				d[y * L + x] = (decalage * 10000 + y * 100 + x) / 100000f;
			}
		}
		return d;
	}

	@Test
	public void nonCarreSansInversionXYSurLesTroisCanaux() {
		ImageCouleur img = new ImageCouleur(canal(1), canal(2), canal(3), L, H);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				assertEquals((10000 + y * 100 + x) / 100000f, img.getRouge(x, y), 0.0);
				assertEquals((20000 + y * 100 + x) / 100000f, img.getVert(x, y), 0.0);
				assertEquals((30000 + y * 100 + x) / 100000f, img.getBleu(x, y), 0.0);
			}
		}
	}

	@Test
	public void constructeurCopieLesTroisCanaux() {
		float[] r = canal(1), v = canal(2), b = canal(3);
		ImageCouleur img = new ImageCouleur(r, v, b, L, H);
		r[0] = 9f;
		v[0] = 9f;
		b[0] = 9f;
		assertEquals(10000 / 100000f, img.getRouge(0, 0), 0.0);
		assertEquals(20000 / 100000f, img.getVert(0, 0), 0.0);
		assertEquals(30000 / 100000f, img.getBleu(0, 0), 0.0);
		assertNotSame(r, img.rougeBrut());
	}

	@Test
	public void enveloppeNeCopiePas() {
		float[] r = canal(1), v = canal(2), b = canal(3);
		ImageCouleur img = ImageCouleur.enveloppe(r, v, b, L, H);
		assertSame(r, img.rougeBrut());
		assertSame(v, img.vertBrut());
		assertSame(b, img.bleuBrut());
	}

	@Test
	public void bornesSurLesTroisCanaux() {
		ImageCouleur img = new ImageCouleur(canal(1), canal(2), canal(3), L, H);
		int[][] hors = { { -1, 0 }, { L, 0 }, { 0, H }, { 0, -1 } };
		for (int[] p : hors) {
			for (int c = 0; c < 3; c++) {
				try {
					switch (c) {
						case 0: img.getRouge(p[0], p[1]); break;
						case 1: img.getVert(p[0], p[1]); break;
						default: img.getBleu(p[0], p[1]);
					}
					fail("Exception attendue, canal " + c + " en " + p[0] + "," + p[1]);
				} catch (IndexOutOfBoundsException e) {
					assertTrue(e.getMessage().contains(L + "x" + H));
				}
			}
		}
	}

	@Test
	public void canalVertEstLeVertEtUneCopie() {
		float[] v = canal(2);
		ImageCouleur img = new ImageCouleur(canal(1), v, canal(3), L, H);
		ImageGris g = img.canalVert();
		assertEquals(L, g.largeur());
		assertEquals(H, g.hauteur());
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				assertEquals(img.getVert(x, y), g.get(x, y), 0.0);
			}
		}
		assertNotSame(img.vertBrut(), g.donneesBrutes());
	}

	@Test(expected = IllegalArgumentException.class)
	public void canauxIncoherentsRefuses() {
		new ImageCouleur(new float[4], new float[4], new float[3], 2, 2);
	}
}
