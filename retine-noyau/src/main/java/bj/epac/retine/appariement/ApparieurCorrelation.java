package bj.epac.retine.appariement;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import bj.epac.retine.bifurcation.Bifurcation;
import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.zernike.DescripteurZernike;

/**
 * Appariement des bifurcations de deux visites par leurs descripteurs de Zernike (Point 2,
 * section 13 de Docs/algorithmes.md).
 *
 * <ol>
 * <li><b>Score.</b> Coefficient de corrélation entre les deux vecteurs descripteurs : 1 =
 * mêmes formes, 0 = aucun rapport. Il ne dépend ni du niveau ni de l'échelle des valeurs.</li>
 * <li><b>Choix mutuel.</b> Un couple (a, b) n'est gardé que si b est le meilleur score de a
 * parmi les points du suivi, <em>et</em> a le meilleur score de b parmi ceux de la
 * référence : un point qui ressemble vaguement à tout le monde n'est le choix mutuel de
 * personne.</li>
 * <li><b>Seuil.</b> On ne garde que les couples de score ≥ {@code appariement.seuil} ; la
 * valeur définitive viendra de l'EER mesurée par le binôme B (section 17.3).</li>
 * </ol>
 *
 * <p>Aucune limite de distance entre les deux positions (section 13.4) : sur les paires à
 * faible recouvrement, le déplacement peut être grand.</p>
 */
public final class ApparieurCorrelation {

	/** Un couple apparié. Immuable. */
	public static final class Couple {
		private final Bifurcation reference;
		private final Bifurcation suivi;
		private final double score;

		public Couple(Bifurcation reference, Bifurcation suivi, double score) {
			this.reference = reference;
			this.suivi = suivi;
			this.score = score;
		}

		/** @return le point de la visite 1 */
		public Bifurcation reference() {
			return reference;
		}

		/** @return le point de la visite 2 */
		public Bifurcation suivi() {
			return suivi;
		}

		/** @return le coefficient de corrélation des deux descripteurs, dans [−1, 1] */
		public double score() {
			return score;
		}
	}

	/**
	 * @param reference descripteurs de la visite 1
	 * @param suivi     descripteurs de la visite 2
	 * @param ctx       paramètre {@code appariement.seuil}
	 * @return les couples mutuels de score ≥ seuil, du meilleur score au moins bon (liste
	 *         non modifiable)
	 */
	public List<Couple> apparier(List<DescripteurZernike.Descripteur> reference,
			List<DescripteurZernike.Descripteur> suivi, ContexteExecution ctx) {
		double seuil = ctx.parametres().reel("appariement.seuil");
		if (!(seuil >= -1 && seuil <= 1)) {
			throw new IllegalArgumentException("appariement.seuil attendu dans [-1, 1] : " + seuil);
		}
		double[][] scores = scores(reference, suivi);
		int na = reference.size();
		int nb = suivi.size();
		int[] meilleurDeA = new int[na];
		int[] meilleurDeB = new int[nb];
		java.util.Arrays.fill(meilleurDeA, -1);
		java.util.Arrays.fill(meilleurDeB, -1);
		for (int a = 0; a < na; a++) {
			for (int b = 0; b < nb; b++) {
				double s = scores[a][b];
				if (Double.isNaN(s)) {
					continue;
				}
				if (meilleurDeA[a] < 0 || s > scores[a][meilleurDeA[a]]) {
					meilleurDeA[a] = b;
				}
				if (meilleurDeB[b] < 0 || s > scores[meilleurDeB[b]][b]) {
					meilleurDeB[b] = a;
				}
			}
		}
		List<Couple> couples = new ArrayList<>();
		for (int a = 0; a < na; a++) {
			int b = meilleurDeA[a];
			if (b >= 0 && meilleurDeB[b] == a && scores[a][b] >= seuil) {
				couples.add(new Couple(reference.get(a).point(), suivi.get(b).point(), scores[a][b]));
			}
		}
		couples.sort(Comparator.comparingDouble(Couple::score).reversed());
		return Collections.unmodifiableList(couples);
	}

	/**
	 * Matrice des scores : coefficient de corrélation entre chaque descripteur de
	 * {@code reference} (lignes) et de {@code suivi} (colonnes). NaN si l'un des deux vecteurs
	 * est constant (fenêtre uniforme).
	 */
	public static double[][] scores(List<DescripteurZernike.Descripteur> reference,
			List<DescripteurZernike.Descripteur> suivi) {
		double[][] a = centresReduits(reference);
		double[][] b = centresReduits(suivi);
		double[][] s = new double[a.length][b.length];
		for (int i = 0; i < a.length; i++) {
			for (int j = 0; j < b.length; j++) {
				if (a[i] == null || b[j] == null) {
					s[i][j] = Double.NaN;
					continue;
				}
				if (a[i].length != b[j].length) {
					throw new IllegalArgumentException("Descripteurs de longueurs différentes : " + a[i].length
							+ " et " + b[j].length + " (même ordre de Zernike pour les deux visites ?)");
				}
				double p = 0;
				for (int k = 0; k < a[i].length; k++) {
					p += a[i][k] * b[j][k];
				}
				s[i][j] = p;
			}
		}
		return s;
	}

	/** Coefficient de corrélation de deux vecteurs ; NaN si l'un est constant. */
	public static double correlation(double[] a, double[] b) {
		double[] x = centreReduit(a);
		double[] y = centreReduit(b);
		if (x == null || y == null) {
			return Double.NaN;
		}
		double p = 0;
		for (int k = 0; k < x.length; k++) {
			p += x[k] * y[k];
		}
		return p;
	}

	private static double[][] centresReduits(List<DescripteurZernike.Descripteur> d) {
		double[][] v = new double[d.size()][];
		for (int i = 0; i < v.length; i++) {
			v[i] = centreReduit(d.get(i).valeurs());
		}
		return v;
	}

	/** @return (v − moyenne) / ‖v − moyenne‖, ou null si v est constant */
	private static double[] centreReduit(double[] v) {
		double m = 0;
		for (double x : v) {
			m += x;
		}
		m /= v.length;
		double n2 = 0;
		double[] c = new double[v.length];
		for (int k = 0; k < v.length; k++) {
			c[k] = v[k] - m;
			n2 += c[k] * c[k];
		}
		if (n2 < 1e-24) {
			return null;
		}
		double n = Math.sqrt(n2);
		for (int k = 0; k < c.length; k++) {
			c[k] /= n;
		}
		return c;
	}

	/**
	 * Écrit les couples au format d'échange D6 : en-tête
	 * {@code x_ref,y_ref,x_suivi,y_suivi,score}, point décimal. Crée les dossiers manquants.
	 */
	public static void ecrireCsv(Path fichier, List<Couple> couples) throws IOException {
		if (fichier.getParent() != null) {
			Files.createDirectories(fichier.getParent());
		}
		try (Writer w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
			w.write("x_ref,y_ref,x_suivi,y_suivi,score\n");
			for (Couple c : couples) {
				w.write(String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f,%.4f\n", c.reference.x(), c.reference.y(),
						c.suivi.x(), c.suivi.y(), c.score));
			}
		}
	}
}
