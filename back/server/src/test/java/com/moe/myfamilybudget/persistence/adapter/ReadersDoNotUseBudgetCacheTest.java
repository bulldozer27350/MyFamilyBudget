package com.moe.myfamilybudget.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowChargeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowOneOffRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowTransferRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.domain.credit.core.persistence.CreditLoanRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.transition.port.BudgetReader;

/**
 * DB-1070 -- Preuve d'execution que les readers bascules sur JPA (Budget/Tresorerie, Patrimoine, Fiscalite,
 * Retraite, Banque, Credit, Objectifs) lisent les tables de leur domaine et non le cache memoire du
 * {@link PersistenceManager}. Complete {@code ReaderPersistenceBoundaryArchTest}, qui garde la frontiere
 * statiquement.
 *
 * <p>Seul {@code SettingsPersistenceAdapter} lit encore le cache : les parametres restent stockes dans le hub
 * jusqu'a la separation de leur stockage.
 *
 * <p>Base H2 dediee, pour ne pas partager d'etat avec les autres contextes de test.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:db1070;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@DisplayName("DB-1070 -- Les readers bascules ne lisent plus le cache du budget")
class ReadersDoNotUseBudgetCacheTest {

    private static final IncomeModel INCOME = new IncomeModel("inc_1070", "Salaire", new BigDecimal("2500"),
            "2026-01-01", "2030-12-31", new BigDecimal("0"), "", "");

    @Autowired
    private PersistenceManager writer;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CashflowIncomeRepository cashflowIncomeRepository;

    @BeforeEach
    void startFromBlankDatabase() {
        writer.resetData();
    }

    @Test
    @DisplayName("tous les readers JPA repondent sans jamais appeler le PersistenceManager")
    void jpaReadersNeverCallPersistenceManager() {
        writer.setBudgetData(writer.getBudgetData().withIncomes(List.of(INCOME)));
        PersistenceManager cache = mock(PersistenceManager.class);

        BudgetPersistenceAdapter budget = new BudgetPersistenceAdapter(cache,
                context.getBean(CashflowIncomeRepository.class),
                context.getBean(CashflowChargeRepository.class),
                context.getBean(CashflowOneOffRepository.class),
                context.getBean(CashflowVariableIncomeRepository.class),
                context.getBean(CashflowVariableOverrideRepository.class));
        PatrimoinePersistenceAdapter patrimoine = new PatrimoinePersistenceAdapter(cache,
                context.getBean(WealthPlacementRepository.class),
                context.getBean(WealthRealEstateRepository.class),
                context.getBean(WealthCategoryRepository.class),
                context.getBean(CashflowTransferRepository.class));
        TaxPersistenceAdapter tax = new TaxPersistenceAdapter(cache,
                context.getBean(FiscalChildRepository.class),
                context.getBean(FiscalBracketRepository.class),
                context.getBean(FiscalRateOverrideRepository.class),
                context.getBean(FiscalActualOverrideRepository.class));
        RetirementPersistenceAdapter retirement = new RetirementPersistenceAdapter(cache,
                context.getBean(PensionPlanRepository.class));
        BankPersistenceAdapter bank = new BankPersistenceAdapter(cache,
                context.getBean(BankImportDocumentRepository.class));
        LoanPersistenceAdapter loans = new LoanPersistenceAdapter(cache, context.getBean(CreditLoanRepository.class));
        GoalPersistenceAdapter goals = new GoalPersistenceAdapter(cache, context.getBean(GoalRepository.class));

        assertThat(budget.getIncomes()).extracting(IncomeModel::id).containsExactly("inc_1070");
        assertThat(budget.getCharges()).isNotNull();
        assertThat(budget.getOneoffExpenses()).isNotNull();
        assertThat(budget.getVariableIncomes()).isNotNull();
        assertThat(budget.getVariableOverrides()).isNotNull();
        assertThat(patrimoine.getPlacements()).isNotNull();
        assertThat(patrimoine.getRealEstate()).isNotNull();
        assertThat(patrimoine.getAssetCategories()).isNotNull();
        assertThat(patrimoine.getTransfers()).isNotNull();
        assertThat(tax.getTaxChildren()).isNotNull();
        assertThat(tax.getTaxBrackets()).isNotNull();
        assertThat(tax.getTaxRateOverrides()).isNotNull();
        assertThat(tax.getTaxActualOverrides()).isNotNull();
        retirement.getRetirement();
        assertThat(bank.getBankImport()).isNotNull();
        assertThat(loans.getLoans()).isNotNull();
        assertThat(goals.getGoals()).isNotNull();

        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("le reader Spring suit la table du domaine, pas le cache memoire")
    void springWiredReaderFollowsDomainTableNotCache() {
        writer.setBudgetData(writer.getBudgetData().withIncomes(List.of(INCOME)));
        BudgetReader reader = context.getBean(BudgetReader.class);
        assertThat(reader.getIncomes()).extracting(IncomeModel::id).containsExactly("inc_1070");

        // Vide uniquement la table autonome : le cache du PersistenceManager garde encore le revenu.
        cashflowIncomeRepository.deleteAll();

        assertThat(writer.getBudgetData().getEffectiveIncomes()).hasSize(1);
        assertThat(reader.getIncomes()).isEmpty();
    }
}
