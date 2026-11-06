package bj.epac.retine.plugin.visualiseur;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import bj.epac.retine.parametres.Parametres;

/**
 * Formulaire de tous les paramètres de la chaîne, groupés par étape, avec une infobulle
 * qui rappelle l'effet de chaque paramètre (voir Docs/algorithmes.md). Produit le JSON
 * qui sert à relancer la chaîne, au même format que config/defaut.json.
 */
final class PanneauParametres extends JPanel {

	private static final long serialVersionUID = 1L;

	private enum Type {
		REEL, ENTIER, LISTE
	}

	private static final class Def {
		final String cle;
		final String libelle;
		final Type type;
		final double min;
		final double max;
		final double pas;
		final String aide;

		Def(String cle, String libelle, Type type, double min, double max, double pas, String aide) {
			this.cle = cle;
			this.libelle = libelle;
			this.type = type;
			this.min = min;
			this.max = max;
			this.pas = pas;
			this.aide = aide;
		}
	}

	/** null = titre de groupe (le libellé est dans {@code cle}). */
	private static final Object[] DEFS = {
			"Masque du champ de vue",
			new Def("masqueChampVision.seuil", "Seuil du rouge", Type.REEL, 0, 1, 0.01,
					"Plus haut : masque plus petit. Plus bas : le halo gris entre dans le masque."),
			new Def("masqueChampVision.erosion", "Érosion (px)", Type.ENTIER, 0, 200, 1,
					"Rogne la bande de transition au bord. Plus haut : on perd de la rétine en périphérie."),
			"Correction de l'éclairage",
			new Def("correctionEclairage.sigmaRelatif", "σ / largeur", Type.REEL, 0.005, 0.5, 0.005,
					"Taille du flou qui estime l'éclairage (0,05 ≈ 146 px sur FIRE). Trop petit : disque et gros vaisseaux pâlissent."),
			"Rehaussement du contraste",
			new Def("rehaussementContraste.percentileBas", "Percentile noir (%)", Type.REEL, 0, 49, 0.5,
					"Ce qui devient noir. Plus haut : contraste plus fort, vaisseaux sombres écrasés."),
			new Def("rehaussementContraste.percentileHaut", "Percentile blanc (%)", Type.REEL, 51, 100, 0.5,
					"Ce qui devient blanc. Plus bas : contraste plus fort, exsudats et disque écrasés."),
			"Filtre médian",
			new Def("filtreMedian.rayon", "Rayon (px)", Type.ENTIER, 0, 10, 1,
					"Fenêtre de (2r+1)². Plus haut : moins de bruit, mais petits vaisseaux et microanévrismes effacés."),
			"Segmentation (Hessien)",
			new Def("segmentation.sigmas", "Échelles σ (px)", Type.LISTE, 0, 0, 0,
					"Largeurs cherchées (σ ≈ 0,6 × largeur du vaisseau), séparées par des espaces ou « ; ». Ajouter 1,5 : vaisseaux fins ; ajouter 12 : gros troncs creux."),
			new Def("segmentation.facteurEchelle", "Facteur d'échelle", Type.REEL, 0.05, 10, 0.05,
					"Multiplie les σ (et l'aire min. par son carré). Autre jeu : ppd du jeu / ppd de FIRE."),
			new Def("segmentation.percentileEcretage", "Écrêtage (%)", Type.REEL, 0, 100, 1,
					"Plafond qui aplatit les taches claires. 100 = désactivé : anneaux autour des exsudats."),
			new Def("segmentation.percentileHaut", "Seuil haut (%)", Type.REEL, 0, 100, 0.5,
					"Germes de l'hystérésis. Plus haut : vaisseaux plus fins, réseau coupé, moins de bifurcations."),
			new Def("segmentation.percentileBas", "Seuil bas (%)", Type.REEL, 0, 100, 0.5,
					"Extension depuis les germes. Plus bas : réseau plus continu, mais plus de barbes."),
			new Def("segmentation.aireMin", "Aire minimale (px)", Type.ENTIER, 0, 100000, 10,
					"Retire les morceaux isolés plus petits. Plus haut : masque plus propre, bouts fins perdus."), };

	private final Map<String, JComponent> champs = new LinkedHashMap<String, JComponent>();
	private final Map<String, Def> defs = new LinkedHashMap<String, Def>();

