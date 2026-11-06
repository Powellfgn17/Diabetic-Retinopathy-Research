package bj.epac.retine.squelette;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.chaine.Observateur;
import bj.epac.retine.noyau.image.Masque;

/**
 * Squelette du masque des vaisseaux : lignes d'un pixel d'épaisseur qui suivent le centre
 * des vaisseaux et gardent leur topologie (mêmes morceaux, mêmes trous). Décision D3 : codé
 * ici en Java pur plutôt qu'avec AnalyzeSkeleton de Fiji.
 *
 * <p><b>1. Amincissement de Zhang-Suen (1984).</b> On ronge le masque couche par couche.
 * Pour chaque pixel P1 allumé, avec ses voisins numérotés à partir du haut, dans le sens des
 * aiguilles d'une montre :</p>
 * <pre>
 *   P9 P2 P3
 *   P8 P1 P4
 *   P7 P6 P5
 * </pre>
 * <p>B = nombre de voisins allumés, A = nombre de passages éteint → allumé dans la suite
 * P2, P3, …, P9, P2. P1 est retiré si 2 ≤ B ≤ 6 (ni extrémité, ni pixel intérieur), A = 1
 * (le retirer ne coupe rien) et, selon la sous-passe :</p>
 * <ul>
 * <li>sous-passe 1 : P2·P4·P6 = 0 et P4·P6·P8 = 0 (ronge les bords sud et est) ;</li>
 * <li>sous-passe 2 : P2·P4·P8 = 0 et P2·P6·P8 = 0 (ronge les bords nord et ouest).</li>
 * </ul>
 * <p>Dans une sous-passe, tous les pixels à retirer sont d'abord marqués, puis retirés
 * ensemble ; on alterne les deux sous-passes jusqu'à ce que plus rien ne change, ce qui
 * garde le squelette centré.</p>
 *
 * <p><b>2. Retrait des marches d'escalier.</b> Zhang-Suen laisse des pixels en trop là où une
 * ligne tourne ou descend en diagonale (deux pixels voisins par un côté au lieu d'un seul
 * par un coin). On retire, un par un dans l'ordre de lecture, chaque pixel qui n'est pas une
 * extrémité et dont le retrait ne change pas la topologie (pixel « simple » : ses voisins
 * allumés forment un seul morceau en 8-connexité, et ses voisins éteints touchant ses côtés
 * un seul morceau en 4-connexité). Le résultat est une ligne d'un pixel en 8-connexité.
 * Ce retrait peut déplacer une bifurcation d'un pixel, jamais la supprimer.</p>
 *
 * <p>Mesuré sur FIRE S01 (06/11/2026) : la passe retire 12 % des pixels de Zhang-Suen. Le
 * nombre de croisements y est peu sensible (408 jonctions avant, 410 après), mais sans elle
 * 14 784 pixels ordinaires ont 3 voisins ou plus : suivre une branche pixel par pixel
 * (élagage des barbes, longueur des branches) n'aurait plus un seul pixel suivant.</p>
 *
 * <p>Temps : environ 0,3 s pour une image FIRE (2912 × 2912).</p>
 *
 * <p>L'extérieur de l'image compte comme éteint. Aucun paramètre.</p>
 */
public final class AmincissementZhangSuen implements Etape<Masque, Masque> {

	/** Noms des étapes transmis à l'observateur. */
	public static final String ZHANG_SUEN = "Squelette : amincissement de Zhang-Suen";
	public static final String MARCHES = "Squelette : marches d'escalier retirées";

	/**
	 * Codage du voisinage sur 8 bits : bit k = voisin P(k+2), soit P2 (haut) pour le bit 0,
	 * puis dans le sens des aiguilles d'une montre jusqu'à P9 (haut-gauche) pour le bit 7.
	 */
	private static final int[] DX = { 0, 1, 1, 1, 0, -1, -1, -1 };
	private static final int[] DY = { -1, -1, 0, 1, 1, 1, 0, -1 };

	private static final boolean[] RETIRABLE_PASSE_1 = new boolean[256];
	private static final boolean[] RETIRABLE_PASSE_2 = new boolean[256];
	private static final boolean[] SIMPLE_NON_EXTREMITE = new boolean[256];

	static {
		for (int c = 0; c < 256; c++) {
			int b = Integer.bitCount(c);
			int a = 0;
			for (int k = 0; k < 8; k++) {
				if (!bit(c, k) && bit(c, (k + 1) % 8)) {
					a++;
				}
			}
			boolean p2 = bit(c, 0), p4 = bit(c, 2), p6 = bit(c, 4), p8 = bit(c, 6);
			boolean commun = b >= 2 && b <= 6 && a == 1;
			RETIRABLE_PASSE_1[c] = commun && !(p2 && p4 && p6) && !(p4 && p6 && p8);
			RETIRABLE_PASSE_2[c] = commun && !(p2 && p4 && p8) && !(p2 && p6 && p8);
			SIMPLE_NON_EXTREMITE[c] = b >= 2 && composantesAllumees(c) == 1 && composantesEteintesAuxCotes(c) == 1;
		}
	}

	@Override
	public Masque executer(Masque entree, ContexteExecution ctx) {
		return executer(entree, ctx, Observateur.AUCUN);
	}

