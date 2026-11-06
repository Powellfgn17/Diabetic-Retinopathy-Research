package bj.epac.retine.plugin.visualiseur;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import javax.swing.JTextField;

import org.junit.Test;

import com.google.gson.JsonObject;

import bj.epac.retine.parametres.Parametres;

public class PanneauParametresTest {

	/** Le JSON produit par le formulaire relit exactement les valeurs chargées. */
	@Test
	public void allerRetourDesDefauts() {
		PanneauParametres p = new PanneauParametres();
		p.charger(Parametres.defauts());
		Parametres relu = new Parametres(p.versJson());
		Parametres d = Parametres.defauts();
		assertArrayEquals(d.reels("segmentation.sigmas"), relu.reels("segmentation.sigmas"), 0);
		assertEquals(d.reel("segmentation.percentileHaut"), relu.reel("segmentation.percentileHaut"), 0);
		assertEquals(d.entier("segmentation.aireMin"), relu.entier("segmentation.aireMin"));
		assertEquals(d.reel("masqueChampVision.seuil"), relu.reel("masqueChampVision.seuil"), 1e-12);
		assertEquals(d.entier("filtreMedian.rayon"), relu.entier("filtreMedian.rayon"));
	}

	/** Espaces, « ; » et « , » suivie d'un espace séparent ; « 1,5 » est un nombre décimal. */
	@Test
	public void listeDeSigmasEcriteALaFrancaise() {
		PanneauParametres p = new PanneauParametres();
		p.charger(Parametres.defauts());
		champSigmas(p).setText("1,5 ; 2, 3 4  12");
		JsonObject j = p.versJson();
		assertArrayEquals(new double[] { 1.5, 2, 3, 4, 12 }, new Parametres(j).reels("segmentation.sigmas"), 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void listeIllisibleRefusee() {
		PanneauParametres p = new PanneauParametres();
		p.charger(Parametres.defauts());
		champSigmas(p).setText("2,3,4");
		p.versJson();
	}

	private static JTextField champSigmas(PanneauParametres p) {
		for (java.awt.Component c : p.getComponents()) {
			if (c instanceof JTextField) {
				return (JTextField) c;
			}
		}
		throw new AssertionError("champ des σ introuvable");
	}
}
