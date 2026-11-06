package bj.epac.retine.plugin.visualiseur;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PartenaireFireTest {

	@Rule
	public TemporaryFolder dossier = new TemporaryFolder();

	@Test
	public void trouveLaVisiteDansLesDeuxSens() throws IOException {
		Path un = dossier.newFile("A01_1.jpg").toPath();
		Path deux = dossier.newFile("A01_2.jpg").toPath();
		assertEquals(deux, VisualiseurChaine.partenaireFire(un));
		assertEquals(un, VisualiseurChaine.partenaireFire(deux));
	}

	@Test
	public void rienSansPartenaireOuHorsConventionFire() throws IOException {
		assertNull(VisualiseurChaine.partenaireFire(dossier.newFile("S07_1.jpg").toPath()));
		Path hrf = dossier.newFile("01_dr.jpg").toPath();
		Files.createFile(hrf.resolveSibling("01_h.jpg"));
		assertNull(VisualiseurChaine.partenaireFire(hrf));
	}
}
