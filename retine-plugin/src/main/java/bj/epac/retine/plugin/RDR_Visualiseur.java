package bj.epac.retine.plugin;

import javax.swing.SwingUtilities;

import bj.epac.retine.plugin.visualiseur.VisualiseurChaine;
import ij.plugin.PlugIn;

/**
 * Commande de menu Plugins &gt; RDR &gt; Visualiseur : ouvre le visualiseur de la chaîne
 * (chaque étape affichée au moment où elle est calculée).
 */
public class RDR_Visualiseur implements PlugIn {

	@Override
	public void run(String arg) {
		SwingUtilities.invokeLater(() -> new VisualiseurChaine(false).setVisible(true));
	}
}
