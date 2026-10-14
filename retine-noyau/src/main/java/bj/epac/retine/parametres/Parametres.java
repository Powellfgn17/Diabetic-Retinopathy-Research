package bj.epac.retine.parametres;

import com.google.gson.JsonObject;

/**
 * Valeurs lues depuis le JSON de configuration, accès typé par clé
 * de la forme "section.cle" (ex. "filtreMedian.rayon").
 */
public final class Parametres {

	private final JsonObject racine;

	public Parametres(JsonObject racine) {
		this.racine = racine;
	}

	public double reel(String cle) {
		throw new UnsupportedOperationException("À implémenter (binôme B)");
	}

	public int entier(String cle) {
		throw new UnsupportedOperationException("À implémenter (binôme B)");
	}

	public String texte(String cle) {
		throw new UnsupportedOperationException("À implémenter (binôme B)");
	}
}