	/**
	 * Comme {@link #executer(Masque, ContexteExecution)}, en prévenant {@code obs} après
	 * l'amincissement puis après le retrait des marches (image null : l'étape ne produit
	 * qu'un masque).
	 *
	 * @param entree masque des vaisseaux
	 * @param ctx    contexte (non utilisé : aucun paramètre)
	 * @param obs    observateur des résultats intermédiaires
	 * @return le squelette, inclus dans {@code entree}
	 */
	public Masque executer(Masque entree, ContexteExecution ctx, Observateur obs) {
		int l = entree.largeur();
		int h = entree.hauteur();
		boolean[] v = amincir(entree.donneesBrutes(), l, h);
		if (obs != Observateur.AUCUN) {
			obs.etape(ZHANG_SUEN, null, new Masque(v, l, h)); // copie : v est encore modifié
		}
		retirerMarches(v, l, h);
		Masque squelette = Masque.enveloppe(v, l, h);
		obs.etape(MARCHES, null, squelette);
		return squelette;
	}

	/** Zhang-Suen sur une copie de {@code src} ; ne parcourt que les pixels encore allumés. */
	static boolean[] amincir(boolean[] src, int l, int h) {
		boolean[] v = src.clone();
		int n = 0;
		for (boolean b : v) {
			if (b) {
				n++;
			}
		}
		int[] allumes = new int[n];
		int j = 0;
		for (int i = 0; i < v.length; i++) {
			if (v[i]) {
				allumes[j++] = i;
			}
		}
		int[] aRetirer = new int[n];
		boolean change = true;
		while (change) {
			change = false;
			for (int passe = 0; passe < 2; passe++) {
				boolean[] table = passe == 0 ? RETIRABLE_PASSE_1 : RETIRABLE_PASSE_2;
				int k = 0;
				for (int t = 0; t < n; t++) {
					int i = allumes[t];
					if (table[voisinage(v, i % l, i / l, l, h)]) {
						aRetirer[k++] = i;
					}
				}
				if (k == 0) {
					continue;
				}
				change = true;
				for (int t = 0; t < k; t++) {
					v[aRetirer[t]] = false;
				}
				int m = 0;
				for (int t = 0; t < n; t++) {
					if (v[allumes[t]]) {
						allumes[m++] = allumes[t];
					}
				}
				n = m;
			}
		}
		return v;
	}

	/**
	 * Retire en place, un par un dans l'ordre de lecture, les pixels simples qui ne sont pas
	 * des extrémités, jusqu'à ce que plus aucun ne le soit.
	 */
	static void retirerMarches(boolean[] v, int l, int h) {
		boolean change = true;
		while (change) {
			change = false;
			for (int i = 0; i < v.length; i++) {
				if (v[i] && SIMPLE_NON_EXTREMITE[voisinage(v, i % l, i / l, l, h)]) {
					v[i] = false;
					change = true;
				}
			}
		}
	}

	/** @return le code 8 bits du voisinage de (x, y) ; l'extérieur de l'image compte comme éteint */
	static int voisinage(boolean[] v, int x, int y, int l, int h) {
		int c = 0;
		for (int k = 0; k < 8; k++) {
			int xx = x + DX[k];
			int yy = y + DY[k];
			if (xx >= 0 && yy >= 0 && xx < l && yy < h && v[yy * l + xx]) {
				c |= 1 << k;
			}
		}
		return c;
	}

	private static boolean bit(int c, int k) {
		return (c >> k & 1) != 0;
	}

	/** Nombre de morceaux 8-connexes formés par les voisins allumés (le centre exclu). */
	private static int composantesAllumees(int c) {
		return composantes(c, true, false);
	}

	/** Nombre de morceaux 4-connexes de voisins éteints qui touchent un côté du centre. */
	private static int composantesEteintesAuxCotes(int c) {
		return composantes(~c & 0xff, false, true);
	}

	/**
	 * @param c            les voisins à regrouper (bit k = voisin k)
	 * @param huitConnexe  true : deux voisins se touchent aussi par un coin ; false : par un côté
	 * @param auxCotesSeul ne compter que les morceaux contenant un voisin de côté (P2, P4, P6, P8)
	 */
	private static int composantes(int c, boolean huitConnexe, boolean auxCotesSeul) {
		int vu = 0;
		int nombre = 0;
		for (int depart = 0; depart < 8; depart++) {
			if (!bit(c, depart) || bit(vu, depart)) {
				continue;
			}
			int morceau = 1 << depart;
			int pile = 1 << depart;
			while (pile != 0) {
				int k = Integer.numberOfTrailingZeros(pile);
				pile &= pile - 1;
				for (int q = 0; q < 8; q++) {
					if (bit(c, q) && !bit(morceau, q) && voisins(k, q, huitConnexe)) {
						morceau |= 1 << q;
						pile |= 1 << q;
					}
				}
			}
			vu |= morceau;
			boolean toucheUnCote = (morceau & 0b01010101) != 0; // bits 0, 2, 4, 6 = P2, P4, P6, P8
			if (!auxCotesSeul || toucheUnCote) {
				nombre++;
			}
		}
		return nombre;
	}

	private static boolean voisins(int a, int b, boolean huitConnexe) {
		int dx = Math.abs(DX[a] - DX[b]);
		int dy = Math.abs(DY[a] - DY[b]);
		return huitConnexe ? Math.max(dx, dy) == 1 : dx + dy == 1;
	}
}
