package bj.epac.retine.pretraitement;

import java.util.Arrays;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.Masque;
import bj.epac.retine.parametres.Parametres;

/**
 * Détecte le champ de vision : le disque rétinien clair sur fond noir.
 *
 * <ol>
 * <li>Seuillage du canal rouge (le plus lumineux dans le disque) à
 * {@code masqueChampVision.seuil}.</li>
 * <li>Plus grande composante connexe (4-connexité) : élimine reflets isolés et texte
 * incrusté ; les trous intérieurs sont ensuite comblés.</li>
 * <li>Érosion de {@code masqueChampVision.erosion} pixels (disque). Indispensable : le
 * bord du champ est une transition progressive qu'une détection de vaisseaux prendrait
 * pour un vaisseau sur toute la circonférence. Les bords de l'image comptent comme
 * « dehors ».</li>
 * <li>Centre (centroïde) et rayon équivalent {@code sqrt(aire/π)} stockés comme
 * métadonnée du masque, selon la convention D1 (centre du pixel en x+0.5).</li>
 * </ol>
 *
 * <p>Le masque rendu suit le champ réel (souvent tronqué en haut et en bas par le
 * capteur), ce n'est pas un cercle idéal.</p>
 */
public final class MasqueChampVision implements Etape<ImageCouleur, Masque> {

