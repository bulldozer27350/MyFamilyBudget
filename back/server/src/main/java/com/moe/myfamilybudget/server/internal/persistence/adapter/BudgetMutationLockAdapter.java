package com.moe.myfamilybudget.server.internal.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.transition.port.BudgetMutationLock;

/**
 * Adaptateur de persistance pour {@link BudgetMutationLock} (DB-061) : delegue au verrou de mutation du
 * {@code PersistenceManager} (VT-350b).
 */
@Component
public class BudgetMutationLockAdapter implements BudgetMutationLock {

    private final PersistenceManager persistenceManager;

    public BudgetMutationLockAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void lockForCurrentTransaction() {
        persistenceManager.lockForCurrentTransaction();
    }
}
