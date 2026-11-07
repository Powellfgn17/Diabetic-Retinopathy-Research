package bj.epac.retine.appariement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonParser;

import bj.epac.retine.bifurcation.Bifurcation;
import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.parametres.Parametres;
import bj.epac.retine.zernike.DescripteurZernike.Descripteur;

public class ApparieurCorrelationTest {

	private static ContexteExecution ctx(double seuil) {
		return new ContexteExecution(
				new Parametres(JsonParser.parseString("{\"appariement\": {\"seuil\": " + seuil + "}}").getAsJsonObject()),
				1L, Paths.get("."));
	}

	private static Descripteur d(double x, double... v) {
		return new Descripteur(new Bifurcation(x, 0, Bifurcation.Type.BIFURCATION), v);
	}

	@Test
	public void correlationCasConnus() {
		double[] a = { 1, 4, 2, 8, 5 };
		assertEquals(1, ApparieurCorrelation.correlation(a, a), 1e-12);
		assertEquals(1, ApparieurCorrelation.correlation(a, new double[] { 5, 11, 7, 19, 13 }), 1e-12); // 2a + 3
		assertEquals(-1, ApparieurCorrelation.correlation(a, new double[] { -1, -4, -2, -8, -5 }), 1e-12);
		assertTrue(Double.isNaN(ApparieurCorrelation.correlation(a, new double[] { 3, 3, 3, 3, 3 })));
	}

	@Test
	public void chaquePointRetrouveSonCorrespondant() {
		List<Descripteur> ref = Arrays.asList(d(1, 1, 5, 2, 8), d(2, 9, 1, 7, 2), d(3, 3, 3, 9, 1));
		// le suivi dans un autre ordre, valeurs légèrement bruitées et changées d'échelle
		List<Descripteur> suivi = Arrays.asList(d(30, 3.1, 2.9, 9.2, 1.0), d(10, 2.1, 10.2, 3.9, 16.1),
				d(20, 18.2, 2.1, 13.8, 4.1));
		List<ApparieurCorrelation.Couple> c = new ApparieurCorrelation().apparier(ref, suivi, ctx(0.9));
		assertEquals(3, c.size());
		for (ApparieurCorrelation.Couple k : c) {
			assertEquals(k.reference().x() * 10, k.suivi().x(), 0);
			assertTrue(k.score() > 0.99);
		}
	}

	@Test
	public void choixMutuelSeulement() {
		// X et Y préfèrent tous deux Z, mais Z ne choisit que Y : un seul couple
		List<Descripteur> ref = Arrays.asList(d(1, 1, 2, 3, 5), d(2, 1, 2, 3, 4));
		List<Descripteur> suivi = Arrays.asList(d(3, 1, 2, 3, 4.1));
		List<ApparieurCorrelation.Couple> c = new ApparieurCorrelation().apparier(ref, suivi, ctx(0));
		assertEquals(1, c.size());
		assertEquals(2, c.get(0).reference().x(), 0);
	}

	@Test
	public void seuilEcarteLesCouplesFaibles() {
		List<Descripteur> ref = Arrays.asList(d(1, 1, 2, 3, 4));
		List<Descripteur> suivi = Arrays.asList(d(2, 1, 3, 2, 4));
		double score = ApparieurCorrelation.correlation(new double[] { 1, 2, 3, 4 }, new double[] { 1, 3, 2, 4 });
		assertEquals(0.8, score, 1e-12);
		assertEquals(1, new ApparieurCorrelation().apparier(ref, suivi, ctx(0.8)).size());
		assertEquals(0, new ApparieurCorrelation().apparier(ref, suivi, ctx(0.81)).size());
	}

	@Test
	public void descripteurConstantIgnore() {
		List<Descripteur> ref = Arrays.asList(d(1, 0, 0, 0, 0), d(2, 1, 2, 3, 4));
		List<Descripteur> suivi = Arrays.asList(d(3, 1, 2, 3, 4.2));
		List<ApparieurCorrelation.Couple> c = new ApparieurCorrelation().apparier(ref, suivi, ctx(0));
		assertEquals(1, c.size());
		assertEquals(2, c.get(0).reference().x(), 0);
	}

	@Test
	public void triesDuMeilleurAuMoinsBon() {
		List<Descripteur> ref = Arrays.asList(d(1, 1, 5, 2, 8), d(2, 9, 1, 7, 2));
		List<Descripteur> suivi = Arrays.asList(d(10, 1.5, 5, 2, 7), d(20, 9, 1, 7, 2));
		List<ApparieurCorrelation.Couple> c = new ApparieurCorrelation().apparier(ref, suivi, ctx(0));
		assertEquals(2, c.size());
		assertTrue(c.get(0).score() >= c.get(1).score());
		assertEquals(2, c.get(0).reference().x(), 0);
	}

	@Test
	public void listesVides() {
		assertTrue(new ApparieurCorrelation().apparier(Arrays.asList(), Arrays.asList(d(1, 1, 2)), ctx(0)).isEmpty());
	}

	@Test(expected = IllegalArgumentException.class)
	public void longueursDifferentesRefusees() {
		new ApparieurCorrelation().apparier(Arrays.asList(d(1, 1, 2, 3)), Arrays.asList(d(2, 1, 2, 3, 4)), ctx(0));
	}

	@Test(expected = IllegalArgumentException.class)
	public void seuilHorsBornesRefuse() {
		new ApparieurCorrelation().apparier(Arrays.asList(d(1, 1, 2)), Arrays.asList(d(2, 1, 2)), ctx(1.5));
	}

	@Test
	public void ecritureCsv() throws Exception {
		Bifurcation a = new Bifurcation(10.5, 20.5, Bifurcation.Type.BIFURCATION);
		Bifurcation b = new Bifurcation(12.25, 19, Bifurcation.Type.CROISEMENT);
		Path f = Files.createTempDirectory("appariement").resolve("appariements/S01.csv");
		ApparieurCorrelation.ecrireCsv(f, Arrays.asList(new ApparieurCorrelation.Couple(a, b, 0.98765)));
		assertEquals(Arrays.asList("x_ref,y_ref,x_suivi,y_suivi,score", "10.50,20.50,12.25,19.00,0.9877"),
				Files.readAllLines(f));
	}

	@Test
	public void descripteurCopieSesValeurs() {
		double[] v = { 1, 2, 3 };
		Descripteur x = d(1, v);
		v[0] = 99;
		assertEquals(1, x.valeurs()[0], 0);
		assertSame(x.point(), x.point());
	}
}
