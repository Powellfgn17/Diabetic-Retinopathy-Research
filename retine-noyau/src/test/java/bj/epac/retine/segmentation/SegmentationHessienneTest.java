package bj.epac.retine.segmentation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.Random;

import org.junit.Test;

import com.google.gson.JsonParser;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.noyau.image.ImageMasquee;
import bj.epac.retine.noyau.image.Masque;
import bj.epac.retine.parametres.Parametres;

public class SegmentationHessienneTest {

	private static final int L = 200;
	private static final int H = 200;

	private static ContexteExecution ctx() {
		return new ContexteExecution(Parametres.defauts(), 1L, Paths.get("."));
	}

	/**
	 * Seuils adaptés à une seule ligne (environ 2 % de l'image) : les défauts supposent des
	 * vaisseaux couvrant ~10 % du champ, comme sur une vraie rétine.
	 */
	private static ContexteExecution ctxUneLigne() {
		return ctx("{\"percentileHaut\": 99, \"percentileBas\": 97}");
	}

	private static ContexteExecution ctx(String segmentationJson) {
		Parametres p = new Parametres(
				JsonParser.parseString("{\"segmentation\": " + segmentationJson + "}").getAsJsonObject());
		return new ContexteExecution(p, 1L, Paths.get("."));
	}

	/** Fond 0.6, bruit gaussien d'écart-type {@code bruit} (graine fixe). */
	private static float[] fond(double bruit) {
		Random r = new Random(7);
		float[] f = new float[L * H];
		for (int i = 0; i < f.length; i++) {
			f[i] = (float) (0.6 + bruit * r.nextGaussian());
		}
		return f;
	}