	@Override
	public Masque executer(ImageCouleur entree, ContexteExecution ctx) {
		Parametres p = ctx.parametres();
		double seuil = p.reel("masqueChampVision.seuil");
		int erosion = p.entier("masqueChampVision.erosion");
		if (erosion < 0) {
			throw new IllegalArgumentException("masqueChampVision.erosion doit être >= 0 : " + erosion);
		}
		int l = entree.largeur();
		int h = entree.hauteur();

		float[] rouge = entree.rougeBrut();
		boolean[] clair = new boolean[l * h];
		for (int i = 0; i < clair.length; i++) {
			clair[i] = rouge[i] > seuil;
		}

		boolean[] champ = plusGrandeComposante(clair, l, h);
		if (champ == null) {
			throw new IllegalStateException("Champ de vision non détecté : aucun pixel au-dessus du seuil "
					+ seuil + " (canal rouge).");
		}
		combler(champ, l, h);
		boolean[] erode = eroder(champ, l, h, erosion);

		long n = 0;
		double sx = 0;
		double sy = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < l; x++) {
				if (erode[y * l + x]) {
					n++;
					sx += x + 0.5;
					sy += y + 0.5;
				}
			}
		}
		if (n == 0) {
			throw new IllegalStateException("Champ de vision vide après érosion de " + erosion + " pixels.");
		}
		return Masque.enveloppeAvecCercle(erode, l, h, sx / n, sy / n, Math.sqrt(n / Math.PI));
	}

	/** @return le masque de la plus grande composante 4-connexe, ou null si {@code vrai} est vide */
	private static boolean[] plusGrandeComposante(boolean[] vrai, int l, int h) {
		int[] etiquette = new int[vrai.length];
		int[] pile = new int[vrai.length];
		int meilleure = 0;
		int meilleureTaille = 0;
		int courante = 0;
		for (int depart = 0; depart < vrai.length; depart++) {
			if (!vrai[depart] || etiquette[depart] != 0) {
				continue;
			}
			courante++;
			int taille = 0;
			int sommet = 0;
			pile[sommet++] = depart;
			etiquette[depart] = courante;
			while (sommet > 0) {
				int i = pile[--sommet];
				taille++;
				int x = i % l;
				int y = i / l;
				if (x > 0 && vrai[i - 1] && etiquette[i - 1] == 0) {
					etiquette[i - 1] = courante;
					pile[sommet++] = i - 1;
				}
				if (x < l - 1 && vrai[i + 1] && etiquette[i + 1] == 0) {
					etiquette[i + 1] = courante;
					pile[sommet++] = i + 1;
				}
				if (y > 0 && vrai[i - l] && etiquette[i - l] == 0) {
					etiquette[i - l] = courante;
					pile[sommet++] = i - l;
				}
				if (y < h - 1 && vrai[i + l] && etiquette[i + l] == 0) {
					etiquette[i + l] = courante;
					pile[sommet++] = i + l;
				}
			}
			if (taille > meilleureTaille) {
				meilleureTaille = taille;
				meilleure = courante;
			}
		}
		if (meilleure == 0) {
			return null;
		}
		boolean[] sortie = new boolean[vrai.length];
		for (int i = 0; i < sortie.length; i++) {
			sortie[i] = etiquette[i] == meilleure;
		}
		return sortie;
	}

	/** Comble en place les trous : tout pixel faux non relié au bord de l'image par des pixels faux. */
	private static void combler(boolean[] champ, int l, int h) {
		boolean[] dehors = new boolean[champ.length];
		int[] pile = new int[champ.length];
		int sommet = 0;
		for (int x = 0; x < l; x++) {
			sommet = empiler(champ, dehors, pile, sommet, x);
			sommet = empiler(champ, dehors, pile, sommet, (h - 1) * l + x);
		}
		for (int y = 0; y < h; y++) {
			sommet = empiler(champ, dehors, pile, sommet, y * l);
			sommet = empiler(champ, dehors, pile, sommet, y * l + l - 1);
		}
		while (sommet > 0) {
			int i = pile[--sommet];
			int x = i % l;
			int y = i / l;
			if (x > 0) {
				sommet = empiler(champ, dehors, pile, sommet, i - 1);
			}
			if (x < l - 1) {
				sommet = empiler(champ, dehors, pile, sommet, i + 1);
			}
			if (y > 0) {
				sommet = empiler(champ, dehors, pile, sommet, i - l);
			}
			if (y < h - 1) {
				sommet = empiler(champ, dehors, pile, sommet, i + l);
			}
		}
		for (int i = 0; i < champ.length; i++) {
			champ[i] = !dehors[i];
		}
	}

	private static int empiler(boolean[] champ, boolean[] dehors, int[] pile, int sommet, int i) {
		if (!champ[i] && !dehors[i]) {
			dehors[i] = true;
			pile[sommet++] = i;
		}
		return sommet;
	}

	/**
	 * Érosion par un disque de rayon {@code rayon} ; l'extérieur de l'image compte comme faux.
	 * Pour chaque décalage vertical dy, la demi-largeur horizontale du disque est
	 * {@code floor(sqrt(rayon² - dy²))} ; « aucun faux dans [x-w, x+w] » se teste en O(1)
	 * avec des sommes cumulées.
	 */
	private static boolean[] eroder(boolean[] champ, int l, int h, int rayon) {
		if (rayon == 0) {
			return champ.clone();
		}
		boolean[] sortie = new boolean[champ.length];
		Arrays.fill(sortie, true);
		int[] cumul = new int[l + 1];
		boolean[] libre = new boolean[champ.length];
		for (int w = 0; w <= rayon; w++) {
			// décalages dy dont la demi-largeur vaut w
			boolean utile = false;
			for (int dy = -rayon; dy <= rayon && !utile; dy++) {
				utile = demiLargeur(rayon, dy) == w;
			}
			if (!utile) {
				continue;
			}
			for (int y = 0; y < h; y++) {
				int base = y * l;
				cumul[0] = 0;
				for (int x = 0; x < l; x++) {
					cumul[x + 1] = cumul[x] + (champ[base + x] ? 0 : 1);
				}
				for (int x = 0; x < l; x++) {
					int a = x - w;
					int b = x + w;
					libre[base + x] = a >= 0 && b < l && cumul[b + 1] - cumul[a] == 0;
				}
			}
			for (int dy = -rayon; dy <= rayon; dy++) {
				if (demiLargeur(rayon, dy) != w) {
					continue;
				}
				for (int y = 0; y < h; y++) {
					int yy = y + dy;
					int base = y * l;
					if (yy < 0 || yy >= h) {
						Arrays.fill(sortie, base, base + l, false);
					} else {
						int src = yy * l;
						for (int x = 0; x < l; x++) {
							sortie[base + x] &= libre[src + x];
						}
					}
				}
			}
		}
		return sortie;
	}

	private static int demiLargeur(int rayon, int dy) {
		return (int) Math.floor(Math.sqrt((double) rayon * rayon - (double) dy * dy));
	}
}
