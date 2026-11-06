package bj.epac.retine.segmentation;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.chaine.Observateur;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.ImageMasquee;
import bj.epac.retine.noyau.image.Masque;
import bj.epac.retine.parametres.Parametres;

/**
 * Segmentation des vaisseaux par le Hessien multi-échelle (chapitres 6 et 7).
 *
 * <p>Entrée : la sortie de la chaîne de prétraitement (vaisseaux sombres sur fond clair,
 * 0 hors masque). Pour chaque échelle σ :</p>
 * <ol>
 * <li>dérivées secondes Ixx, Iyy, Ixy par convolution séparable avec les dérivées d'une
 * gaussienne d'écart-type σ (lissage et dérivation en une seule passe) ;</li>
 * <li>valeurs propres du Hessien, |λ1| ≤ |λ2| : un vaisseau sombre est un creux, donc
 * λ2 fortement positive. Réponse {@code σ²·λ2} si λ2 > 0, sinon 0 (le facteur σ² rend
 * les échelles comparables).</li>
 * </ol>
 * <p>Avant le Hessien, l'image est écrêtée au percentile
 * {@code segmentation.percentileEcretage} de ses valeurs dans le masque (médiane par
 * défaut). Le pied d'une tache claire (exsudat, reflet) forme un creux que le Hessien
 * prend pour un vaisseau sombre : sur FIRE A01, chaque exsudat était entouré d'un anneau
 * de faux vaisseaux. Les vaisseaux étant plus sombres que le fond, l'écrêtage ne leur
 * retire rien ; seuls les petits vaisseaux du disque optique, plus clairs que la médiane
 * de la rétine, peuvent s'affaiblir. 100 désactive l'écrêtage.</p>
 *
 * <p>On garde le maximum sur les échelles, puis :</p>
 * <ol>
 * <li>seuillage par hystérésis : les pixels au-dessus du percentile
 * {@code segmentation.percentileHaut} de la réponse dans le masque sont des germes ;
 * ceux au-dessus de {@code segmentation.percentileBas} ne sont gardés que s'ils touchent
 * un germe (8-connexité) ;</li>
 * <li>retrait des composantes de moins de {@code segmentation.aireMin} pixels.</li>
 * </ol>
 *
 * <p><b>Bord du champ de vue.</b> Hors masque l'image vaut 0 : le bord est une marche
 * vers le bas, dont la dérivée seconde est négative côté intérieur. Avec le signe
 * « vaisseau sombre », il ne produit donc pas de faux vaisseau (vérifié sur FIRE A01,
 * 05/11/2026), au prix d'une détection affaiblie à moins de ~2σ du bord. Hors masque, la
 * réponse et le masque de sortie valent 0. Ne pas remplacer le 0 hors masque par un
 * prolongement sans revérifier ce point.</p>
 *
 * <p><b>Tailles.</b> Les σ ({@code segmentation.sigmas}) et l'aire minimale sont donnés
 * en pixels FIRE, puis multipliés par {@code segmentation.facteurEchelle} (et son carré
 * pour l'aire). Pour un autre jeu : facteur = ppd du jeu / ppd de FIRE. Ce sera remplacé
 * par des tailles en degrés quand le ppd de chaque jeu sera mesuré (décision D4).</p>
 *
 * <p>Seuils en percentiles plutôt qu'en valeur absolue : la réponse dépend du contraste
 * de chaque image, sa distribution beaucoup moins.</p>
 */
public final class SegmentationHessienne implements Etape<ImageMasquee, Masque> {

	/** Rayon des noyaux en nombre de σ : 4σ garde la dérivée seconde précise à 1e-3 près. */
	private static final double RAYON_NOYAU_SIGMAS = 4.0;

	/** Réponse multi-échelle et masque des vaisseaux. */
	public static final class Resultat {
		private final ImageGris reponse;
		private final Masque vaisseaux;

		Resultat(ImageGris reponse, Masque vaisseaux) {
			this.reponse = reponse;
			this.vaisseaux = vaisseaux;
		}

