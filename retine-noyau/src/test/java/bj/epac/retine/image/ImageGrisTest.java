package bj.epac.retine.image;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImageGrisTest {

	@Test
	public void valeurSuitLaConventionLigneParLigne() {
		// 3 colonnes, 2 lignes : indice = y * largeur + x
		ImageGris img = new ImageGris(3, 2, new float[] { 0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f });
		assertEquals(0.2f, img.valeur(2, 0), 0f);
		assertEquals(0.3f, img.valeur(0, 1), 0f);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void xHorsBornesEstRefuse() {
		new ImageGris(3, 2).valeur(3, 0);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void yHorsBornesEstRefuse() {
		new ImageGris(3, 2).valeur(0, 2);
	}
}
