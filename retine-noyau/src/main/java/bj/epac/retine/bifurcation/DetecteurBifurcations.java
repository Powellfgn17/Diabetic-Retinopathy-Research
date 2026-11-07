package bj.epac.retine.bifurcation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Observateur;
import bj.epac.retine.noyau.image.Masque;
import bj.epac.retine.parametres.Parametres;
import bj.epac.retine.squelette.AmincissementZhangSuen;

/**
 * Bifurcations et croisements du squelette des vaisseaux (Point 2, section 11 de
 * Docs/algorithmes.md).
 *
 * <ol>
 * <li><b>Petites boucles.</b> Un trou dans un vaisseau du masque (fréquent près du disque
 * optique) devient une boucle dans le squelette, avec deux fausses bifurcations. Les zones
 * entourées par le squelette de moins de {@code bifurcations.aireBoucle} pixels sont
 * remplies, puis le squelette est recalculé : la boucle redevient une ligne.</li>
 * <li><b>Élagage.</b> Une barbe du masque laisse dans le squelette une branche courte qui
 * part d'une jonction et finit par une extrémité, donc une fausse bifurcation. Depuis chaque
 * extrémité, on suit la branche pixel par pixel ; si elle atteint une jonction (pixel à 3
 * voisins ou plus) avant {@code bifurcations.longueurElagage}, elle est retirée. Un bout
 * isolé plus court est retiré aussi. Toutes les branches d'une passe sont retirées ensemble,
 * puis les marches d'escalier laissées aux jonctions ; on recommence (au plus
 * {@value #PASSES_ELAGAGE} passes) : une barbe fourchue ne disparaît qu'en deux fois.</li>
 * <li><b>Détection.</b> Nombre de croisements de chaque pixel du squelette : CN = nombre de
 * passages éteint → allumé en faisant le tour de ses 8 voisins. CN ≥ 3 : jonction.</li>
 * <li><b>Fusion.</b> Les pixels jonctions à moins de {@code bifurcations.distanceFusion} les
 * uns des autres forment un seul point, au centre de leurs pixels (D1).</li>
 * <li><b>Type, par comptage des branches.</b> On compte les branches qui sortent d'un petit
 * carré autour du point (seulement celles reliées au point) : 3 = bifurcation, 4 ou plus =
 * croisement, 2 ou moins = artefact écarté (petite boucle autour d'un trou du masque).</li>
 * <li><b>Croisements.</b> Deux vaisseaux qui se croisent donnent souvent deux bifurcations
 * décalées. Deux bifurcations à moins de {@code bifurcations.distanceCroisement} sont
 * remplacées par leur milieu, puis on recompte les branches autour des deux : 4 ou plus =
 * croisement ; 3 = une bifurcation ; 2 = une boucle, les deux sont écartées.</li>
 * <li><b>Bord.</b> On écarte les points dont le disque de rayon {@code zernike.rayon} sort
 * du champ de vue : leur descripteur de Zernike serait faussé.</li>
 * </ol>
 *
 * <p>Toutes les longueurs sont en pixels FIRE, multipliées par
 * {@code segmentation.facteurEchelle} (même facteur que pour la segmentation, en attendant
 * les tailles en degrés de D4).</p>
 */
public final class DetecteurBifurcations {

	/** Nom de l'étape transmis à l'observateur (le squelette après élagage). */
	public static final String ELAGAGE = "Bifurcations : squelette élagué";

	/** Nombre maximal de passes d'élagage. */
	static final int PASSES_ELAGAGE = 3;

	/** Ordre des 8 voisins : haut, puis sens des aiguilles d'une montre (comme Zhang-Suen). */
	private static final int[] DX = { 0, 1, 1, 1, 0, -1, -1, -1 };
	private static final int[] DY = { -1, -1, 0, 1, 1, 1, 0, -1 };

	/** Résultat complet, pour les figures et le visualiseur. */
	public static final class Resultat {
		private final Masque elague;
		private final List<Bifurcation> points;
		private final int ecartesAuBord;

		Resultat(Masque elague, List<Bifurcation> points, int ecartesAuBord) {
			this.elague = elague;
			this.points = points;
			this.ecartesAuBord = ecartesAuBord;
		}

		/** @return le squelette après élagage */
		public Masque elague() {
			return elague;
		}

		/** @return les points gardés, triés par y puis x (liste non modifiable) */
		public List<Bifurcation> points() {
			return points;
		}

		/** @return le nombre de points écartés parce que trop près du bord du champ */
		public int ecartesAuBord() {
			return ecartesAuBord;
		}
	}

