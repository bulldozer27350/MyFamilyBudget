package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.LoanReader;

/**
 * Adaptateur de persistance pour {@link LoanReader} (RF-B00).
 */
@Component
public class LoanPersistenceAdapter implements LoanReader {

    private final PersistenceManager persistenceManager;

    public LoanPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public List<LoanModel> getLoans() {
        return persistenceManager.getBudgetData().getEffectiveLoans();
    }
}