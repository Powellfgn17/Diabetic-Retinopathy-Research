package bj.epac.retine.parametres;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Valeurs lues depuis le JSON de configuration, accès typé par clé
 * de la forme "section.cle" (ex. "filtreMedian.rayon").
 *
 * <p>Les valeurs par défaut sont codées ici ; le JSON les surcharge clé par clé.
 * Chaque clé effectivement lue est mémorisée avec sa valeur ({@link #utilises()}),
 * pour être consignée dans la sortie du run.</p>
 */
public final class Parametres {

	private static final Map<String, Object> DEFAUTS = new LinkedHashMap<String, Object>();

	static {
		// Prétraitement (Point 1)
		DEFAUTS.put("masqueChampVision.seuil", 0.08);
		DEFAUTS.put("masqueChampVision.erosion", 7);
		DEFAUTS.put("correctionEclairage.sigmaRelatif", 0.05);
		DEFAUTS.put("rehaussementContraste.percentileBas", 1.0);
		DEFAUTS.put("rehaussementContraste.percentileHaut", 99.0);
		DEFAUTS.put("filtreMedian.rayon", 2);
	}

	private final JsonObject racine;
	private final Map<String, Object> utilises = new TreeMap<String, Object>();

	public Parametres(JsonObject racine) {
		this.racine = racine;
	}

	/** @return des paramètres réduits aux valeurs par défaut */
	public static Parametres defauts() {
		return new Parametres(new JsonObject());
	}

	/**
	 * @param cle clé « section.cle »
	 * @return la valeur réelle (JSON, sinon défaut)
	 * @throws IllegalArgumentException si la clé n'existe ni dans le JSON ni dans les défauts
	 */
	public double reel(String cle) {
		JsonElement e = dansJson(cle);
		double v = e != null ? e.getAsDouble() : defaut(cle).doubleValue();
		utilises.put(cle, v);
		return v;
	}

	/**
	 * @param cle clé « section.cle »
	 * @return la valeur entière (JSON, sinon défaut)
	 * @throws IllegalArgumentException si la clé n'existe ni dans le JSON ni dans les défauts
	 */
	public int entier(String cle) {
		JsonElement e = dansJson(cle);
		int v = e != null ? e.getAsInt() : defaut(cle).intValue();
		utilises.put(cle, v);
		return v;
	}

	/**
	 * @param cle clé « section.cle »
	 * @return la valeur texte du JSON
	 * @throws IllegalArgumentException si la clé est absente du JSON (aucun défaut texte)
	 */
	public String texte(String cle) {
		JsonElement e = dansJson(cle);
		if (e == null) {
			throw new IllegalArgumentException("Paramètre texte absent : " + cle);
		}
		String v = e.getAsString();
		utilises.put(cle, v);
		return v;
	}

	/** @return les clés lues jusqu'ici avec leur valeur, triées par clé (vue non modifiable) */
	public Map<String, Object> utilises() {
		return Collections.unmodifiableMap(utilises);
	}

	private JsonElement dansJson(String cle) {
		int point = cle.indexOf('.');
		if (point < 0) {
			throw new IllegalArgumentException("Clé « section.cle » attendue : " + cle);
		}
		JsonElement section = racine.get(cle.substring(0, point));
		if (section == null || !section.isJsonObject()) {
			return null;
		}
		JsonElement e = section.getAsJsonObject().get(cle.substring(point + 1));
		return e == null || e.isJsonNull() ? null : e;
	}

	private static Number defaut(String cle) {
		Object d = DEFAUTS.get(cle);
		if (!(d instanceof Number)) {
			throw new IllegalArgumentException("Paramètre inconnu : " + cle);
		}
		return (Number) d;
	}
}
