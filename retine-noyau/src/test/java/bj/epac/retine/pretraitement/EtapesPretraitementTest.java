package bj.epac.retine.pretraitement;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;

import org.junit.Test;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.ImageMasquee;
import bj.epac.retine.noyau.image.Masque;
import bj.epac.retine.parametres.Parametres;

public class EtapesPretraitementTest {

	private static ContexteExecution ctx() {
		return new ContexteExecution(Parametres.defauts(), 1L, Paths.get("."));
	}

	/** Disque clair de valeur 0.6 sur fond noir, avec dégradé horizontal optionnel. */
	private static ImageCouleur disque(int l, int h, double cx, double cy, double r, double pente) {
		float[] c = new float[l * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				double dx = x + 0.5 - cx;
				double dy = y + 0.5 - cy;
				if (dx * dx + dy * dy <= r * r) {
					c[y * l + x] = (float) (0.4 + pente * x / l);
				}
			}
		}
		return ImageCouleur.enveloppe(c, c.clone(), c.clone(), l, h);
	}

	@Test
	public void masqueRetrouveLeDisqueALErosionPres() {
		int l = 300, h = 260;
		ImageCouleur img = disque(l, h, 150, 130, 100, 0);
		Masque m = new MasqueChampVision().executer(img, ctx());
		assertEquals(l, m.largeur());
		assertEquals(h, m.hauteur());
		assertTrue(m.estDans(150, 130));
		assertFalse(m.estDans(5, 5));
		assertFalse(m.estDans(150 + 98, 130)); // dans le disque d'origine, mais dans l'anneau érodé (7 px)
		assertTrue(m.aMetadonneeCercle());
		assertEquals(150.0, m.cx(), 1.0);
		assertEquals(130.0, m.cy(), 1.0);
		assertEquals(93.0, m.rayon(), 2.0);
	}

	@Test
	public void masqueIgnoreLesReflets() {
		int l = 300, h = 260;
		ImageCouleur img = disque(l, h, 150, 130, 100, 0);
		img.rougeBrut()[3 * l + 3] = 1f; // pixel isolé hors du disque
		Masque m = new MasqueChampVision().executer(img, ctx());
		assertFalse(m.estDans(3, 3));
	}

	@Test
	public void canalVertLaisseSonEntreeInchangee() {
		ImageCouleur img = disque(20, 20, 10, 10, 8, 0.1);
		float[] avant = img.vertBrut().clone();
		ImageGris g = new CanalVert().executer(img, ctx());
		assertEquals(20, g.largeur());
		assertArrayEquals(avant, img.vertBrut(), 0f);
	}

	@Test
	public void correctionReduitLEcartTypeEtLaisseLEntreeInchangee() {
		int l = 300, h = 260;
		ImageCouleur img = disque(l, h, 150, 130, 100, 0.5);
		Masque m = new MasqueChampVision().executer(img, ctx());
		ImageMasquee in = new ImageMasquee(img.canalVert(), m);
		float[] avant = in.image().donneesBrutes().clone();
		ImageMasquee out = new CorrectionEclairage().executer(in, ctx());
		assertEquals(l, out.largeur());
		assertEquals(h, out.hauteur());
		assertArrayEquals(avant, in.image().donneesBrutes(), 0f);
		assertTrue(ecartType(out) < ecartType(in) / 2);
		assertEquals(0.0, out.image().get(2, 2), 0.0);
	}

	@Test
	public void rehaussementEtireLaDynamiqueUtile() {
		int l = 100, h = 100;
		float[] p = new float[l * h];
		boolean[] mk = new boolean[l * h];
		for (int i = 0; i < p.length; i++) {
			mk[i] = true;
			p[i] = 0.4f + 0.2f * (i % 1000) / 999f;
		}
		ImageMasquee in = ImageMasquee.enveloppe(p, mk, l, h);
		float[] avant = p.clone();
		ImageMasquee out = new RehaussementContraste().executer(in, ctx());
		assertArrayEquals(avant, in.image().donneesBrutes(), 0f);
		assertEquals(0.0, out.image().min(), 0.01);
		assertEquals(1.0, out.image().max(), 0.01);
	}

	@Test
	public void rehaussementCalculeSesPercentilesDansLeMasque() {
		int l = 100, h = 100;
		float[] p = new float[l * h];
		boolean[] mk = new boolean[l * h];
		for (int i = 0; i < p.length; i++) {
			mk[i] = i % 100 >= 50; // moitié noire hors masque
			p[i] = mk[i] ? 0.4f + 0.2f * (i % 50) / 49f : 0f;
		}
		ImageMasquee out = new RehaussementContraste().executer(ImageMasquee.enveloppe(p, mk, l, h), ctx());
		assertEquals(1.0, out.image().max(), 0.01);
		assertEquals(0.0, out.image().get(0, 0), 0.0);
	}

	@Test
	public void medianSupprimeLAberrationEtRespecteLeMasque() {
		int l = 30, h = 30;
		float[] p = new float[l * h];
		boolean[] mk = new boolean[l * h];
		for (int y = 5; y < 25; y++) {
			for (int x = 5; x < 25; x++) {
				mk[y * l + x] = true;
				p[y * l + x] = 0.5f;
			}
		}
		p[15 * l + 15] = 1f;
		p[4 * l + 4] = 0.9f; // hors masque : ne doit pas contaminer les voisins du bord
		ImageMasquee in = ImageMasquee.enveloppe(p, mk, l, h);
		float[] avant = p.clone();
		ImageMasquee out = new FiltreMedian().executer(in, ctx());
		assertArrayEquals(avant, in.image().donneesBrutes(), 0f);
		assertEquals(0.5, out.image().get(15, 15), 0.0);
		assertEquals(0.5, out.image().get(5, 5), 0.0);
		assertEquals(0.0, out.image().get(4, 4), 0.0);
	}

	private static double ecartType(ImageMasquee im) {
		double s = 0, s2 = 0;
		long n = 0;
		float[] v = im.image().donneesBrutes();
		boolean[] m = im.masque().donneesBrutes();
		for (int i = 0; i < v.length; i++) {
			if (m[i]) {
				s += v[i];
				s2 += (double) v[i] * v[i];
				n++;
			}
		}
		double mu = s / n;
		return Math.sqrt(s2 / n - mu * mu);
	}
}
