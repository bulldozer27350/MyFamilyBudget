package com.moe.myfamilybudget.server.internal.command;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Service de commande du domaine Import Bancaire / Rapprochement (RF-A00).
 * Encapsule les operations d'ecriture sur les transactions bancaires et rapprochements.
 */
@Service
public class BankImportCommandService {

    private final PersistenceManager persistenceManager;

    public BankImportCommandService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public void updateBankImport(BankImportModel bankImport) {
        persistenceManager.updateBankImport(bankImport);
    }
}