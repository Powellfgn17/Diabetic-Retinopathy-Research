package bj.epac.retine.parametres;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Charge un fichier de configuration JSON (par défaut config/defaut.json) avec Gson.
 */
public final class LecteurParametres {

	/**
	 * @param fichier fichier JSON
	 * @return les paramètres : valeurs du fichier, complétées par les défauts
	 * @throws IOException si le fichier est absent, illisible ou n'est pas un objet JSON
	 */
	public Parametres lire(Path fichier) throws IOException {
		try (Reader r = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
			return new Parametres(JsonParser.parseReader(r).getAsJsonObject());
		} catch (JsonParseException | IllegalStateException e) {
			throw new IOException("JSON invalide : " + fichier, e);
		}
	}
}
