package com.moe.myfamilybudget.transition.error;

/**
 * @deprecated Utiliser {@link com.moe.myfamilybudget.domain.treasury.port.UnknownTresorerieFieldException}
 *             (SILO-216, lot B1 — déplacée dans treasury-api).
 */
@Deprecated(since = "SILO-216")
public class UnknownTresorerieFieldException
        extends com.moe.myfamilybudget.domain.treasury.port.UnknownTresorerieFieldException {

    public UnknownTresorerieFieldException(String listKey, String field) {
        super(listKey, field);
    }
}
