package bj.epac.retine.bifurcation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonParser;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.noyau.image.Masque;
import bj.epac.retine.parametres.Parametres;
import bj.epac.retine.squelette.AmincissementZhangSuen;

public class DetecteurBifurcationsTest {

	private static final int L = 200;
	private static final int H = 200;

	/** Marge de bord nulle par défaut : les formes de test sont loin du bord, sauf exprès. */
	private static ContexteExecution ctx() {
		return ctx("{\"zernike\": {\"rayon\": 0}}");
	}

	private static ContexteExecution ctx(String json) {
		return new ContexteExecution(new Parametres(JsonParser.parseString(json).getAsJsonObject()), 1L,
				Paths.get("."));
	}

	/** Trace un segment épais (disques de rayon {@code r} le long du segment). */
	private static void segment(boolean[] v, double x0, double y0, double x1, double y1, double r) {
		double longueur = Math.hypot(x1 - x0, y1 - y0);
		int pas = (int) Math.ceil(longueur * 2) + 1;
		for (int s = 0; s <= pas; s++) {
			double cx = x0 + (x1 - x0) * s / pas;
			double cy = y0 + (y1 - y0) * s / pas;
			for (int y = (int) (cy - r - 1); y <= cy + r + 1; y++) {
				for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
					if (x >= 0 && y >= 0 && x < L && y < H && Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) {
						v[y * L + x] = true;
					}
				}
			}
		}
	}

	/** Squelette (Zhang-Suen + marches) de la forme épaisse, puis détection dans un champ plein. */
	private static DetecteurBifurcations.Resultat detecter(boolean[] forme, ContexteExecution c) {
		Masque sq = new AmincissementZhangSuen().executer(new Masque(forme, L, H), c);
		return new DetecteurBifurcations().detecter(sq, Masque.plein(L, H), c);
	}

	private static long compter(List<Bifurcation> p, Bifurcation.Type t) {
		return p.stream().filter(b -> b.type() == t).count();
	}

	private static double distanceA(Bifurcation b, double x, double y) {
		return Math.hypot(b.x() - x, b.y() - y);
	}

	@Test
	public void yDonneUneBifurcationAuCentre() {
		boolean[] f = new boolean[L * H];
		segment(f, 100, 100, 100, 180, 3);
		segment(f, 100, 100, 40, 40, 3);
		segment(f, 100, 100, 160, 40, 3);
		List<Bifurcation> p = detecter(f, ctx()).points();
		assertEquals(p.toString(), 1, p.size());
		assertEquals(Bifurcation.Type.BIFURCATION, p.get(0).type());
		assertTrue(p.toString(), distanceA(p.get(0), 100, 100) < 4);
	}

	@Test
	public void bifurcationAAngleAiguGardee() {
		// branches à ±15° de la verticale : 30° entre elles, cas fréquent sur la rétine
		boolean[] f = new boolean[L * H];
		segment(f, 100, 170, 100, 100, 2);
		segment(f, 100, 100, 100 - 70 * Math.sin(Math.toRadians(15)), 100 - 70 * Math.cos(Math.toRadians(15)), 2);
		segment(f, 100, 100, 100 + 70 * Math.sin(Math.toRadians(15)), 100 - 70 * Math.cos(Math.toRadians(15)), 2);
		List<Bifurcation> p = detecter(f, ctx()).points();
		assertEquals(p.toString(), 1, p.size());
		assertEquals(Bifurcation.Type.BIFURCATION, p.get(0).type());
		assertTrue(p.toString(), distanceA(p.get(0), 100, 100) < 8);
	}

	@Test
	public void croixDonneUnCroisement() {
		boolean[] f = new boolean[L * H];
		segment(f, 30, 100, 170, 100, 3);
		segment(f, 100, 30, 100, 170, 3);
		List<Bifurcation> p = detecter(f, ctx()).points();
		assertEquals(p.toString(), 1, p.size());
		assertEquals(Bifurcation.Type.CROISEMENT, p.get(0).type());
		assertTrue(p.toString(), distanceA(p.get(0), 100, 100) < 3);
	}

	@Test
	public void croisementObliqueVuCommeDeuxBifurcationsDevientUnCroisement() {
		// angle de 60° : le squelette sépare la jonction en deux, à environ 6 px l'une de l'autre
		boolean[] f = new boolean[L * H];
		segment(f, 30, 100, 170, 100, 2);
		double a = Math.toRadians(60);
		segment(f, 100 - 80 * Math.cos(a), 100 - 80 * Math.sin(a), 100 + 80 * Math.cos(a), 100 + 80 * Math.sin(a), 2);
		List<Bifurcation> p = detecter(f, ctx()).points();
		assertEquals(p.toString(), 1, p.size());
		assertEquals(Bifurcation.Type.CROISEMENT, p.get(0).type());
		assertTrue(p.toString(), distanceA(p.get(0), 100, 100) < 4);
	}

	@Test
	public void sansRegroupementLeCroisementObliqueRestePlusieursPoints() {
		boolean[] f = new boolean[L * H];
		segment(f, 30, 100, 170, 100, 2);
		double a = Math.toRadians(60);
		segment(f, 100 - 80 * Math.cos(a), 100 - 80 * Math.sin(a), 100 + 80 * Math.cos(a), 100 + 80 * Math.sin(a), 2);
		DetecteurBifurcations.Resultat r = detecter(f,
				ctx("{\"zernike\": {\"rayon\": 0}, \"bifurcations\": {\"distanceCroisement\": 0}}"));
		Masque sq = r.elague();
		int jonctions = DetecteurBifurcations.jonctions(sq.donneesBrutes(), L, H).length;
		// le test précédent n'a de sens que si la jonction est vraiment éclatée en deux
		assertTrue("squelette avec plusieurs pixels jonctions : " + jonctions, jonctions >= 2);
		assertEquals(r.points().toString(), 2, r.points().size());
	}

	@Test
	public void barbeCourteElagueeBrancheLongueGardee() {
		boolean[] f = new boolean[L * H];
		segment(f, 20, 100, 180, 100, 2);
		segment(f, 60, 100, 60, 92, 1); // barbe : dépasse de ~6 px du bord du vaisseau
		segment(f, 140, 100, 140, 40, 2); // vraie branche de 60 px
		DetecteurBifurcations.Resultat r = detecter(f, ctx());
		List<Bifurcation> p = r.points();
		assertEquals(p.toString(), 1, p.size());
		assertTrue(p.toString(), distanceA(p.get(0), 140.5, 100.5) < 4);
		// la ligne principale reste entière : aucun trou entre x = 25 et x = 175
		boolean[] sq = r.elague().donneesBrutes();
		for (int x = 25; x <= 175; x++) {
			boolean present = false;
			for (int y = 95; y <= 105; y++) {
				present |= sq[y * L + x];
			}
			assertTrue("colonne " + x + " coupée", present);
		}
	}

	@Test
	public void elagageRetireUneBrancheCourteSurUnSqueletteFin() {
		boolean[] v = new boolean[L * H];
		for (int x = 20; x <= 180; x++) {
			v[100 * L + x] = true;
		}
		for (int y = 90; y < 100; y++) {
			v[y * L + 60] = true; // branche de 10 px < 12
		}
		for (int y = 70; y < 100; y++) {
			v[y * L + 140] = true; // branche de 30 px
		}
		DetecteurBifurcations.elaguer(v, L, H, 12);
		for (int y = 90; y < 100; y++) {
			assertTrue(!v[y * L + 60]);
		}
		assertTrue(v[70 * L + 140]);
		assertTrue(v[100 * L + 60]); // le point de jonction reste sur la ligne
	}

	@Test
	public void petiteBoucleNeDonneAucunPoint() {
		// un trou dans un vaisseau : le squelette fait une boucle, deux fausses bifurcations
		boolean[] f = new boolean[L * H];
		segment(f, 20, 100, 180, 100, 5);
		for (int y = 97; y <= 102; y++) {
			for (int x = 97; x <= 102; x++) {
				f[y * L + x] = false;
			}
		}
		Masque sq = new AmincissementZhangSuen().executer(new Masque(f, L, H), ctx());
		assertTrue("le squelette doit contenir une boucle (2 jonctions ou plus)",
				DetecteurBifurcations.jonctions(sq.donneesBrutes(), L, H).length >= 2);
		List<Bifurcation> p = new DetecteurBifurcations().detecter(sq, Masque.plein(L, H), ctx()).points();
		assertEquals(p.toString(), 0, p.size());
		// sans remplissage, la boucle donne bien deux fausses bifurcations
		List<Bifurcation> sans = new DetecteurBifurcations().detecter(sq, Masque.plein(L, H),
				ctx("{\"zernike\": {\"rayon\": 0}, \"bifurcations\": {\"aireBoucle\": 0}}")).points();
		assertEquals(sans.toString(), 2, sans.size());
	}

	@Test
	public void grandeBoucleGardee() {
		// un vaisseau qui quitte la ligne et la rejoint plus loin : vraie boucle (100 × 40 px)
		boolean[] f = new boolean[L * H];
		segment(f, 10, 100, 190, 100, 2);
		segment(f, 50, 100, 50, 60, 2);
		segment(f, 50, 60, 150, 60, 2);
		segment(f, 150, 60, 150, 100, 2);
		List<Bifurcation> p = detecter(f, ctx()).points();
		assertEquals(p.toString(), 2, p.size());
		assertTrue(p.toString(), distanceA(p.get(0), 50.5, 100.5) < 4 || distanceA(p.get(0), 150.5, 100.5) < 4);
	}

	@Test
	public void deuxVaisseauxParallelesProchesSansJonction() {
		boolean[] f = new boolean[L * H];
		segment(f, 20, 95, 180, 95, 2);
		segment(f, 20, 105, 180, 105, 2);
		assertEquals(0, detecter(f, ctx()).points().size());
	}

	@Test
	public void pointsTropPresDuBordEcartes() {
		boolean[] f = new boolean[L * H];
		segment(f, 100, 100, 100, 180, 3);
		segment(f, 100, 100, 40, 40, 3);
		segment(f, 100, 100, 160, 40, 3);
		Masque sq = new AmincissementZhangSuen().executer(new Masque(f, L, H), ctx());
		Masque champ = Masque.cercle(L, H, 100, 100, 95);
		ContexteExecution petite = ctx("{\"zernike\": {\"rayon\": 30}}");
		ContexteExecution grande = ctx("{\"zernike\": {\"rayon\": 120}}");
		DetecteurBifurcations.Resultat r1 = new DetecteurBifurcations().detecter(sq, champ, petite);
		DetecteurBifurcations.Resultat r2 = new DetecteurBifurcations().detecter(sq, champ, grande);
		assertEquals(1, r1.points().size());
		assertEquals(0, r1.ecartesAuBord());
		assertEquals(0, r2.points().size());
		assertEquals(1, r2.ecartesAuBord());
	}

	@Test
	public void coordonneesAuCentreDuPixel() {
		boolean[] v = new boolean[L * H];
		for (int k = -30; k <= 30; k++) {
			v[50 * L + 50 + k] = true;
			v[(50 + k) * L + 50] = true;
		}
		List<Bifurcation> p = new DetecteurBifurcations().detecter(Masque.enveloppe(v, L, H), Masque.plein(L, H), ctx())
				.points();
		assertEquals(1, p.size());
		assertEquals(50.5, p.get(0).x(), 1e-9);
		assertEquals(50.5, p.get(0).y(), 1e-9);
		assertEquals(Bifurcation.Type.CROISEMENT, p.get(0).type());
	}

	@Test
	public void nombreDeCroisements() {
		boolean[] v = new boolean[9];
		v[4] = true;
		v[1] = true; // haut
		assertEquals(1, DetecteurBifurcations.nombreCroisements(v, 3, 3, 4));
		v[7] = true; // bas
		assertEquals(2, DetecteurBifurcations.nombreCroisements(v, 3, 3, 4));
		v[3] = true; // gauche
		assertEquals(3, DetecteurBifurcations.nombreCroisements(v, 3, 3, 4));
		v[5] = true; // droite
		assertEquals(4, DetecteurBifurcations.nombreCroisements(v, 3, 3, 4));
		v[2] = true; // haut-droite rejoint haut et droite : un passage de moins
		assertEquals(3, DetecteurBifurcations.nombreCroisements(v, 3, 3, 4));
	}

	@Test
	public void entreeInchangeeEtObservateurPrevenu() {
		boolean[] f = new boolean[L * H];
		segment(f, 20, 100, 180, 100, 2);
		segment(f, 60, 100, 60, 92, 1);
		Masque sq = new AmincissementZhangSuen().executer(new Masque(f, L, H), ctx());
		boolean[] avant = sq.donneesBrutes().clone();
		List<String> noms = new ArrayList<>();
		new DetecteurBifurcations().detecter(sq, Masque.plein(L, H), ctx(), (nom, img, m) -> noms.add(nom));
		assertTrue(Arrays.equals(avant, sq.donneesBrutes()));
		assertEquals(Arrays.asList(DetecteurBifurcations.ELAGAGE), noms);
	}

	@Test
	public void ecritureCsv() throws Exception {
		Path dossier = Files.createTempDirectory("bifurcations");
		Path f = dossier.resolve("sous/A01_1.csv");
		Bifurcation.ecrireCsv(f, Arrays.asList(new Bifurcation(10.5, 20.25, Bifurcation.Type.BIFURCATION),
				new Bifurcation(3, 4, Bifurcation.Type.CROISEMENT)));
		assertEquals(Arrays.asList("x,y,type", "10.50,20.25,bifurcation", "3.00,4.00,croisement"),
				Files.readAllLines(f));
	}
}
