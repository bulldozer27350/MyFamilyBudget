package com.moe.myfamilybudget.server.internal.command;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Service de commande du domaine Retraite (RF-A00).
 * Encapsule les operations d'ecriture sur le modele de retraite.
 */
@Service
public class RetirementCommandService {

    private final PersistenceManager persistenceManager;

    public RetirementCommandService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public void updateRetirement(RetirementModel retirement) {
        persistenceManager.updateRetirement(retirement);
    }
}