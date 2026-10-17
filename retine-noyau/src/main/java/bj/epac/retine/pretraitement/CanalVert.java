package bj.epac.retine.pretraitement;

import bj.epac.retine.chaine.ContexteExecution;
import bj.epac.retine.chaine.Etape;
import bj.epac.retine.noyau.image.ImageCouleur;
import bj.epac.retine.noyau.image.ImageGris;

/**
 * Extrait le canal vert d'une image couleur.
 *
 * <p>Cette étape ne calcule rien : elle existe pour porter la justification
 * méthodologique du choix. Dans une photographie du fond d'œil, le canal vert offre
 * le meilleur contraste entre les vaisseaux (qui absorbent fortement le vert, donc
 * apparaissent sombres) et le fond rétinien ; le rouge est saturé et peu contrasté,
 * le bleu est très bruité et pauvre en signal (voir chapitre 8).</p>
 */
public final class CanalVert implements Etape<ImageCouleur, ImageGris> {

	@Override
	public ImageGris executer(ImageCouleur entree, ContexteExecution ctx) {
		return entree.canalVert();
	}
}
