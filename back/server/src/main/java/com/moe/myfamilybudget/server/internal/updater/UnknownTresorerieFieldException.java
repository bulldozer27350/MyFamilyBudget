package com.moe.myfamilybudget.server.internal.updater;

/**
 * Levée lorsqu'un appel à updateTresorerieRow référence un {@code listKey} ou un {@code field}
 * non reconnu. Remplace l'ancien comportement de PersistenceManager qui, dans ce cas, renvoyait
 * silencieusement l'état inchangé (aucune exception, aucun log, réponse HTTP 200 côté client
 * alors que rien n'avait été modifié).
 *
 * Tant qu'aucun @RestControllerAdvice global n'est en place (cf. point 2 de l'audit), cette
 * exception remonte telle quelle et se traduit par une réponse 500 générique côté client — ce qui
 * est déjà une amélioration par rapport au faux-succès silencieux actuel. Une fois le point 2
 * implémenté, elle pourra y être mappée vers un 400 Bad Request explicite.
 */
public class UnknownTresorerieFieldException extends RuntimeException {

    public UnknownTresorerieFieldException(String listKey, String field) {
        super(field == null
                ? "Type de ligne de trésorerie inconnu : '" + listKey + "'"
                : "Champ inconnu '" + field + "' pour le type de ligne '" + listKey + "'");
    }
}
