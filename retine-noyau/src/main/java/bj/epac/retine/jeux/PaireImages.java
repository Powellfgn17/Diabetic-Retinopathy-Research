package bj.epac.retine.jeux;

import java.nio.file.Path;

/**
 * Une paire d'images d'un même œil : référence et suivi.
 */
public final class PaireImages {

	private final String identifiant;
	private final Path reference;
	private final Path suivi;

	public PaireImages(String identifiant, Path reference, Path suivi) {
		this.identifiant = identifiant;
		this.reference = reference;
		this.suivi = suivi;
	}

	public String identifiant() {
		return identifiant;
	}

	public Path reference() {
		return reference;
	}

	public Path suivi() {
		return suivi;
	}
}