		/** @return la réponse max_σ σ²·λ2⁺, 0 hors masque (pour les figures et les courbes ROC) */
		public ImageGris reponse() {
			return reponse;
		}

		/** @return le masque binaire des vaisseaux, inclus dans le masque d'entrée */
		public Masque vaisseaux() {
			return vaisseaux;
		}
	}

	@Override
	public Masque executer(ImageMasquee entree, ContexteExecution ctx) {
		return segmenter(entree, ctx).vaisseaux();
	}

	/**
	 * Calcule la réponse et le masque en une passe.
	 *
	 * @param entree image prétraitée et son masque
	 * @param ctx    contexte (paramètres {@code segmentation.*})
	 * @return réponse et masque des vaisseaux
	 */
	public Resultat segmenter(ImageMasquee entree, ContexteExecution ctx) {
		return segmenter(entree, ctx, Observateur.AUCUN);
	}

	/** Noms des étapes transmis à l'observateur (les réponses par échelle s'y ajoutent). */
	public static final String ECRETAGE = "Écrêtage à la médiane";
	public static final String REPONSE = "Réponse multi-échelle (max σ²·λ2)";
	public static final String GERMES = "Hystérésis : germes (seuil haut)";
	public static final String HYSTERESIS = "Hystérésis : germes + pixels reliés";
	public static final String VAISSEAUX = "Vaisseaux (après filtre d'aire)";

	/** @return le nom transmis à l'observateur pour la réponse à l'échelle σ (en pixels) */
	public static String nomReponseEchelle(double sigma) {
		String v = String.format(java.util.Locale.FRANCE, "%.2f", sigma).replaceAll(",?0+$", ""); // 2 ; 1,5
		return "Réponse à σ = " + v + " px";
	}

	/**
	 * Comme {@link #segmenter(ImageMasquee, ContexteExecution)}, en prévenant {@code obs}
	 * après chaque étape : écrêtage, réponse de chaque échelle, réponse multi-échelle,
	 * germes, hystérésis, résultat. Les réponses par échelle ne sont copiées que si un
	 * observateur est fourni.
	 *
	 * @param entree image prétraitée et son masque
	 * @param ctx    contexte (paramètres {@code segmentation.*})
	 * @param obs    observateur des résultats intermédiaires
	 * @return réponse et masque des vaisseaux
	 */
	public Resultat segmenter(ImageMasquee entree, ContexteExecution ctx, Observateur obs) {
		Parametres p = ctx.parametres();
		double facteur = p.reel("segmentation.facteurEchelle");
		double[] sigmas = p.reels("segmentation.sigmas");
		double haut = p.reel("segmentation.percentileHaut");
		double bas = p.reel("segmentation.percentileBas");
		int aireMin = p.entier("segmentation.aireMin");
		double ecretage = p.reel("segmentation.percentileEcretage");
		if (!(facteur > 0)) {
			throw new IllegalArgumentException("segmentation.facteurEchelle doit être > 0 : " + facteur);
		}
		if (sigmas.length == 0) {
			throw new IllegalArgumentException("segmentation.sigmas est vide");
		}
		for (int i = 0; i < sigmas.length; i++) {
			if (!(sigmas[i] > 0)) {
				throw new IllegalArgumentException("segmentation.sigmas : σ doit être > 0 : " + sigmas[i]);
			}
			sigmas[i] *= facteur;
		}
		if (!(0 <= bas && bas <= haut && haut <= 100)) {
			throw new IllegalArgumentException("Percentiles attendus avec 0 ≤ bas ≤ haut ≤ 100 : bas = " + bas
					+ ", haut = " + haut);
		}
		if (!(0 <= ecretage && ecretage <= 100)) {
			throw new IllegalArgumentException("segmentation.percentileEcretage attendu dans [0,100] : " + ecretage);
		}
		if (aireMin < 0) {
			throw new IllegalArgumentException("segmentation.aireMin doit être >= 0 : " + aireMin);
		}

		int l = entree.largeur();
		int h = entree.hauteur();
		boolean[] m = entree.masque().donneesBrutes();
		Masque masque = entree.masque();
		float[] img = ecreter(entree.image().donneesBrutes(), m, ecretage);
		obs.etape(ECRETAGE, ImageGris.enveloppe(img, l, h), masque);
		BiConsumer<Double, float[]> parEchelle = obs == Observateur.AUCUN ? null
				: (s, r) -> obs.etape(nomReponseEchelle(s), ImageGris.enveloppe(r, l, h), masque);
		float[] rep = reponse(img, m, l, h, sigmas, parEchelle);
		ImageGris reponse = ImageGris.enveloppe(rep, l, h);
		obs.etape(REPONSE, reponse, masque);

		float[] dansMasque = valeursDansMasque(rep, m);
		if (dansMasque.length == 0) {
			throw new IllegalStateException("Masque vide : rien à segmenter.");
		}
		Arrays.sort(dansMasque);
		float seuilHaut = percentile(dansMasque, haut);
		float seuilBas = percentile(dansMasque, bas);
		if (obs != Observateur.AUCUN) {
			boolean[] germes = new boolean[rep.length];
			for (int i = 0; i < rep.length; i++) {
				germes[i] = m[i] && rep[i] > 0 && rep[i] >= seuilHaut;
			}
			obs.etape(GERMES, reponse, Masque.enveloppe(germes, l, h));
		}
		boolean[] v = hysteresis(rep, m, l, h, seuilHaut, seuilBas);
		if (obs != Observateur.AUCUN) {
			obs.etape(HYSTERESIS, reponse, new Masque(v, l, h)); // copie : v est encore filtré
		}
		retirerPetitesComposantes(v, l, h, (int) Math.round(aireMin * facteur * facteur));
		Masque vaisseaux = Masque.enveloppe(v, l, h);
		obs.etape(VAISSEAUX, entree.image(), vaisseaux);
		return new Resultat(reponse, vaisseaux);
	}

