package com.moe.myfamilybudget.server.internal.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.BankReader;
import com.moe.myfamilybudget.server.internal.port.BankWriter;

/**
 * Adaptateur de persistance pour {@link BankReader} (RF-B00) et {@link BankWriter} (DB-040).
 */
@Component
public class BankPersistenceAdapter implements BankReader, BankWriter {

    private final PersistenceManager persistenceManager;

    public BankPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public BankImportModel getBankImport() {
        return persistenceManager.getBankImport();
    }

    @Override
    public void updateBankImport(BankImportModel bankImport) {
        persistenceManager.updateBankImport(bankImport);
    }
}
