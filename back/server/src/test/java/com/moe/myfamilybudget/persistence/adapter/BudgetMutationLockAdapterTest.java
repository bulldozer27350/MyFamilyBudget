package com.moe.myfamilybudget.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.transition.port.BudgetMutationLock;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * DB-061 -- Le verrou de mutation du budget est un port transverse : l'adaptateur delegue au
 * {@code PersistenceManager} (VT-350b).
 */
@DisplayName("DB-061 -- BudgetMutationLock")
class BudgetMutationLockAdapterTest {

    @Test
    @DisplayName("l'adaptateur ne bloque rien hors transaction et reste appelable plusieurs fois")
    void lockWithoutTransactionIsANoOp() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        BudgetMutationLock lock = new BudgetMutationLockAdapter(persistenceManager);

        assertThatCode(() -> {
            lock.lockForCurrentTransaction();
            lock.lockForCurrentTransaction();
        }).doesNotThrowAnyException();
    }
}
