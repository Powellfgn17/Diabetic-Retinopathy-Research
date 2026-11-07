package bj.epac.retine.bifurcation;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Un point de repère du réseau vasculaire : bifurcation (un vaisseau se sépare en deux) ou
 * croisement (deux vaisseaux se croisent). Immuable.
 *
 * <p>Coordonnées en pixels selon D1/D5 : le centre du pixel (i, j) est en (i + 0,5, j + 0,5).
 * Un point détecté sur le pixel (i, j) a donc x = i + 0,5 ; après fusion de plusieurs
 * pixels, x est la moyenne de leurs centres.</p>
 */
public final class Bifurcation {

	/** Nature du point. */
	public enum Type {
		BIFURCATION("bifurcation"), CROISEMENT("croisement");

		private final String nomCsv;

		Type(String nomCsv) {
			this.nomCsv = nomCsv;
		}

		/** @return le nom écrit dans la colonne {@code type} des CSV (D6) */
		public String nomCsv() {
			return nomCsv;
		}
	}

	private final double x;
	private final double y;
	private final Type type;

	public Bifurcation(double x, double y, Type type) {
		if (type == null) {
			throw new IllegalArgumentException("type null");
		}
		this.x = x;
		this.y = y;
		this.type = type;
	}

	/** @return abscisse (D1 : centre du pixel i en i + 0,5) */
	public double x() {
		return x;
	}

	/** @return ordonnée (D1 : centre du pixel j en j + 0,5) */
	public double y() {
		return y;
	}

	public Type type() {
		return type;
	}

	/** @return distance euclidienne à {@code autre}, en pixels */
	public double distance(Bifurcation autre) {
		return Math.hypot(x - autre.x, y - autre.y);
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT, "%s(%.1f, %.1f)", type.nomCsv, x, y);
	}

	/**
	 * Écrit {@code points} au format d'échange D6 : en-tête {@code x,y,type}, puis une ligne
	 * par point, nombres avec un point décimal (deux décimales). Crée les dossiers manquants.
	 */
	public static void ecrireCsv(Path fichier, List<Bifurcation> points) throws IOException {
		if (fichier.getParent() != null) {
			Files.createDirectories(fichier.getParent());
		}
		try (Writer w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
			w.write("x,y,type\n");
			for (Bifurcation b : points) {
				w.write(String.format(Locale.ROOT, "%.2f,%.2f,%s\n", b.x, b.y, b.type.nomCsv));
			}
		}
	}
}