	/** Comme {@link #detecter(Masque, Masque, ContexteExecution, Observateur)}, sans observateur. */
	public Resultat detecter(Masque squelette, Masque champ, ContexteExecution ctx) {
		return detecter(squelette, champ, ctx, Observateur.AUCUN);
	}

	/**
	 * @param squelette squelette d'un pixel d'épaisseur ({@link AmincissementZhangSuen})
	 * @param champ     masque du champ de vue, mêmes dimensions
	 * @param ctx       paramètres {@code bifurcations.*}, {@code zernike.rayon},
	 *                  {@code segmentation.facteurEchelle}
	 * @param obs       prévenu avec le squelette élagué
	 */
	public Resultat detecter(Masque squelette, Masque champ, ContexteExecution ctx, Observateur obs) {
		Parametres p = ctx.parametres();
		double f = p.reel("segmentation.facteurEchelle");
		double longueur = p.reel("bifurcations.longueurElagage") * f;
		double fusion = p.reel("bifurcations.distanceFusion") * f;
		double croisement = p.reel("bifurcations.distanceCroisement") * f;
		double marge = p.reel("zernike.rayon") * f;
		double aireBoucle = p.reel("bifurcations.aireBoucle") * f * f;
		if (!(f > 0) || !(longueur >= 0) || !(fusion >= 0) || !(croisement >= 0) || !(marge >= 0)
				|| !(aireBoucle >= 0)) {
			throw new IllegalArgumentException("Tailles des bifurcations attendues >= 0 (facteur > 0) : élagage "
					+ longueur + ", fusion " + fusion + ", croisement " + croisement + ", marge " + marge
					+ ", aire des boucles " + aireBoucle);
		}
		int l = squelette.largeur();
		int h = squelette.hauteur();
		if (champ.largeur() != l || champ.hauteur() != h) {
			throw new IllegalArgumentException("Squelette et champ de dimensions différentes");
		}

		boolean[] v = squelette.donneesBrutes().clone();
		if (remplirPetitesBoucles(v, l, h, aireBoucle) > 0) {
			v = new AmincissementZhangSuen().executer(Masque.enveloppe(v, l, h), ctx).donneesBrutes();
		}
		elaguer(v, l, h, longueur);
		Masque elague = Masque.enveloppe(v, l, h); // v n'est plus modifié ensuite
		obs.etape(ELAGAGE, null, elague);

		List<Noeud> noeuds = new ArrayList<>();
		for (int[] groupe : grouper(jonctions(v, l, h), l, fusion)) {
			Noeud n = new Noeud(groupe, l);
			int b = compterBranches(v, l, h, n.x, n.y, n.pixels, n.etendue + 2);
			if (b >= 3) {
				n.type = b >= 4 ? Bifurcation.Type.CROISEMENT : Bifurcation.Type.BIFURCATION;
				noeuds.add(n);
			}
		}
		noeuds = fusionnerCroisements(v, l, h, noeuds, croisement);

		boolean[] c = champ.donneesBrutes();
		List<Bifurcation> points = new ArrayList<>();
		int ecartes = 0;
		for (Noeud n : noeuds) {
			if (disqueDansChamp(c, l, h, n.x, n.y, marge)) {
				points.add(new Bifurcation(n.x, n.y, n.type));
			} else {
				ecartes++;
			}
		}
		points.sort(Comparator.comparingDouble(Bifurcation::y).thenComparingDouble(Bifurcation::x));
		return new Resultat(elague, java.util.Collections.unmodifiableList(points), ecartes);
	}

	// ---------------------------------------------------------------- boucles

	/**
	 * Remplit en place les zones éteintes entourées par le squelette (4-connexité, sans
	 * contact avec le bord de l'image) de moins de {@code aireMax} pixels.
	 *
	 * @return le nombre de zones remplies
	 */
	static int remplirPetitesBoucles(boolean[] v, int l, int h, double aireMax) {
		if (aireMax <= 0) {
			return 0;
		}
		boolean[] vu = new boolean[v.length];
		int[] pile = new int[v.length];
		int[] zone = new int[v.length];
		int remplies = 0;
		for (int depart = 0; depart < v.length; depart++) {
			if (v[depart] || vu[depart]) {
				continue;
			}
			int taille = 0;
			int sommet = 0;
			boolean bord = false;
			pile[sommet++] = depart;
			vu[depart] = true;
			while (sommet > 0) {
				int i = pile[--sommet];
				zone[taille++] = i;
				int x = i % l;
				int y = i / l;
				if (x == 0 || y == 0 || x == l - 1 || y == h - 1) {
					bord = true;
				}
				for (int k = 0; k < 8; k += 2) { // 4-connexité : haut, droite, bas, gauche
					int xx = x + DX[k];
					int yy = y + DY[k];
					if (xx >= 0 && yy >= 0 && xx < l && yy < h) {
						int j = yy * l + xx;
						if (!v[j] && !vu[j]) {
							vu[j] = true;
							pile[sommet++] = j;
						}
					}
				}
			}
			if (!bord && taille < aireMax) {
				for (int k = 0; k < taille; k++) {
					v[zone[k]] = true;
				}
				remplies++;
			}
		}
		return remplies;
	}

