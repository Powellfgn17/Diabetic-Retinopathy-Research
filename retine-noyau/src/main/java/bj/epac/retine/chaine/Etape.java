package bj.epac.retine.chaine;

/**
 * Une étape de la chaîne de traitement : transforme une entrée E en sortie S.
 */
public interface Etape<E, S> {

	S executer(E entree, ContexteExecution ctx);
}
