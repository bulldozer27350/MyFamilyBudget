package com.moe.myfamilybudget.server.internal.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.RetirementReader;
import com.moe.myfamilybudget.server.internal.port.RetirementWriter;

/**
 * Adaptateur de persistance pour {@link RetirementReader} (RF-B00) et {@link RetirementWriter} (DB-020).
 */
@Component
public class RetirementPersistenceAdapter implements RetirementReader, RetirementWriter {

    private final PersistenceManager persistenceManager;

    public RetirementPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public RetirementModel getRetirement() {
        return persistenceManager.getBudgetData().retirement();
    }

    @Override
    public void updateRetirement(RetirementModel retirement) {
        persistenceManager.updateRetirement(retirement);
    }
}