	/**
	 * Copie de l'image où tout ce qui dépasse le percentile {@code p} (calculé dans le
	 * masque) est ramené à ce percentile ; 100 rend une copie inchangée. Le plafond
	 * s'applique aussi hors masque : sinon un extérieur non nul et plus clair que le
	 * plafond formerait une marche montante au bord du masque, donc un faux creux juste à
	 * l'intérieur (en sortie de prétraitement l'extérieur vaut 0 et n'est pas touché).
	 */
	static float[] ecreter(float[] img, boolean[] m, double p) {
		float[] sortie = img.clone();
		if (p >= 100) {
			return sortie;
		}
		float[] dans = valeursDansMasque(img, m);
		if (dans.length == 0) {
			return sortie;
		}
		Arrays.sort(dans);
		float plafond = percentile(dans, p);
		for (int i = 0; i < sortie.length; i++) {
			if (sortie[i] > plafond) {
				sortie[i] = plafond;
			}
		}
		return sortie;
	}

	/** Réponse max_σ σ²·λ2⁺, mise à 0 hors masque. */
	static float[] reponse(float[] img, boolean[] m, int l, int h, double[] sigmas) {
		return reponse(img, m, l, h, sigmas, null);
	}

	/**
	 * Idem, et si {@code parEchelle} n'est pas null, lui passe la réponse de chaque échelle
	 * (nouveau tableau, 0 hors masque, propriété transférée).
	 */
	static float[] reponse(float[] img, boolean[] m, int l, int h, double[] sigmas,
			BiConsumer<Double, float[]> parEchelle) {
		int n = l * h;
		float[] max = new float[n];
		float[] tmp = new float[n];
		float[] dxx = new float[n];
		float[] dyy = new float[n];
		float[] dxy = new float[n];
		for (double s : sigmas) {
			float[][] k = noyaux(s);
			float[] g = k[0];
			float[] g1 = k[1];
			float[] g2 = k[2];
			convolutionX(img, tmp, l, h, g2);
			convolutionY(tmp, dxx, l, h, g);
			convolutionX(img, tmp, l, h, g);
			convolutionY(tmp, dyy, l, h, g2);
			convolutionX(img, tmp, l, h, g1);
			convolutionY(tmp, dxy, l, h, g1);
			final double s2 = s * s;
			final float[] ech = parEchelle == null ? null : new float[n];
			IntStream.range(0, h).parallel().forEach(y -> {
				for (int i = y * l, fin = i + l; i < fin; i++) {
					double t = 0.5 * (dxx[i] + dyy[i]);
					if (t <= 0) {
						continue; // valeur propre dominante négative : crête claire ou bord, pas un creux
					}
					double d = 0.5 * (dxx[i] - dyy[i]);
					double lambda2 = t + Math.sqrt(d * d + (double) dxy[i] * dxy[i]);
					float r = (float) (s2 * lambda2);
					if (r > max[i]) {
						max[i] = r;
					}
					if (ech != null && m[i]) {
						ech[i] = r;
					}
				}
			});
			if (parEchelle != null) {
				parEchelle.accept(s, ech);
			}
		}
		for (int i = 0; i < n; i++) {
			if (!m[i]) {
				max[i] = 0f;
			}
		}
		return max;
	}

