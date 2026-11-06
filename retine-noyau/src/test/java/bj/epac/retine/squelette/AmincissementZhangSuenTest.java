package bj.epac.retine.squelette;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.noyau.image.Masque;
import bj.epac.retine.parametres.Parametres;

/**
 * Tests du protocole du Point 2 (une ligne, un Y, une croix, une image non carrée de
 * 7 × 13) et propriétés générales : topologie conservée, une ligne d'un pixel en
 * 8-connexité, squelette inclus dans le masque.
 */
public class AmincissementZhangSuenTest {

	private static final ContexteExecution CTX = new ContexteExecution(Parametres.defauts(), 1L, Paths.get("."));

	// ------------------------------------------------------------------ formes

	/** Pixels à moins de {@code demiLargeur} du segment (x0, y0)–(x1, y1) (centres de pixels, D1). */
	private static void segment(boolean[] v, int l, double x0, double y0, double x1, double y1, double demiLargeur) {
		int h = v.length / l;
		double dx = x1 - x0, dy = y1 - y0, n2 = dx * dx + dy * dy;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				double px = x + 0.5, py = y + 0.5;
				double t = n2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / n2));
				double ex = px - (x0 + t * dx), ey = py - (y0 + t * dy);
				if (ex * ex + ey * ey <= demiLargeur * demiLargeur) {
					v[y * l + x] = true;
				}
			}
		}
	}

	private static Masque squelette(boolean[] v, int l) {
		return new AmincissementZhangSuen().executer(new Masque(v, l, v.length / l), CTX);
	}

	// ------------------------------------------------------------------ mesures indépendantes

	private static boolean allume(Masque m, int x, int y) {
		return x >= 0 && y >= 0 && x < m.largeur() && y < m.hauteur() && m.estDans(x, y);
	}

	private static int voisinsAllumes(Masque m, int x, int y) {
		int b = 0;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				if ((dx != 0 || dy != 0) && allume(m, x + dx, y + dy)) {
					b++;
				}
			}
		}
		return b;
	}

	/** Nombre de croisements CN = ½ Σ |Pi − Pi+1| (3 : bifurcation, 4 : croisement). */
	private static int nombreCroisements(Masque m, int x, int y) {
		int[] dx = { 0, 1, 1, 1, 0, -1, -1, -1 };
		int[] dy = { -1, -1, 0, 1, 1, 1, 0, -1 };
		int s = 0;
		for (int k = 0; k < 8; k++) {
			boolean a = allume(m, x + dx[k], y + dy[k]);
			boolean b = allume(m, x + dx[(k + 1) % 8], y + dy[(k + 1) % 8]);
			s += a != b ? 1 : 0;
		}
		return s / 2;
	}

	private static List<int[]> points(Masque m) {
		List<int[]> p = new ArrayList<int[]>();
		for (int y = 0; y < m.hauteur(); y++) {
			for (int x = 0; x < m.largeur(); x++) {
				if (m.estDans(x, y)) {
					p.add(new int[] { x, y });
				}
			}
		}
		return p;
	}

	private static int extremites(Masque m) {
		int n = 0;
		for (int[] p : points(m)) {
			n += voisinsAllumes(m, p[0], p[1]) == 1 ? 1 : 0;
		}
		return n;
	}

	/** Morceaux du squelette (8-connexité) ou du fond (4-connexité, l'extérieur de l'image compris). */
	private static int composantes(Masque m, boolean fond) {
		int l = m.largeur() + 2, h = m.hauteur() + 2; // bordure d'un pixel éteint autour
		boolean[] v = new boolean[l * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				v[y * l + x] = allume(m, x - 1, y - 1) != fond;
			}
		}
		boolean[] vu = new boolean[v.length];
		int n = 0;
		for (int d = 0; d < v.length; d++) {
			if (!v[d] || vu[d]) {
				continue;
			}
			n++;
			List<Integer> pile = new ArrayList<Integer>();
			pile.add(d);
			vu[d] = true;
			while (!pile.isEmpty()) {
				int i = pile.remove(pile.size() - 1);
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						if ((dx == 0 && dy == 0) || (fond && dx != 0 && dy != 0)) {
							continue;
						}
						int x = i % l + dx, y = i / l + dy;
						if (x >= 0 && y >= 0 && x < l && y < h && v[y * l + x] && !vu[y * l + x]) {
							vu[y * l + x] = true;
							pile.add(y * l + x);
						}
					}
				}
			}
		}
		return n;
	}

	/**
	 * Vrai si chaque pixel qui n'est pas une extrémité est indispensable : le retirer
	 * couperait un morceau ou ouvrirait un trou (vérifié sur la fenêtre 3 × 3, par une
	 * recherche de morceaux indépendante des tables de la classe).
	 */
	private static boolean uneLigneDUnPixel(Masque m) {
		for (int[] p : points(m)) {
			if (voisinsAllumes(m, p[0], p[1]) < 2) {
				continue;
			}
			boolean[] f = new boolean[9];
			for (int k = 0; k < 9; k++) {
				f[k] = k != 4 && allume(m, p[0] + k % 3 - 1, p[1] + k / 3 - 1);
			}
			if (morceaux(f, true, false) == 1 && morceaux(inverse(f), false, true) == 1) {
				return false; // pixel retirable sans changer la topologie : le squelette n'est pas fin
			}
		}
		return true;
	}

	private static boolean[] inverse(boolean[] f) {
		boolean[] g = new boolean[9];
		for (int k = 0; k < 9; k++) {
			g[k] = k != 4 && !f[k];
		}
		return g;
	}

	private static int morceaux(boolean[] f, boolean huit, boolean auxCotes) {
		boolean[] vu = new boolean[9];
		int n = 0;
		for (int d = 0; d < 9; d++) {
			if (!f[d] || vu[d]) {
				continue;
			}
			List<Integer> pile = new ArrayList<Integer>();
			pile.add(d);
			vu[d] = true;
			boolean cote = false;
			while (!pile.isEmpty()) {
				int a = pile.remove(pile.size() - 1);
				cote |= a == 1 || a == 3 || a == 5 || a == 7;
				for (int b = 0; b < 9; b++) {
					int dx = Math.abs(a % 3 - b % 3), dy = Math.abs(a / 3 - b / 3);
					boolean touche = huit ? Math.max(dx, dy) == 1 : dx + dy == 1;
					if (f[b] && !vu[b] && touche) {
						vu[b] = true;
						pile.add(b);
					}
				}
			}
			n += !auxCotes || cote ? 1 : 0;
		}
		return n;
	}

	private static void inclusDans(Masque s, boolean[] v) {
		for (int[] p : points(s)) {
			assertTrue("squelette hors du masque en " + p[0] + "," + p[1], v[p[1] * s.largeur() + p[0]]);
		}
	}

	// ------------------------------------------------------------------ tests du protocole

	@Test
	public void ligneEpaisseDevientUneLigneCentree() {
		int l = 60, h = 21;
		boolean[] v = new boolean[l * h];
		for (int y = 7; y <= 13; y++) {
			for (int x = 10; x < 50; x++) {
				v[y * l + x] = true;
			}
		}
		Masque s = squelette(v, l);
		assertEquals(1, composantes(s, false));
		assertEquals(2, extremites(s));
		assertTrue(uneLigneDUnPixel(s));
		for (int[] p : points(s)) {
			assertEquals("ligne centrée sur y = 10", 10, p[1], 1);
		}
		assertTrue("longueur " + points(s).size(), points(s).size() >= 30);
		inclusDans(s, v);
	}

	@Test
	public void yDonneUneSeuleBifurcationAuCentre() {
		int l = 80, h = 80;
		double cx = 40, cy = 40, r = 30;
		boolean[] v = new boolean[l * h];
		for (double a : new double[] { 90, 210, 330 }) {
			double t = Math.toRadians(a);
			segment(v, l, cx, cy, cx + r * Math.cos(t), cy - r * Math.sin(t), 2.5);
		}
		Masque s = squelette(v, l);
		assertEquals(1, composantes(s, false));
		assertEquals(3, extremites(s));
		assertTrue(uneLigneDUnPixel(s));
		int bifurcations = 0;
		for (int[] p : points(s)) {
			if (nombreCroisements(s, p[0], p[1]) >= 3) {
				bifurcations++;
				assertTrue("bifurcation loin du centre : " + p[0] + "," + p[1], Math.hypot(p[0] + 0.5 - cx, p[1] + 0.5 - cy) < 5);
			}
		}
		assertEquals(1, bifurcations);
		inclusDans(s, v);
	}

	@Test
	public void croixDonneUnCroisementOuDeuxBifurcationsVoisines() {
		int l = 80, h = 80;
		boolean[] v = new boolean[l * h];
		segment(v, l, 10, 40, 70, 40, 2.5);
		segment(v, l, 40, 10, 40, 70, 2.5);
		Masque s = squelette(v, l);
		assertEquals(1, composantes(s, false));
		assertEquals(4, extremites(s));
		assertTrue(uneLigneDUnPixel(s));
		int branches = 0;
		for (int[] p : points(s)) {
			int cn = nombreCroisements(s, p[0], p[1]);
			if (cn >= 3) {
				branches += cn - 2; // un croisement (CN 4) compte comme deux bifurcations (CN 3)
				assertTrue("point de jonction loin du centre", Math.hypot(p[0] + 0.5 - 40, p[1] + 0.5 - 40) < 6);
			}
		}
		assertEquals("un croisement, ou deux bifurcations à moins de 8 px", 2, branches);
	}

	@Test
	public void imageNonCarreeSeptSurTreize() {
		// barre verticale de 3 px dans une image de 7 de large sur 13 de haut
		boolean[] v = new boolean[7 * 13];
		for (int y = 1; y <= 11; y++) {
			for (int x = 2; x <= 4; x++) {
				v[y * 7 + x] = true;
			}
		}
		Masque s = squelette(v, 7);
		assertEquals(7, s.largeur());
		assertEquals(13, s.hauteur());
		assertEquals(1, composantes(s, false));
		for (int[] p : points(s)) {
			assertEquals("colonne du milieu", 3, p[0]);
		}
		// la même, couchée : 13 de large sur 7 de haut
		boolean[] w = new boolean[13 * 7];
		for (int y = 2; y <= 4; y++) {
			for (int x = 1; x <= 11; x++) {
				w[y * 13 + x] = true;
			}
		}
		Masque t = squelette(w, 13);
		assertEquals(1, composantes(t, false));
		for (int[] p : points(t)) {
			assertEquals("ligne du milieu", 3, p[1]);
		}
	}

	// ------------------------------------------------------------------ propriétés générales

	@Test
	public void anneauGardeSonTrou() {
		int l = 50, h = 50;
		boolean[] v = new boolean[l * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				double d = Math.hypot(x + 0.5 - 25, y + 0.5 - 25);
				v[y * l + x] = d >= 10 && d <= 16;
			}
		}
		Masque s = squelette(v, l);
		assertEquals(1, composantes(s, false));
		assertEquals("une boucle fermée n'a pas d'extrémité", 0, extremites(s));
		assertEquals("le trou est conservé : fond intérieur + fond extérieur", 2, composantes(s, true));
		assertTrue(uneLigneDUnPixel(s));
	}

	@Test
	public void ligneDejaFineInchangee() {
		int l = 30, h = 20;
		boolean[] v = new boolean[l * h];
		for (int x = 2; x < 12; x++) {
			v[5 * l + x] = true; // horizontale
		}
		for (int k = 0; k < 10; k++) {
			v[(6 + k) * l + 12 + k] = true; // diagonale qui la prolonge
		}
		assertArrayEquals(v, squelette(v, l).donneesBrutes());
	}

	@Test
	public void formeQuiToucheLeBordDeLImage() {
		int l = 40, h = 15;
		boolean[] v = new boolean[l * h];
		for (int y = 5; y <= 9; y++) {
			for (int x = 0; x < l; x++) {
				v[y * l + x] = true; // de bord à bord
			}
		}
		Masque s = squelette(v, l);
		assertEquals(1, composantes(s, false));
		assertTrue(points(s).size() >= l - 6);
		assertTrue(uneLigneDUnPixel(s));
	}

	@Test
	public void plusieursMorceauxRestentSepares() {
		int l = 60, h = 40;
		boolean[] v = new boolean[l * h];
		segment(v, l, 5, 10, 55, 10, 2.5);
		segment(v, l, 5, 30, 55, 30, 3.5);
		assertEquals(2, composantes(squelette(v, l), false));
	}

	@Test
	public void masqueVideDonneSqueletteVide() {
		assertEquals(0, points(squelette(new boolean[20 * 10], 20)).size());
	}

	@Test
	public void entreeInchangee() {
		int l = 40, h = 40;
		boolean[] v = new boolean[l * h];
		segment(v, l, 5, 5, 35, 35, 3);
		Masque m = new Masque(v, l, h);
		boolean[] avant = m.donneesBrutes().clone();
		new AmincissementZhangSuen().executer(m, CTX);
		assertArrayEquals(avant, m.donneesBrutes());
	}

	@Test
	public void observateurVoitLesDeuxEtapes() {
		int l = 60, h = 60;
		boolean[] v = new boolean[l * h];
		segment(v, l, 5, 5, 55, 50, 4);
		List<String> noms = new ArrayList<String>();
		List<Masque> masques = new ArrayList<Masque>();
		Masque fin = new AmincissementZhangSuen().executer(new Masque(v, l, h), CTX, (nom, image, masque) -> {
			noms.add(nom);
			masques.add(masque);
		});
		assertEquals(java.util.Arrays.asList(AmincissementZhangSuen.ZHANG_SUEN, AmincissementZhangSuen.MARCHES), noms);
		assertArrayEquals(fin.donneesBrutes(), masques.get(1).donneesBrutes());
		// le retrait des marches ne fait qu'enlever des pixels
		for (int i = 0; i < v.length; i++) {
			assertFalse(fin.donneesBrutes()[i] && !masques.get(0).donneesBrutes()[i]);
		}
	}
}
