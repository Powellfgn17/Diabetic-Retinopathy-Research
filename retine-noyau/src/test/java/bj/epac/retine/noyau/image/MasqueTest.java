package bj.epac.retine.noyau.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MasqueTest {

	@Test
	public void cercleCentreDedansCoinDehors() {
		Masque m = Masque.cercle(200, 200, 100, 100, 80);
		assertTrue(m.estDans(100, 100));
		assertFalse(m.estDans(0, 0));
		assertTrue(m.aMetadonneeCercle());
		assertEquals(80.0, m.rayon(), 0.0);
		double attendu = Math.PI * 80 * 80;
		assertEquals(attendu, m.nombrePixelsActifs(), attendu * 0.02);
	}

	@Test
	public void pleinEtBornes() {
		Masque m = Masque.plein(5, 4);
		assertEquals(20, m.nombrePixelsActifs());
		assertFalse(m.aMetadonneeCercle());
		try {
			m.estDans(5, 0);
			org.junit.Assert.fail();
		} catch (IndexOutOfBoundsException e) {
			assertTrue(e.getMessage().contains("5x4"));
		}
	}

	@Test
	public void constructeurCopie() {
		boolean[] src = new boolean[4];
		Masque m = new Masque(src, 2, 2);
		src[0] = true;
		assertFalse(m.estDans(0, 0));
	}
}