	/**
	 * Noyaux 1D échantillonnés g, g', g'' de rayon ⌈4σ⌉, corrigés pour être exacts sur les
	 * polynômes de degré 2 malgré l'échantillonnage : Σg = 1 ; g' donne la pente de x ;
	 * Σg'' = 0 et g'' donne 2 sur x².
	 */
	static float[][] noyaux(double s) {
		int r = Math.max(1, (int) Math.ceil(RAYON_NOYAU_SIGMAS * s));
		int t = 2 * r + 1;
		double[] g = new double[t];
		double somme = 0;
		for (int k = -r; k <= r; k++) {
			g[k + r] = Math.exp(-0.5 * k * k / (s * s));
			somme += g[k + r];
		}
		for (int i = 0; i < t; i++) {
			g[i] /= somme;
		}
		double[] g1 = new double[t];
		double[] g2 = new double[t];
		double m1 = 0;
		double s2 = 0;
		for (int k = -r; k <= r; k++) {
			g1[k + r] = -k / (s * s) * g[k + r];
			g2[k + r] = (k * k / (s * s * s * s) - 1 / (s * s)) * g[k + r];
			m1 += k * g1[k + r];
			s2 += g2[k + r];
		}
		double m2 = 0;
		for (int k = -r; k <= r; k++) {
			g1[k + r] /= -m1; // (x * g')(x) = -Σ k g'(k) = 1
			g2[k + r] -= s2 * g[k + r]; // Σ g'' = 0
			m2 += (double) k * k * g2[k + r];
		}
		float[][] sortie = new float[3][t];
		for (int i = 0; i < t; i++) {
			sortie[0][i] = (float) g[i];
			sortie[1][i] = (float) g1[i];
			sortie[2][i] = (float) (g2[i] * 2 / m2); // (x² * g'')(x) = Σ k² g''(k) = 2
		}
		return sortie;
	}

	/** Convolution horizontale (f*k)(x) = Σ f(x−j) k(j), bords répliqués. */
	static void convolutionX(float[] src, float[] dst, int l, int h, float[] k) {
		int r = k.length / 2;
		IntStream.range(0, h).parallel().forEach(y -> {
			int base = y * l;
			for (int x = 0; x < l; x++) {
				double acc = 0;
				for (int j = -r; j <= r; j++) {
					int xx = x - j;
					xx = xx < 0 ? 0 : (xx >= l ? l - 1 : xx);
					acc += src[base + xx] * k[j + r];
				}
				dst[base + x] = (float) acc;
			}
		});
	}

