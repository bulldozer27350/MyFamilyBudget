package com.moe.myfamilybudget.domain.treasury.port;

/**
 * Levée lorsqu'un appel à updateTresorerieRow référence un {@code listKey} ou un {@code field}
 * non reconnu (SILO-216, lot B1). Déplacée depuis {@code transition.error} vers {@code treasury-api}
 * pour permettre à {@code treasury-core} de l'utiliser sans dépendance à {@code transition-snapshot}.
 */
public class UnknownTresorerieFieldException extends RuntimeException {

    public UnknownTresorerieFieldException(String listKey, String field) {
        super(field == null
                ? "Type de ligne de trésorerie inconnu : '" + listKey + "'"
                : "Champ inconnu '" + field + "' pour le type de ligne '" + listKey + "'");
    }
}
