package bj.epac.retine.plugin;

import ij.IJ;
import ij.plugin.PlugIn;

/**
 * Commande de menu Plugins > RDR > Pretraitement.
 * Le "_" dans le nom de classe est obligatoire pour que Fiji l'affiche.
 *
 * Étape 1 : un simple message pour valider le build et l'installation.
 */
public class RDR_Pretraitement implements PlugIn {

	@Override
	public void run(String arg) {
		IJ.showMessage("RDR", "Plugin RDR installé — prétraitement à venir.");
	}
}
