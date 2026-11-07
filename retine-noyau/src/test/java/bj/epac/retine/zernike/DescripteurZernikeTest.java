package bj.epac.retine.zernike;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

import org.junit.Test;

import com.google.gson.JsonParser;

import bj.epac.retine.bifurcation.Bifurcation;
import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.parametres.Parametres;

public class DescripteurZernikeTest {

	private static final int L = 200;
	private static final int H = 200;
	private static final double CX = 100.5;
	private static final double CY = 100.5;

	private static ContexteExecution ctx(double rayon, int ordre) {
		String json = "{\"zernike\": {\"rayon\": " + rayon + ", \"ordre\": " + ordre + "}}";
		return new ContexteExecution(new Parametres(JsonParser.parseString(json).getAsJsonObject()), 1L,
				Paths.get("."));
	}

	/** Distance du point (x, y) à la demi-droite partant de (CX, CY) dans la direction {@code angle}. */
	private static double distanceDemiDroite(double x, double y, double angle) {
		double ux = Math.cos(angle);
		double uy = Math.sin(angle);
		double t = Math.max(0, (x - CX) * ux + (y - CY) * uy);
		return Math.hypot(x - CX - t * ux, y - CY - t * uy);
	}

	/**
	 * Bifurcation lisse en Y (trois vaisseaux sombres à profil gaussien, angles inégaux) et
	 * une tache sombre décentrée, pour que la fenêtre n'ait aucune symétrie.
	 */
	private static double motif(double x, double y) {
		double v = 1.0;
		double[] angles = { Math.toRadians(90), Math.toRadians(-30), Math.toRadians(-140) };
		double[] largeurs = { 3.0, 2.0, 1.6 };
		for (int k = 0; k < 3; k++) {
			double d = distanceDemiDroite(x, y, angles[k]);
			v -= 0.5 * Math.exp(-d * d / (2 * largeurs[k] * largeurs[k]));
		}
		v -= 0.3 * Math.exp(-(Math.pow(x - CX - 15, 2) + Math.pow(y - CY + 10, 2)) / (2 * 4.0 * 4.0));
		return v;
	}

