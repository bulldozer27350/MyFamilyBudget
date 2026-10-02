package com.moe.myfamilybudget.server.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportDocumentRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CreditLoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalBracketRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalChildRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.GoalRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ObjectifRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PensionPlanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RetirementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.SettingsRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxActualOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxBracketRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxChildRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxRateOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableOverrideRepository;

/**
 * FIX-020 -- Modifier un parametre ne depend plus de la presence de {@code sweepEnabled}.
 *
 * <p>{@code BudgetMutationService.updateTaxSettings} melangeait un {@code boolean} primitif et le
 * {@code Boolean} nullable {@code sweepEnabled} dans une expression ternaire : l'unboxing implicite levait une
 * {@link NullPointerException} des qu'un autre champ etait modifie sur un budget dont {@code sweepEnabled}
 * n'etait pas renseigne. Ces tests partent d'un budget minimal, sans ce parametre.
 */
class UpdateTaxSettingsMinimalBudgetTest {

    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        BudgetDataRepository budgetDataRepository = mock(BudgetDataRepository.class);
        doAnswer(invocation -> invocation.getArgument(0)).when(budgetDataRepository).save(any());

        persistenceManager = new PersistenceManager(
                budgetDataRepository,
                mock(SettingsRepository.class),
                mock(IncomeRepository.class),
                mock(ChargeRepository.class),
                mock(PlacementRepository.class),
                mock(RealEstateRepository.class),
                mock(OneOffExpenseRepository.class),
                mock(TransferRepository.class),
                mock(VariableIncomeRepository.class),
                mock(VariableOverrideRepository.class),
                mock(TaxChildRepository.class),
                mock(TaxBracketRepository.class),
                mock(TaxRateOverrideRepository.class),
                mock(TaxActualOverrideRepository.class),
                mock(AssetCategoryRepository.class),
                mock(RetirementRepository.class),
                mock(BankImportRepository.class),
                mock(LoanRepository.class),
                mock(ObjectifRepository.class),
                mock(GoalRepository.class),
                mock(CreditLoanRepository.class),
                mock(FiscalChildRepository.class),
                mock(FiscalBracketRepository.class),
                mock(FiscalRateOverrideRepository.class),
                mock(FiscalActualOverrideRepository.class),
                mock(PensionPlanRepository.class),
                mock(BankImportDocumentRepository.class),
                mock(PlatformTransactionManager.class),
                mock(ApplicationEventPublisher.class));
        persistenceManager.init();

        // Budget minimal : tous les parametres de tresorerie facultatifs (sweep, cash) sont absents.
        persistenceManager.setBudgetData(persistenceManager.getBudgetData().withSettings(new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "auto", BigDecimal.ZERO, 21,
                new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015"),
                null, null, null, null)));
        assertThat(settings().sweepEnabled()).isNull();
    }

    @Test
    @DisplayName("Modifier un parametre sur un budget sans sweepEnabled ne leve pas d'exception")
    void updatingAnyFieldDoesNotRequireSweepEnabled() {
        assertThatCode(() -> {
            update("retireAge", 60);
            update("birthYear", 1980);
            update("simulateUntilAge", 90);
            update("inflationRate", "0.03");
            update("pivotDate", "2027-01-01");
            update("pivotMode", "manual");
            update("startBalance", 1500);
            update("childExitAge", 25);
            update("taxAbattement", "0.12");
            update("pass2026", 48000);
            update("passGrowthRate", "0.02");
            update("cashCeiling", 9000);
            update("cashFloor", 500);
            update("cashAlertThreshold", 200);
        }).doesNotThrowAnyException();

        SettingsModel s = settings();
        assertThat(s.retireAge()).isEqualTo(60);
        assertThat(s.birthYear()).isEqualTo(1980);
        assertThat(s.childExitAge()).isEqualTo(25);
        assertThat(s.taxAbattement()).isEqualByComparingTo("0.12");
        assertThat(s.cashCeiling()).isEqualByComparingTo("9000");
        assertThat(s.cashFloor()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Un champ sans rapport avec sweepEnabled le laisse tel quel (absent reste absent)")
    void untouchedSweepEnabledIsLeftUnchanged() {
        update("retireAge", 62);

        assertThat(settings().retireAge()).isEqualTo(62);
        assertThat(settings().sweepEnabled()).isNull();
    }

    @Test
    @DisplayName("sweepEnabled reste modifiable : booleen, chaine ou valeur absente (= desactive)")
    void sweepEnabledCanStillBeSet() {
        update("sweepEnabled", true);
        assertThat(settings().sweepEnabled()).isTrue();

        update("sweepEnabled", "false");
        assertThat(settings().sweepEnabled()).isFalse();

        update("sweepEnabled", "true");
        assertThat(settings().sweepEnabled()).isTrue();

        update("sweepEnabled", null);
        assertThat(settings().sweepEnabled()).isFalse();
    }

    private void update(String field, Object value) {
        persistenceManager.write(m -> m.updateTaxSettings(field, value));
    }

    private SettingsModel settings() {
        return persistenceManager.getBudgetData().settings();
    }
}
