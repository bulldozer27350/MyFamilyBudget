package com.moe.myfamilybudget.server.internal.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

class PersistenceAdaptersTest {

    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
    }

    @Test
    @DisplayName("BudgetPersistenceAdapter returns non-null collections from BudgetDataModel")
    void testBudgetPersistenceAdapter() {
        BudgetPersistenceAdapter adapter = new BudgetPersistenceAdapter(persistenceManager);

        assertThat(adapter.getIncomes()).isNotNull();
        assertThat(adapter.getCharges()).isNotNull();
        assertThat(adapter.getOneoffExpenses()).isNotNull();
        assertThat(adapter.getVariableIncomes()).isNotNull();
        assertThat(adapter.getVariableOverrides()).isNotNull();
    }

    @Test
    @DisplayName("PatrimoinePersistenceAdapter returns non-null collections from BudgetDataModel")
    void testPatrimoinePersistenceAdapter() {
        PatrimoinePersistenceAdapter adapter = new PatrimoinePersistenceAdapter(persistenceManager);

        assertThat(adapter.getPlacements()).isNotNull();
        assertThat(adapter.getRealEstate()).isNotNull();
        assertThat(adapter.getAssetCategories()).isNotNull();
        assertThat(adapter.getTransfers()).isNotNull();
    }

    @Test
    @DisplayName("RetirementPersistenceAdapter returns retirement model")
    void testRetirementPersistenceAdapter() {
        RetirementPersistenceAdapter adapter = new RetirementPersistenceAdapter(persistenceManager);

        assertThat(adapter.getRetirement()).isNotNull();
    }

    @Test
    @DisplayName("TaxPersistenceAdapter returns tax configurations")
    void testTaxPersistenceAdapter() {
        TaxPersistenceAdapter adapter = new TaxPersistenceAdapter(persistenceManager);

        assertThat(adapter.getTaxChildren()).isNotNull();
        assertThat(adapter.getTaxBrackets()).isNotNull();
        assertThat(adapter.getTaxRateOverrides()).isNotNull();
        assertThat(adapter.getTaxActualOverrides()).isNotNull();
    }

    @Test
    @DisplayName("BankPersistenceAdapter returns bank import")
    void testBankPersistenceAdapter() {
        BankPersistenceAdapter adapter = new BankPersistenceAdapter(persistenceManager);

        assertThat(adapter.getBankImport()).isNotNull();
    }

    @Test
    @DisplayName("LoanPersistenceAdapter returns loans")
    void testLoanPersistenceAdapter() {
        LoanPersistenceAdapter adapter = new LoanPersistenceAdapter(persistenceManager);

        assertThat(adapter.getLoans()).isNotNull();
    }

    @Test
    @DisplayName("GoalPersistenceAdapter returns goals")
    void testGoalPersistenceAdapter() {
        GoalPersistenceAdapter adapter = new GoalPersistenceAdapter(persistenceManager);

        assertThat(adapter.getGoals()).isNotNull();
    }

    @Test
    @DisplayName("SettingsPersistenceAdapter returns settings")
    void testSettingsPersistenceAdapter() {
        SettingsPersistenceAdapter adapter = new SettingsPersistenceAdapter(persistenceManager);

        assertThat(adapter.getSettings()).isNotNull();
        assertThat(adapter.getSettings().getEffectiveBirthYear()).isPositive();
    }
}