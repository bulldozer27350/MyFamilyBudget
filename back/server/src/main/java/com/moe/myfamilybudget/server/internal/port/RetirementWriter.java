package com.moe.myfamilybudget.server.internal.port;

import com.moe.myfamilybudget.server.internal.model.RetirementModel;

/**
 * Port d'ecriture pour le domaine Retraite (DB-020). Seul le command service du domaine
 * ({@code RetirementCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager}
 * et prepare la bascule JPA (DB-1001) sans changer l'appelant.
 */
public interface RetirementWriter {

    /** Remplace les parametres et les personnes du domaine Retraite. */
    void updateRetirement(RetirementModel retirement);
}