	/** Image du motif tourné de {@code angle} (degrés) autour de (CX, CY), échantillonné au centre des pixels. */
	private static ImageGris image(double angle, DoubleBinaryOperator f) {
		double a = Math.toRadians(angle);
		double c = Math.cos(a);
		double s = Math.sin(a);
		float[] px = new float[L * H];
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				double dx = x + 0.5 - CX;
				double dy = y + 0.5 - CY;
				// rotation inverse : où était ce pixel avant la rotation ?
				px[y * L + x] = (float) f.applyAsDouble(CX + c * dx + s * dy, CY - s * dx + c * dy);
			}
		}
		return ImageGris.enveloppe(px, L, H);
	}

	private static double[] descripteur(ImageGris img, double rayon, int ordre) {
		List<DescripteurZernike.Descripteur> d = new DescripteurZernike().decrire(img,
				Arrays.asList(new Bifurcation(CX, CY, Bifurcation.Type.BIFURCATION)), ctx(rayon, ordre));
		return d.get(0).valeurs();
	}

	private static double ecartRelatif(double[] a, double[] b) {
		double num = 0;
		double den = 0;
		for (int i = 0; i < a.length; i++) {
			num += (a[i] - b[i]) * (a[i] - b[i]);
			den += a[i] * a[i];
		}
		return Math.sqrt(num / den);
	}

	@Test
	public void couplesOrdre4Et8() {
		int[][] c = DescripteurZernike.couples(4);
		int[][] attendu = { { 1, 1 }, { 2, 0 }, { 2, 2 }, { 3, 1 }, { 3, 3 }, { 4, 0 }, { 4, 2 }, { 4, 4 } };
		assertEquals(attendu.length, c.length);
		for (int i = 0; i < c.length; i++) {
			assertArrayEquals(attendu[i], c[i]);
		}
		assertEquals(24, DescripteurZernike.couples(8).length);
	}

	@Test
	public void polynomesRadiauxConnus() {
		for (double r = 0; r <= 1.0001; r += 0.125) {
			assertEquals(2 * r * r - 1, DescripteurZernike.radial(DescripteurZernike.coefficientsRadiaux(2, 0), 2, r), 1e-12);
			assertEquals(3 * Math.pow(r, 3) - 2 * r,
					DescripteurZernike.radial(DescripteurZernike.coefficientsRadiaux(3, 1), 3, r), 1e-12);
			assertEquals(6 * Math.pow(r, 4) - 6 * r * r + 1,
					DescripteurZernike.radial(DescripteurZernike.coefficientsRadiaux(4, 0), 4, r), 1e-12);
			assertEquals(Math.pow(r, 5), DescripteurZernike.radial(DescripteurZernike.coefficientsRadiaux(5, 5), 5, r),
					1e-12);
		}
		// R(n,m)(1) = 1 pour tout couple
		for (int[] c : DescripteurZernike.couples(10)) {
			assertEquals(1.0, DescripteurZernike.radial(DescripteurZernike.coefficientsRadiaux(c[0], c[1]), c[0], 1), 1e-9);
		}
	}

	@Test
	public void invariantParRotation() {
		for (double rayon : new double[] { 32, 48 }) {
			for (int ordre : new int[] { 4, 8 }) {
				double[] ref = descripteur(image(0, DescripteurZernikeTest::motif), rayon, ordre);
				for (double angle : new double[] { 30, 45 }) {
					double[] t = descripteur(image(angle, DescripteurZernikeTest::motif), rayon, ordre);
					double e = ecartRelatif(ref, t);
					assertTrue("R = " + rayon + ", ordre " + ordre + ", " + angle + "° : écart " + e, e < 0.05);
				}
			}
		}
	}

	@Test
	public void distingueDeuxFenetresDifferentes() {
		double[] y = descripteur(image(0, DescripteurZernikeTest::motif), 32, 4);
		double[] ligne = descripteur(image(0, (x, yy) -> 1 - 0.5 * Math.exp(-Math.pow(yy - CY, 2) / 8)), 32, 4);
		assertTrue("écart " + ecartRelatif(y, ligne), ecartRelatif(y, ligne) > 0.3);
	}

	@Test
	public void insensibleALEclairageEtAuContraste() {
		double[] a = descripteur(image(0, DescripteurZernikeTest::motif), 32, 4);
		double[] b = descripteur(image(0, (x, y) -> 0.2 + 2.5 * motif(x, y)), 32, 4);
		assertArrayEquals(a, b, 1e-4);
	}

	@Test
	public void fenetreUniformeDonneZero() {
		double[] d = descripteur(image(0, (x, y) -> 0.7), 32, 4);
		assertArrayEquals(new double[8], d, 0);
	}

	/** Erreur quadratique moyenne de la reconstruction de la fenêtre normalisée à l'ordre {@code ordre}. */
	private static double erreurReconstruction(ImageGris img, double rayon, int ordre) {
		int[][] c = new int[DescripteurZernike.couples(ordre).length + 1][];
		c[0] = new int[] { 0, 0 };
		System.arraycopy(DescripteurZernike.couples(ordre), 0, c, 1, c.length - 1);
		double[][] z = DescripteurZernike.moments(img, CX, CY, rayon, c);
		double s = 0;
		double ss = 0;
		int n = 0;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				if (Math.hypot(x + 0.5 - CX, y + 0.5 - CY) <= rayon) {
					s += img.get(x, y);
					ss += img.get(x, y) * img.get(x, y);
					n++;
				}
			}
		}
		double moyenne = s / n;
		double ecart = Math.sqrt(ss / n - moyenne * moyenne);
		double erreur = 0;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				double dx = x + 0.5 - CX;
				double dy = y + 0.5 - CY;
				double rho = Math.hypot(dx, dy) / rayon;
				if (rho > 1) {
					continue;
				}
				double theta = Math.atan2(dy, dx);
				double f = 0;
				for (int k = 0; k < c.length; k++) {
					double r = DescripteurZernike.radial(DescripteurZernike.coefficientsRadiaux(c[k][0], c[k][1]), c[k][0],
							rho);
					int m = c[k][1];
					f += m == 0 ? z[k][0] * r : 2 * r * (z[k][0] * Math.cos(m * theta) - z[k][1] * Math.sin(m * theta));
				}
				double vrai = (img.get(x, y) - moyenne) / ecart;
				erreur += (f - vrai) * (f - vrai);
			}
		}
		return Math.sqrt(erreur / n);
	}

	@Test
	public void reconstructionDePlusEnPlusFideleQuandLOrdreMonte() {
		ImageGris img = image(0, DescripteurZernikeTest::motif);
		double precedente = Double.MAX_VALUE;
		for (int ordre : new int[] { 2, 4, 8, 12, 16 }) {
			double e = erreurReconstruction(img, 32, ordre);
			assertTrue("ordre " + ordre + " : erreur " + e + " (précédente " + precedente + ")", e < precedente);
			precedente = e;
		}
		assertTrue("à l'ordre 16, erreur " + precedente, precedente < 0.5);
	}

	@Test
	public void ecritureCsv() throws Exception {
		ImageGris img = image(0, DescripteurZernikeTest::motif);
		List<DescripteurZernike.Descripteur> d = new DescripteurZernike().decrire(img,
				Arrays.asList(new Bifurcation(CX, CY, Bifurcation.Type.BIFURCATION)), ctx(32, 4));
		Path f = Files.createTempDirectory("zernike").resolve("descripteurs/A01_1_R32.csv");
		DescripteurZernike.ecrireCsv(f, d);
		List<String> lignes = Files.readAllLines(f);
		assertEquals("x,y,z1,z2,z3,z4,z5,z6,z7,z8", lignes.get(0));
		assertEquals(2, lignes.size());
		assertTrue(lignes.get(1), lignes.get(1).startsWith("100.50,100.50,"));
		assertEquals(10, lignes.get(1).split(",").length);
	}
}