	// ---------------------------------------------------------------- élagage

	/**
	 * Retire en place les branches de longueur inférieure à {@code longueurMax} qui vont d'une
	 * extrémité à une jonction, et les bouts isolés plus courts ; puis les marches d'escalier.
	 *
	 * @return le nombre de pixels retirés
	 */
	static int elaguer(boolean[] v, int l, int h, double longueurMax) {
		int total = 0;
		int[] marque = new int[v.length]; // n° de la branche suivie qui a visité le pixel
		int numero = 0;
		for (int passe = 0; passe < PASSES_ELAGAGE; passe++) {
			List<int[]> aRetirer = new ArrayList<>();
			for (int i = 0; i < v.length; i++) {
				if (!v[i] || nombreVoisins(v, l, h, i) != 1) {
					continue;
				}
				numero++;
				int[] branche = suivreBranche(v, l, h, i, longueurMax, marque, numero);
				if (branche != null) {
					aRetirer.add(branche);
				}
			}
			int retires = 0;
			for (int[] b : aRetirer) {
				for (int i : b) {
					if (v[i]) {
						v[i] = false;
						retires++;
					}
				}
			}
			if (retires == 0) {
				break;
			}
			total += retires;
			AmincissementZhangSuen.retirerMarches(v, l, h);
		}
		return total;
	}

	/**
	 * Suit la branche qui part de l'extrémité {@code depart}.
	 *
	 * @return ses pixels (jonction exclue) si elle est à retirer, sinon null
	 */
	private static int[] suivreBranche(boolean[] v, int l, int h, int depart, double longueurMax, int[] marque,
			int numero) {
		int[] chemin = new int[16];
		int n = 0;
		int cur = depart;
		double longueur = 0;
		marque[cur] = numero;
		while (true) {
			if (cur != depart && nombreVoisins(v, l, h, cur) >= 3) {
				return longueur < longueurMax ? Arrays.copyOf(chemin, n) : null; // jonction atteinte
			}
			if (n == chemin.length) {
				chemin = Arrays.copyOf(chemin, 2 * n);
			}
			chemin[n++] = cur;
			int suivant = -1;
			int x = cur % l;
			int y = cur / l;
			for (int k = 0; k < 8 && suivant < 0; k++) {
				int xx = x + DX[k];
				int yy = y + DY[k];
				if (xx >= 0 && yy >= 0 && xx < l && yy < h) {
					int j = yy * l + xx;
					if (v[j] && marque[j] != numero) {
						suivant = j;
					}
				}
			}
			if (suivant < 0) {
				return longueur < longueurMax ? Arrays.copyOf(chemin, n) : null; // bout isolé
			}
			longueur += (suivant % l != x && suivant / l != y) ? Math.sqrt(2) : 1;
			if (longueur >= longueurMax) {
				return null; // assez longue : on garde, inutile d'aller plus loin
			}
			marque[suivant] = numero;
			cur = suivant;
		}
	}

	// ---------------------------------------------------------------- détection

	/** @return les indices des pixels du squelette dont le nombre de croisements est ≥ 3 */
	static int[] jonctions(boolean[] v, int l, int h) {
		int[] t = new int[64];
		int n = 0;
		for (int i = 0; i < v.length; i++) {
			if (v[i] && nombreCroisements(v, l, h, i) >= 3) {
				if (n == t.length) {
					t = Arrays.copyOf(t, 2 * n);
				}
				t[n++] = i;
			}
		}
		return Arrays.copyOf(t, n);
	}

	/** CN = nombre de passages éteint → allumé en faisant le tour des 8 voisins. */
	static int nombreCroisements(boolean[] v, int l, int h, int i) {
		int x = i % l;
		int y = i / l;
		int cn = 0;
		boolean precedent = allume(v, l, h, x + DX[7], y + DY[7]);
		for (int k = 0; k < 8; k++) {
			boolean courant = allume(v, l, h, x + DX[k], y + DY[k]);
			if (!precedent && courant) {
				cn++;
			}
			precedent = courant;
		}
		return cn;
	}

