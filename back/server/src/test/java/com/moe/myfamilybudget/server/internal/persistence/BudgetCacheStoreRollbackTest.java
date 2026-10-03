package com.moe.myfamilybudget.server.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;

/**
 * VT-340 -- Quand une transaction englobante est annulee apres une mutation du cache, la memoire doit
 * revenir a l'etat d'avant la transaction (la base, elle, est annulee par le gestionnaire de transactions).
 * La synchronisation est pilotee a la main : aucune base ni Spring n'est necessaire.
 */
class BudgetCacheStoreRollbackTest {

    private BudgetPersistenceGateway gateway;
    private BudgetCacheStore store;
    private BudgetDataModel before;

    @BeforeEach
    void setUp() {
        gateway = mock(BudgetPersistenceGateway.class);
        store = new BudgetCacheStore(gateway, null);
        store.init();
        before = store.getBudgetData();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Rollback : plusieurs mutations de la meme transaction sont toutes annulees en memoire")
    void rollbackRestoresStateBeforeTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1"))));
        store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1"), income("inc_2"))));
        assertThat(store.getBudgetData().getEffectiveIncomes()).hasSize(2);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(store.getBudgetData()).isSameAs(before);
    }

    @Test
    @DisplayName("Commit : le nouvel etat memoire est conserve")
    void commitKeepsNewState() {
        TransactionSynchronizationManager.initSynchronization();

        BudgetDataModel updated = store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1"))));

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(store.getBudgetData()).isSameAs(updated);
    }

    @Test
    @DisplayName("Rollback : setBudgetData et resetData sont aussi annules")
    void rollbackRestoresAfterImportAndReset() {
        TransactionSynchronizationManager.initSynchronization();
        store.setBudgetData(before.withIncomes(List.of(income("inc_import"))));
        store.resetData();
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(store.getBudgetData()).isSameAs(before);
    }

    @Test
    @DisplayName("Une sauvegarde en echec ne modifie pas la memoire et le rollback ulterieur reste sans effet")
    void failedSaveThenRollbackKeepsPreviousState() {
        TransactionSynchronizationManager.initSynchronization();
        IllegalStateException failure = new IllegalStateException("simulated database failure");
        doThrow(failure).when(gateway).save(any());

        assertThatThrownBy(() -> store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1")))))
                .isSameAs(failure);
        assertThat(store.getBudgetData()).isSameAs(before);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(store.getBudgetData()).isSameAs(before);
    }

    @Test
    @DisplayName("Sans transaction active, la mutation reste appliquee (comportement inchange)")
    void withoutTransactionMutationIsApplied() {
        BudgetDataModel updated = store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1"))));

        assertThat(store.getBudgetData()).isSameAs(updated);
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private void complete(int status) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
    }

    private static IncomeModel income(String id) {
        return new IncomeModel(id, "Salaire", new BigDecimal("100"), "2026-01-01", "2053-12-31",
                BigDecimal.ZERO, "", "");
    }
}