	/**
	 * Ajoute un vaisseau à profil gaussien (écart-type 1.5 px, profondeur {@code profondeur},
	 * négative pour une ligne claire) le long de la droite passant par (x0, y0) d'angle
	 * {@code angle} (radians), sur la longueur visible.
	 */
	private static void ligne(float[] f, double x0, double y0, double angle, double profondeur) {
		double nx = -Math.sin(angle);
		double ny = Math.cos(angle);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				double d = (x + 0.5 - x0) * nx + (y + 0.5 - y0) * ny;
				f[y * L + x] -= (float) (profondeur * Math.exp(-d * d / (2 * 1.5 * 1.5)));
			}
		}
	}

	private static ImageMasquee plein(float[] f) {
		return new ImageMasquee(ImageGris.enveloppe(f, L, H), Masque.plein(L, H));
	}

	@Test
	public void ligneSombreHorizontaleDetecteeEtFondRejete() {
		float[] f = fond(0.005);
		ligne(f, 0, 100.5, 0, 0.3);
		Masque v = new SegmentationHessienne().executer(plein(f), ctxUneLigne());
		int surLigne = 0;
		for (int x = 15; x < L - 15; x++) {
			if (v.estDans(x, 100)) {
				surLigne++;
			}
		}
		assertTrue("ligne détectée sur " + surLigne + " px", surLigne >= 0.95 * (L - 30));
		int loin = 0;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				if (Math.abs(y + 0.5 - 100.5) > 8 && v.estDans(x, y)) {
					loin++;
				}
			}
		}
		assertEquals("pixels détectés loin de la ligne", 0, loin);
	}

	@Test
	public void ligneSombreObliqueDetectee() {
		float[] f = fond(0.005);
		ligne(f, 100, 100, Math.toRadians(37), 0.3);
		Masque v = new SegmentationHessienne().executer(plein(f), ctxUneLigne());
		int surLigne = 0;
		int total = 0;
		for (double t = -70; t <= 70; t += 1) {
			int x = (int) Math.floor(100 + t * Math.cos(Math.toRadians(37)));
			int y = (int) Math.floor(100 + t * Math.sin(Math.toRadians(37)));
			total++;
			if (v.estDans(x, y)) {
				surLigne++;
			}
		}
		assertTrue("ligne oblique détectée sur " + surLigne + "/" + total, surLigne >= 0.95 * total);
	}

	@Test
	public void ligneClaireNeDonneAucuneReponse() {
		float[] f = fond(0);
		ligne(f, 0, 100.5, 0, -0.3);
		ImageGris r = new SegmentationHessienne().segmenter(plein(f), ctx()).reponse();
		for (int x = 20; x < L - 20; x++) {
			assertEquals("réponse au centre d'une ligne claire, x = " + x, 0.0, r.get(x, 100), 1e-6);
		}
	}

	@Test
	public void bordDuChampDeVueNEstPasPrisPourUnVaisseau() {
		// Comme en sortie de prétraitement : disque clair qui s'éclaircit linéairement sur les
		// 60 derniers pixels (le halo mesuré sur FIRE A01), 0 dehors, masque érodé de 7 px ;
		// une ligne sombre à l'intérieur sert de référence. La cassure du dégradé (à 60 px du
		// bord) est une vraie courbure positive : elle reste hors de la bande mesurée.
		double cx = 100, cy = 100, rayon = 90;
		float[] f = new float[L * H];
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
				if (d <= rayon) {
					f[y * L + x] = (float) (0.5 + 0.3 * Math.max(0, (d - (rayon - 60)) / 60));
				}
			}
		}
		ligne(f, 0, 100.5, 0, 0.2);
		for (int i = 0; i < f.length; i++) {
			if (Math.hypot(i % L + 0.5 - cx, i / L + 0.5 - cy) > rayon) {
				f[i] = 0;
			}
		}
		Masque m = Masque.cercle(L, H, cx, cy, rayon - 7);
		ImageGris r = new SegmentationHessienne().segmenter(new ImageMasquee(ImageGris.enveloppe(f, L, H), m), ctx())
				.reponse();
		double ref = r.get(100, 100);
		assertTrue(ref > 0);
		double maxBord = 0;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
				boolean loinDeLaLigne = Math.abs(y + 0.5 - 100.5) > 10;
				if (m.estDans(x, y) && d > rayon - 7 - 15 && loinDeLaLigne) {
					maxBord = Math.max(maxBord, r.get(x, y));
				}
			}
		}
		assertTrue("réponse au bord " + maxBord + " contre " + ref + " sur la ligne", maxBord < 0.05 * ref);
	}

	/** Réponse maximale sur l'anneau 8–20 px autour d'une tache claire (exsudat) centrée en (60, 60). */
	private static double reponseAutourDUneTacheClaire(ContexteExecution c) {
		float[] f = fond(0);
		ligne(f, 0, 150.5, 0, 0.3); // la ligne fixe la médiane et sert de référence
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				double d2 = Math.pow(x + 0.5 - 60, 2) + Math.pow(y + 0.5 - 60, 2);
				f[y * L + x] += (float) (0.35 * Math.exp(-d2 / (2 * 4.0 * 4.0)));
			}
		}
		ImageGris r = new SegmentationHessienne().segmenter(plein(f), c).reponse();
		double max = 0;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				double d = Math.hypot(x + 0.5 - 60, y + 0.5 - 60);
				if (d >= 8 && d <= 20) {
					max = Math.max(max, r.get(x, y));
				}
			}
		}
		return max / r.get(100, 150);
	}

	@Test
	public void ecretageSupprimeLAnneauAutourDUneTacheClaire() {
		double sans = reponseAutourDUneTacheClaire(ctx("{\"percentileEcretage\": 100}"));
		double avec = reponseAutourDUneTacheClaire(ctx());
		assertTrue("sans écrêtage, l'anneau répond (" + sans + " de la ligne)", sans > 0.2);
		assertTrue("avec écrêtage, l'anneau ne répond plus (" + avec + " de la ligne)", avec < 0.02);
	}

	@Test
	public void reponseEtVaisseauxNulsHorsMasque() {
		float[] f = fond(0.005);
		ligne(f, 0, 100.5, 0, 0.3);
		Masque m = Masque.cercle(L, H, 100, 100, 60);
		SegmentationHessienne.Resultat res = new SegmentationHessienne()
				.segmenter(new ImageMasquee(ImageGris.enveloppe(f, L, H), m), ctx());
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				if (!m.estDans(x, y)) {
					assertEquals(0.0, res.reponse().get(x, y), 0.0);
					assertFalse(res.vaisseaux().estDans(x, y));
				}
			}
		}
		assertTrue(res.vaisseaux().estDans(100, 100));
	}

	@Test
	public void petitPointSombreIsoleRetire() {
		float[] f = fond(0.005);
		ligne(f, 0, 100.5, 0, 0.3);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < L; x++) {
				double d2 = Math.pow(x + 0.5 - 50, 2) + Math.pow(y + 0.5 - 40, 2);
				f[y * L + x] -= (float) (0.3 * Math.exp(-d2 / (2 * 1.5 * 1.5)));
			}
		}
		// Seuils assez bas pour que le point (réponse ≈ 57 % de celle de la ligne) passe
		// l'hystérésis : c'est le filtre d'aire seul qui doit le retirer.
		String seuils = "\"percentileHaut\": 98, \"percentileBas\": 96";
		ContexteExecution sansFiltre = ctx("{" + seuils + ", \"aireMin\": 0}");
		assertTrue("le point passe l'hystérésis sans filtre d'aire",
				new SegmentationHessienne().executer(plein(f.clone()), sansFiltre).estDans(50, 40));
		Masque v = new SegmentationHessienne().executer(plein(f), ctx("{" + seuils + "}"));
		assertFalse("point isolé retiré", v.estDans(50, 40));
		assertTrue("ligne conservée", v.estDans(100, 100));
	}

	@Test
	public void observateurVoitToutesLesEtapesSansChangerLeResultat() {
		float[] f = fond(0.005);
		ligne(f, 0, 100.5, 0, 0.3);
		java.util.List<String> noms = new java.util.ArrayList<String>();
		java.util.List<Masque> masques = new java.util.ArrayList<Masque>();
		SegmentationHessienne.Resultat avec = new SegmentationHessienne().segmenter(plein(f.clone()), ctxUneLigne(),
				(nom, image, masque) -> {
					noms.add(nom);
					masques.add(masque);
				});
		SegmentationHessienne.Resultat sans = new SegmentationHessienne().segmenter(plein(f.clone()), ctxUneLigne());
		java.util.List<String> attendus = new java.util.ArrayList<String>();
		attendus.add(SegmentationHessienne.ECRETAGE);
		for (double s : new double[] { 2, 3, 4, 6, 8 }) {
			attendus.add(SegmentationHessienne.nomReponseEchelle(s));
		}
		attendus.add(SegmentationHessienne.REPONSE);
		attendus.add(SegmentationHessienne.GERMES);
		attendus.add(SegmentationHessienne.HYSTERESIS);
		attendus.add(SegmentationHessienne.VAISSEAUX);
		assertEquals(attendus, noms);
		assertArrayEquals(sans.reponse().donneesBrutes(), avec.reponse().donneesBrutes(), 0f);
		assertArrayEquals(sans.vaisseaux().donneesBrutes(), avec.vaisseaux().donneesBrutes());
		// germes ⊂ hystérésis ⊃ résultat final
		boolean[] germes = masques.get(noms.indexOf(SegmentationHessienne.GERMES)).donneesBrutes();
		boolean[] hyst = masques.get(noms.indexOf(SegmentationHessienne.HYSTERESIS)).donneesBrutes();
		boolean[] fin = avec.vaisseaux().donneesBrutes();
		for (int i = 0; i < fin.length; i++) {
			assertTrue(!germes[i] || hyst[i]);
			assertTrue(!fin[i] || hyst[i]);
		}
	}

	@Test
	public void entreeInchangee() {
		float[] f = fond(0.005);
		ligne(f, 0, 100.5, 0, 0.3);
		float[] avant = f.clone();
		new SegmentationHessienne().executer(plein(f), ctx());
		assertArrayEquals(avant, f, 0f);
	}

	@Test
	public void noyauxExactsSurLesPolynomesDeDegreDeux() {
		for (double s : new double[] { 1, 2, 3.5, 8 }) {
			float[][] k = SegmentationHessienne.noyaux(s);
			int r = k[0].length / 2;
			double g = 0, pente = 0, s2 = 0, courbure = 0;
			for (int j = -r; j <= r; j++) {
				g += k[0][j + r];
				pente -= j * k[1][j + r];
				s2 += k[2][j + r];
				courbure += (double) j * j * k[2][j + r];
			}
			assertEquals(1.0, g, 1e-5);
			assertEquals(1.0, pente, 1e-5);
			assertEquals(0.0, s2, 1e-5);
			assertEquals(2.0, courbure, 1e-4);
		}
	}

	@Test
	public void hysteresisGardeLesFaiblesReliesAUnGerme() {
		// une ligne de 7 pixels : faible, faible, fort, faible | trou | faible, faible
		int l = 7, h = 1;
		float[] v = { 0.5f, 0.5f, 0.9f, 0.5f, 0.1f, 0.5f, 0.5f };
		boolean[] m = new boolean[l];
		java.util.Arrays.fill(m, true);
		boolean[] s = SegmentationHessienne.hysteresis(v, m, l, h, 0.8f, 0.4f);
		assertArrayEquals(new boolean[] { true, true, true, true, false, false, false }, s);
	}

	@Test
	public void hysteresisNeRetientJamaisUneReponseNulle() {
		float[] v = new float[9];
		boolean[] m = new boolean[9];
		java.util.Arrays.fill(m, true);
		boolean[] s = SegmentationHessienne.hysteresis(v, m, 3, 3, 0f, 0f);
		assertArrayEquals(new boolean[9], s);
	}

	@Test(expected = IllegalArgumentException.class)
	public void sigmaNegatifRefuse() {
		new SegmentationHessienne().executer(plein(fond(0)), ctx("{\"sigmas\": [2, -1]}"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void ecretageHorsBornesRefuse() {
		new SegmentationHessienne().executer(plein(fond(0)), ctx("{\"percentileEcretage\": 120}"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void percentilesInversesRefuses() {
		new SegmentationHessienne().executer(plein(fond(0)),
				ctx("{\"percentileHaut\": 80, \"percentileBas\": 90}"));
	}
}