	/**
	 * Regroupe les pixels à distance ≤ {@code distance} les uns des autres (de proche en
	 * proche). Distance 0 : chaque pixel seul.
	 */
	static List<int[]> grouper(int[] pixels, int l, double distance) {
		int n = pixels.length;
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) {
			parent[i] = i;
		}
		double d2 = distance * distance;
		// pixels triés par indice, donc par ligne : on s'arrête dès que l'écart de ligne dépasse
		for (int a = 0; a < n; a++) {
			int xa = pixels[a] % l;
			int ya = pixels[a] / l;
			for (int b = a + 1; b < n; b++) {
				int dy = pixels[b] / l - ya;
				if (dy > distance) {
					break;
				}
				int dx = pixels[b] % l - xa;
				if (dx * dx + dy * dy <= d2) {
					parent[racine(parent, a)] = racine(parent, b);
				}
			}
		}
		List<List<Integer>> parRacine = new ArrayList<>();
		int[] indexGroupe = new int[n];
		Arrays.fill(indexGroupe, -1);
		for (int i = 0; i < n; i++) {
			int r = racine(parent, i);
			if (indexGroupe[r] < 0) {
				indexGroupe[r] = parRacine.size();
				parRacine.add(new ArrayList<>());
			}
			parRacine.get(indexGroupe[r]).add(pixels[i]);
		}
		List<int[]> groupes = new ArrayList<>();
		for (List<Integer> g : parRacine) {
			groupes.add(g.stream().mapToInt(Integer::intValue).toArray());
		}
		return groupes;
	}

	private static int racine(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	/**
	 * Nombre de branches reliées aux pixels {@code depart} qui traversent le bord du carré de
	 * demi-côté {@code k} centré sur le pixel contenant (cx, cy). On ne suit le squelette qu'à
	 * l'intérieur du carré, à partir de {@code depart} : un vaisseau voisin qui passe dans le
	 * carré sans toucher le point n'est pas compté.
	 */
	static int compterBranches(boolean[] v, int l, int h, double cx, double cy, int[] depart, int k) {
		int x0 = (int) Math.floor(cx);
		int y0 = (int) Math.floor(cy);
		int cote = 2 * k + 1;
		boolean[] atteint = new boolean[cote * cote];
		int[] pile = new int[cote * cote];
		int sommet = 0;
		for (int i : depart) {
			int lx = i % l - x0 + k;
			int ly = i / l - y0 + k;
			if (lx >= 0 && ly >= 0 && lx < cote && ly < cote && !atteint[ly * cote + lx]) {
				atteint[ly * cote + lx] = true;
				pile[sommet++] = ly * cote + lx;
			}
		}
		while (sommet > 0) {
			int q = pile[--sommet];
			int lx = q % cote;
			int ly = q / cote;
			for (int d = 0; d < 8; d++) {
				int nx = lx + DX[d];
				int ny = ly + DY[d];
				if (nx < 0 || ny < 0 || nx >= cote || ny >= cote || atteint[ny * cote + nx]) {
					continue;
				}
				if (allume(v, l, h, x0 - k + nx, y0 - k + ny)) {
					atteint[ny * cote + nx] = true;
					pile[sommet++] = ny * cote + nx;
				}
			}
		}
		// tour du bord du carré dans l'ordre, en comptant les passages non atteint → atteint
		int perimetre = 8 * k;
		boolean[] tour = new boolean[perimetre];
		int t = 0;
		for (int x = 0; x < cote - 1; x++) {
			tour[t++] = atteint[x]; // haut, de gauche à droite
		}
		for (int y = 0; y < cote - 1; y++) {
			tour[t++] = atteint[y * cote + cote - 1]; // droite, de haut en bas
		}
		for (int x = cote - 1; x > 0; x--) {
			tour[t++] = atteint[(cote - 1) * cote + x]; // bas, de droite à gauche
		}
		for (int y = cote - 1; y > 0; y--) {
			tour[t++] = atteint[y * cote]; // gauche, de bas en haut
		}
		int branches = 0;
		for (int i = 0; i < perimetre; i++) {
			if (tour[i] && !tour[(i + perimetre - 1) % perimetre]) {
				branches++;
			}
		}
		return branches;
	}

	/**
	 * Remplace chaque paire de bifurcations à distance ≤ {@code distance} (les plus proches
	 * d'abord, chaque point dans une seule paire) selon le nombre de branches autour d'elles.
	 */
	private static List<Noeud> fusionnerCroisements(boolean[] v, int l, int h, List<Noeud> noeuds, double distance) {
		List<double[]> paires = new ArrayList<>(); // {distance, a, b}
		for (int a = 0; a < noeuds.size(); a++) {
			Noeud na = noeuds.get(a);
			if (na.type != Bifurcation.Type.BIFURCATION) {
				continue;
			}
			for (int b = a + 1; b < noeuds.size(); b++) {
				Noeud nb = noeuds.get(b);
				if (nb.type != Bifurcation.Type.BIFURCATION) {
					continue;
				}
				double d = Math.hypot(na.x - nb.x, na.y - nb.y);
				if (d <= distance) {
					paires.add(new double[] { d, a, b });
				}
			}
		}
		paires.sort(Comparator.comparingDouble(q -> q[0]));
		boolean[] pris = new boolean[noeuds.size()];
		List<Noeud> sortie = new ArrayList<>();
		for (double[] q : paires) {
			int a = (int) q[1];
			int b = (int) q[2];
			if (pris[a] || pris[b]) {
				continue;
			}
			pris[a] = true;
			pris[b] = true;
			Noeud na = noeuds.get(a);
			Noeud nb = noeuds.get(b);
			Noeud m = new Noeud(na, nb);
			int k = (int) Math.ceil(Math.max(Math.hypot(na.x - m.x, na.y - m.y) + na.etendue,
					Math.hypot(nb.x - m.x, nb.y - m.y) + nb.etendue)) + 2;
			int branches = compterBranches(v, l, h, m.x, m.y, m.pixels, k);
			if (branches >= 3) {
				m.type = branches >= 4 ? Bifurcation.Type.CROISEMENT : Bifurcation.Type.BIFURCATION;
				sortie.add(m);
			}
		}
		for (int i = 0; i < noeuds.size(); i++) {
			if (!pris[i]) {
				sortie.add(noeuds.get(i));
			}
		}
		return sortie;
	}

	// ---------------------------------------------------------------- bord, outils

	/** @return vrai si tous les pixels dont le centre est à moins de {@code r} de (x, y) sont dans le champ */
	static boolean disqueDansChamp(boolean[] champ, int l, int h, double x, double y, double r) {
		int x0 = (int) Math.floor(x - r);
		int x1 = (int) Math.ceil(x + r);
		int y0 = (int) Math.floor(y - r);
		int y1 = (int) Math.ceil(y + r);
		double r2 = r * r;
		for (int j = y0; j <= y1; j++) {
			for (int i = x0; i <= x1; i++) {
				double dx = i + 0.5 - x;
				double dy = j + 0.5 - y;
				if (dx * dx + dy * dy > r2) {
					continue;
				}
				if (i < 0 || j < 0 || i >= l || j >= h || !champ[j * l + i]) {
					return false;
				}
			}
		}
		return true;
	}

	static int nombreVoisins(boolean[] v, int l, int h, int i) {
		int x = i % l;
		int y = i / l;
		int n = 0;
		for (int k = 0; k < 8; k++) {
			if (allume(v, l, h, x + DX[k], y + DY[k])) {
				n++;
			}
		}
		return n;
	}

	private static boolean allume(boolean[] v, int l, int h, int x, int y) {
		return x >= 0 && y >= 0 && x < l && y < h && v[y * l + x];
	}

	/** Point en construction : ses pixels jonctions, son centre (D1) et son étendue. */
	private static final class Noeud {
		final int[] pixels;
		final double x;
		final double y;
		/** distance maximale (arrondie au-dessus) entre le centre et un de ses pixels */
		final int etendue;
		Bifurcation.Type type;

		Noeud(int[] pixels, int l) {
			this.pixels = pixels;
			double sx = 0;
			double sy = 0;
			for (int i : pixels) {
				sx += i % l + 0.5;
				sy += i / l + 0.5;
			}
			x = sx / pixels.length;
			y = sy / pixels.length;
			double e = 0;
			for (int i : pixels) {
				e = Math.max(e, Math.hypot(i % l + 0.5 - x, i / l + 0.5 - y));
			}
			etendue = (int) Math.ceil(e);
		}

		/** Milieu de deux nœuds (croisement vu comme deux bifurcations décalées). */
		Noeud(Noeud a, Noeud b) {
			pixels = new int[a.pixels.length + b.pixels.length];
			System.arraycopy(a.pixels, 0, pixels, 0, a.pixels.length);
			System.arraycopy(b.pixels, 0, pixels, a.pixels.length, b.pixels.length);
			x = (a.x + b.x) / 2;
			y = (a.y + b.y) / 2;
			etendue = 0;
		}
	}
}
