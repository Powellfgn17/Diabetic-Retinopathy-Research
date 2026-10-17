package bj.epac.retine.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;
import bj.epac.retine.parametres.LecteurParametres;
import bj.epac.retine.parametres.Parametres;
import bj.epac.retine.pretraitement.ChainePretraitement;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;
import ij.plugin.MontageMaker;
import ij.plugin.PlugIn;

/**
 * Commande de menu Plugins > RDR > Pretraitement.
 * Le "_" dans le nom de classe est obligatoire pour que Fiji l'affiche.
 *
 * <p>Demande deux images (référence puis suivi), applique la chaîne de prétraitement à
 * chacune et affiche, pour chacune, l'originale (canal vert) et la prétraitée côte à côte.
 * Les paramètres viennent de {@code config/defaut.json} (dossier courant, ou propriété
 * {@code rdr.config}) ; à défaut, les valeurs par défaut du code. Les paramètres
 * effectivement utilisés sont écrits dans le journal et dans
 * {@code executions/<horodatage>/parametres_utilises.json}.</p>
 */
public class RDR_Pretraitement implements PlugIn {

	private static final long GRAINE = 42L;
	/** Échelle d'affichage du montage : les images FIRE font 2912 px de côté. */
	private static final double ECHELLE_MONTAGE = 0.3;

	@Override
	public void run(String arg) {
		String ref = choisir("Image de référence (visite 1)");
		if (ref == null) {
			return;
		}
		String suivi = choisir("Image de suivi (visite 2)");
		if (suivi == null) {
			return;
		}
		Parametres params = chargerParametres();
		Path sortie = Paths.get("executions", new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
		ContexteExecution ctx = new ContexteExecution(params, GRAINE, sortie);

		traiter("Référence", ref, ctx);
		traiter("Suivi", suivi, ctx);
		consignerParametres(params, sortie);
	}

	private static String choisir(String titre) {
		OpenDialog od = new OpenDialog(titre, null);
		return od.getPath();
	}

	private static Parametres chargerParametres() {
		String chemin = System.getProperty("rdr.config", "config/defaut.json");
		Path p = Paths.get(chemin);
		if (Files.exists(p)) {
			try {
				IJ.log("RDR : paramètres lus dans " + p.toAbsolutePath());
				return new LecteurParametres().lire(p);
			} catch (IOException e) {
				IJ.log("RDR : " + e.getMessage() + " — valeurs par défaut utilisées.");
			}
		} else {
			IJ.log("RDR : " + p.toAbsolutePath() + " absent — valeurs par défaut utilisées.");
		}
		return Parametres.defauts();
	}

	private static void traiter(String role, String chemin, ContexteExecution ctx) {
		ImagePlus imp = IJ.openImage(chemin);
		if (imp == null) {
			IJ.error("RDR", "Impossible d'ouvrir " + chemin);
			return;
		}
		String nom = Paths.get(chemin).getFileName().toString();
		try {
			ImageCouleur couleur = AdaptateurImageJ.versCouleur(imp);
			long debut = System.nanoTime();
			ChainePretraitement.Resultat r = new ChainePretraitement().executer(couleur, ctx);
			IJ.log(String.format("RDR : %s (%s) prétraitée en %.1f s ; champ de vision : centre (%.0f, %.0f), rayon %.0f px.",
					role, nom, (System.nanoTime() - debut) / 1e9, r.masque().cx(), r.masque().cy(), r.masque().rayon()));
			avantApres(role + " — " + nom, r.canalVert(), r.pretraitee().image());
		} catch (IllegalArgumentException | IllegalStateException e) {
			IJ.error("RDR", role + " (" + nom + ") : " + e.getMessage());
		}
	}

	private static void avantApres(String titre, ImageGris avant, ImageGris apres) {
		ImageStack pile = new ImageStack(avant.largeur(), avant.hauteur());
		pile.addSlice("Avant (canal vert)", AdaptateurImageJ.versImagePlus(avant, "a").getProcessor());
		pile.addSlice("Après (prétraitée)", AdaptateurImageJ.versImagePlus(apres, "b").getProcessor());
		ImagePlus montage = new MontageMaker().makeMontage2(new ImagePlus(titre, pile), 2, 1, ECHELLE_MONTAGE, 1, 2, 1, 0, true);
		montage.setTitle(titre);
		montage.show();
	}

	private static void consignerParametres(Parametres params, Path sortie) {
		StringBuilder json = new StringBuilder("{\n");
		int n = 0;
		for (Map.Entry<String, Object> e : params.utilises().entrySet()) {
			json.append("  \"").append(e.getKey()).append("\": ").append(e.getValue());
			json.append(++n < params.utilises().size() ? ",\n" : "\n");
		}
		json.append("}\n");
		IJ.log("RDR : paramètres utilisés\n" + json);
		try {
			Files.createDirectories(sortie);
			Files.write(sortie.resolve("parametres_utilises.json"), json.toString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			IJ.log("RDR : écriture des paramètres impossible (" + e.getMessage() + ").");
		}
	}
}
