package bj.epac.retine.parametres;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.google.gson.JsonParser;

public class ParametresTest {

	private static Parametres depuis(String json) {
		return new Parametres(JsonParser.parseString(json).getAsJsonObject());
	}

	@Test
	public void reelsDefautEtTrace() {
		Parametres p = Parametres.defauts();
		assertArrayEquals(new double[] { 2, 3, 4, 6, 8 }, p.reels("segmentation.sigmas"), 0);
		assertEquals("[2.0, 3.0, 4.0, 6.0, 8.0]", p.utilises().get("segmentation.sigmas").toString());
	}

	@Test
	public void reelsLusDansLeJson() {
		Parametres p = depuis("{\"segmentation\": {\"sigmas\": [1.5, 4]}}");
		assertArrayEquals(new double[] { 1.5, 4 }, p.reels("segmentation.sigmas"), 0);
	}

	@Test
	public void reelsRendUneCopie() {
		Parametres p = Parametres.defauts();
		p.reels("segmentation.sigmas")[0] = 99;
		assertEquals(2.0, p.reels("segmentation.sigmas")[0], 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void reelsRefuseUnScalaire() {
		depuis("{\"segmentation\": {\"sigmas\": 3}}").reels("segmentation.sigmas");
	}

	@Test(expected = IllegalArgumentException.class)
	public void reelsRefuseUneCleInconnue() {
		Parametres.defauts().reels("segmentation.inconnue");
	}
}