	PanneauParametres() {
		super(new GridBagLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 2);
		c.anchor = GridBagConstraints.WEST;
		int ligne = 0;
		for (Object o : DEFS) {
			c.gridy = ligne++;
			if (o instanceof String) {
				JLabel t = new JLabel((String) o);
				t.setFont(t.getFont().deriveFont(Font.BOLD));
				c.gridx = 0;
				c.gridwidth = 2;
				c.insets = new Insets(ligne == 1 ? 2 : 12, 2, 2, 2);
				add(t, c);
				c.gridwidth = 1;
				c.insets = new Insets(2, 2, 2, 2);
				continue;
			}
			Def d = (Def) o;
			JLabel l = new JLabel(d.libelle);
			l.setToolTipText(d.aide);
			c.gridx = 0;
			c.fill = GridBagConstraints.NONE;
			add(l, c);
			JComponent champ;
			if (d.type == Type.LISTE) {
				champ = new JTextField(10);
			} else if (d.type == Type.ENTIER) {
				champ = new JSpinner(new SpinnerNumberModel(0, (int) d.min, (int) d.max, (int) d.pas));
			} else {
				champ = new JSpinner(new SpinnerNumberModel(d.min, d.min, d.max, d.pas));
				((JSpinner) champ).setEditor(new JSpinner.NumberEditor((JSpinner) champ, "0.###"));
			}
			champ.setToolTipText(d.aide);
			c.gridx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.weightx = 1;
			add(champ, c);
			c.weightx = 0;
			champs.put(d.cle, champ);
			defs.put(d.cle, d);
		}
		c.gridy = ligne;
		c.weighty = 1;
		add(new JLabel(" "), c);
	}

	/** Remplit le formulaire avec les valeurs de {@code p} (fichier JSON complété par les défauts). */
	void charger(Parametres p) {
		for (Map.Entry<String, JComponent> e : champs.entrySet()) {
			Def d = defs.get(e.getKey());
			switch (d.type) {
			case LISTE: {
				StringBuilder sb = new StringBuilder();
				for (double v : p.reels(d.cle)) {
					sb.append(sb.length() == 0 ? "" : ", ").append(texte(v));
				}
				((JTextField) e.getValue()).setText(sb.toString());
				break;
			}
			case ENTIER:
				((JSpinner) e.getValue()).setValue(p.entier(d.cle));
				break;
			default:
				((JSpinner) e.getValue()).setValue(Math.max(d.min, Math.min(d.max, p.reel(d.cle))));
			}
		}
	}

	/**
	 * @return les valeurs du formulaire, au format de config/defaut.json
	 * @throws IllegalArgumentException si une liste n'est pas lisible
	 */
	JsonObject versJson() {
		JsonObject racine = new JsonObject();
		for (Map.Entry<String, JComponent> e : champs.entrySet()) {
			Def d = defs.get(e.getKey());
			String section = d.cle.substring(0, d.cle.indexOf('.'));
			String cle = d.cle.substring(d.cle.indexOf('.') + 1);
			if (!racine.has(section)) {
				racine.add(section, new JsonObject());
			}
			JsonObject s = racine.getAsJsonObject(section);
			switch (d.type) {
			case LISTE: {
				JsonArray a = new JsonArray();
				String t = ((JTextField) e.getValue()).getText().trim();
				// séparateurs : espace, « ; » ou virgule suivie d'un espace ; « 1,5 » se lit 1.5
				for (String morceau : t.split("[;\\s]+|,\\s+")) {
					if (!morceau.isEmpty()) {
						try {
							a.add(Double.parseDouble(morceau.replace(',', '.')));
						} catch (NumberFormatException ex) {
							throw new IllegalArgumentException(d.libelle + " : « " + morceau + " » n'est pas un nombre");
						}
					}
				}
				s.add(cle, a);
				break;
			}
			case ENTIER:
				s.addProperty(cle, ((Number) ((JSpinner) e.getValue()).getValue()).intValue());
				break;
			default:
				s.addProperty(cle, ((Number) ((JSpinner) e.getValue()).getValue()).doubleValue());
			}
		}
		return racine;
	}

	private static String texte(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%s", v);
	}
}
