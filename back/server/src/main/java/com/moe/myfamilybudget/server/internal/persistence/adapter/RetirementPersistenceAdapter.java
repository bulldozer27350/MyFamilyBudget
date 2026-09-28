package com.moe.myfamilybudget.server.internal.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.RetirementReader;

/**
 * Adaptateur de persistance pour {@link RetirementReader} (RF-B00).
 */
@Component
public class RetirementPersistenceAdapter implements RetirementReader {

    private final PersistenceManager persistenceManager;

    public RetirementPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public RetirementModel getRetirement() {
        return persistenceManager.getBudgetData().retirement();
    }
}