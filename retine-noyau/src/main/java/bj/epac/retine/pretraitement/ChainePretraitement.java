package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Observateur;
import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.ImageMasquee;
import bj.epac.retine.noyau.image.Masque;

/**
 * Assemble la chaîne de prétraitement :
 * {@code CanalVert → MasqueChampVision → CorrectionEclairage → RehaussementContraste →
 * FiltreMedian}. {@link MasqueChampVision} consomme l'image couleur d'origine, pas la
 * sortie de {@link CanalVert}.
 */
public final class ChainePretraitement {

	/** Résultat de la chaîne : l'original (canal vert), le masque et l'image prétraitée. */
	public static final class Resultat {
		private final ImageGris canalVert;
		private final Masque masque;
		private final ImageMasquee pretraitee;

		Resultat(ImageGris canalVert, Masque masque, ImageMasquee pretraitee) {
			this.canalVert = canalVert;
			this.masque = masque;
			this.pretraitee = pretraitee;
		}

		/** @return le canal vert d'origine, avant tout traitement */
		public ImageGris canalVert() {
			return canalVert;
		}

		/** @return le masque du champ de vision */
		public Masque masque() {
			return masque;
		}

		/** @return l'image prétraitée, dans [0,1], avec son masque */
		public ImageMasquee pretraitee() {
			return pretraitee;
		}
	}

	/**
	 * Exécute la chaîne complète.
	 *
	 * @param couleur image d'origine
	 * @param ctx     contexte d'exécution (paramètres)
	 * @return le résultat de la chaîne
	 */
	public Resultat executer(ImageCouleur couleur, ContexteExecution ctx) {
		return executer(couleur, ctx, Observateur.AUCUN);
	}

	/** Noms des étapes transmis à l'observateur, dans l'ordre. */
	public static final String CANAL_VERT = "Canal vert";
	public static final String MASQUE = "Masque du champ de vue";
	public static final String ECLAIRAGE = "Correction de l'éclairage";
	public static final String CONTRASTE = "Rehaussement du contraste";
	public static final String MEDIAN = "Filtre médian";

	/**
	 * Exécute la chaîne complète en prévenant {@code obs} après chaque étape.
	 *
	 * @param couleur image d'origine
	 * @param ctx     contexte d'exécution (paramètres)
	 * @param obs     observateur des résultats intermédiaires
	 * @return le résultat de la chaîne
	 */
	public Resultat executer(ImageCouleur couleur, ContexteExecution ctx, Observateur obs) {
		ImageGris vert = new CanalVert().executer(couleur, ctx);
		obs.etape(CANAL_VERT, vert, null);
		Masque masque = new MasqueChampVision().executer(couleur, ctx);
		obs.etape(MASQUE, vert, masque);
		ImageMasquee x = new ImageMasquee(vert, masque);
		x = new CorrectionEclairage().executer(x, ctx);
		obs.etape(ECLAIRAGE, x.image(), masque);
		x = new RehaussementContraste().executer(x, ctx);
		obs.etape(CONTRASTE, x.image(), masque);
		x = new FiltreMedian().executer(x, ctx);
		obs.etape(MEDIAN, x.image(), masque);
		return new Resultat(vert, masque, x);
	}
}
