package bj.epac.retine.jeux;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Parcourt le jeu FIRE et rend la liste des paires.
 *
 * Structure attendue sous la racine : Images/A01_1.jpg, Images/A01_2.jpg, ...
 * et Ground Truth/control_points_A01_1_2.txt. Une fois l'archive extraite,
 * la racine est actuellement Donnees/FIRE/FIRE/FIRE.
 */
public final class JeuFire {

	private final Path racine;

	public JeuFire(Path racine) {
		this.racine = racine;
	}

	public List<PaireImages> paires() throws IOException {
		throw new UnsupportedOperationException("À implémenter (binôme B)");
	}
}
