package bj.epac.retine.chaine;

import java.nio.file.Path;
import java.util.Random;

import bj.epac.retine.parametres.Parametres;

/**
 * Ce qu'une étape peut consulter pendant son exécution : paramètres,
 * graine aléatoire, chronomètre, dossier de sortie.
 */
public final class ContexteExecution {

	private final Parametres parametres;
	private final long graine;
	private final Random aleatoire;
	private final Path dossierSortie;
	private final long debutNanos;

	public ContexteExecution(Parametres parametres, long graine, Path dossierSortie) {
		this.parametres = parametres;
		this.graine = graine;
		this.aleatoire = new Random(graine);
		this.dossierSortie = dossierSortie;
		this.debutNanos = System.nanoTime();
	}

	public Parametres parametres() {
		return parametres;
	}

	public long graine() {
		return graine;
	}

	public Random aleatoire() {
		return aleatoire;
	}

	public Path dossierSortie() {
		return dossierSortie;
	}

	/** Millisecondes écoulées depuis la création du contexte. */
	public long millisecondesEcoulees() {
		return (System.nanoTime() - debutNanos) / 1_000_000;
	}
}