	/** Convolution verticale, bords répliqués ; parcourt les lignes pour rester en cache. */
	static void convolutionY(float[] src, float[] dst, int l, int h, float[] k) {
		int r = k.length / 2;
		IntStream.range(0, h).parallel().forEach(y -> {
			int base = y * l;
			double[] acc = new double[l];
			for (int j = -r; j <= r; j++) {
				int yy = y - j;
				yy = yy < 0 ? 0 : (yy >= h ? h - 1 : yy);
				int src0 = yy * l;
				double kj = k[j + r];
				for (int x = 0; x < l; x++) {
					acc[x] += src[src0 + x] * kj;
				}
			}
			for (int x = 0; x < l; x++) {
				dst[base + x] = (float) acc[x];
			}
		});
	}

	private static float[] valeursDansMasque(float[] v, boolean[] m) {
		int n = 0;
		for (boolean b : m) {
			if (b) {
				n++;
			}
		}
		float[] sortie = new float[n];
		int j = 0;
		for (int i = 0; i < v.length; i++) {
			if (m[i]) {
				sortie[j++] = v[i];
			}
		}
		return sortie;
	}

	/** @param trie valeurs triées, non vide ; @param p percentile dans [0,100] */
	static float percentile(float[] trie, double p) {
		int i = (int) Math.round(p / 100.0 * (trie.length - 1));
		return trie[Math.max(0, Math.min(trie.length - 1, i))];
	}

	/**
	 * Hystérésis : germes {@code v ≥ haut}, propagés en 8-connexité sur {@code v ≥ bas}.
	 * Une réponse nulle n'est jamais retenue, même si un seuil tombe à 0 (cas d'une image
	 * presque sans creux).
	 */
	static boolean[] hysteresis(float[] v, boolean[] m, int l, int h, float haut, float bas) {
		boolean[] sortie = new boolean[v.length];
		int[] pile = new int[v.length];
		int sommet = 0;
		for (int i = 0; i < v.length; i++) {
			if (m[i] && v[i] > 0 && v[i] >= haut) {
				sortie[i] = true;
				pile[sommet++] = i;
			}
		}
		while (sommet > 0) {
			int i = pile[--sommet];
			int x = i % l;
			int y = i / l;
			for (int dy = -1; dy <= 1; dy++) {
				int yy = y + dy;
				if (yy < 0 || yy >= h) {
					continue;
				}
				for (int dx = -1; dx <= 1; dx++) {
					int xx = x + dx;
					if (xx < 0 || xx >= l) {
						continue;
					}
					int j = yy * l + xx;
					if (!sortie[j] && m[j] && v[j] > 0 && v[j] >= bas) {
						sortie[j] = true;
						pile[sommet++] = j;
					}
				}
			}
		}
		return sortie;
	}

	/** Retire en place les composantes 8-connexes de moins de {@code aireMin} pixels. */
	static void retirerPetitesComposantes(boolean[] v, int l, int h, int aireMin) {
		if (aireMin <= 1) {
			return;
		}
		boolean[] vu = new boolean[v.length];
		int[] pile = new int[v.length];
		int[] composante = new int[v.length];
		for (int depart = 0; depart < v.length; depart++) {
			if (!v[depart] || vu[depart]) {
				continue;
			}
			int taille = 0;
			int sommet = 0;
			pile[sommet++] = depart;
			vu[depart] = true;
			while (sommet > 0) {
				int i = pile[--sommet];
				composante[taille++] = i;
				int x = i % l;
				int y = i / l;
				for (int dy = -1; dy <= 1; dy++) {
					int yy = y + dy;
					if (yy < 0 || yy >= h) {
						continue;
					}
					for (int dx = -1; dx <= 1; dx++) {
						int xx = x + dx;
						if (xx < 0 || xx >= l) {
							continue;
						}
						int j = yy * l + xx;
						if (v[j] && !vu[j]) {
							vu[j] = true;
							pile[sommet++] = j;
						}
					}
				}
			}
			if (taille < aireMin) {
				for (int k = 0; k < taille; k++) {
					v[composante[k]] = false;
				}
			}
		}
	}
}
