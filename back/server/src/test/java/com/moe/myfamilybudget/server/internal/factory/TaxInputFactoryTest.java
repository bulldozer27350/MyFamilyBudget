package com.moe.myfamilybudget.server.internal.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.AnnualTaxIncome;
import com.moe.myfamilybudget.server.internal.calculation.AnnualTaxableRetirementIncome;
import com.moe.myfamilybudget.server.internal.calculation.AnnualVariableIncome;
import com.moe.myfamilybudget.server.internal.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxCalculator;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;

/**
 * RF-200 : vérifie que {@link TaxInputFactory} reste cohérente avec la logique historique de
 * {@link TaxCalculator} qu'elle réutilise — pas un test de composant du domaine (voir RF-203).
 */
class TaxInputFactoryTest {

    private static BudgetDataModel budgetWithSalaryOnly() {
        SettingsModel settings = new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual", BigDecimal.ZERO,
                21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015"));

        List<IncomeModel> incomes = List.of(
                new IncomeModel("inc1", "Salaire", new BigDecimal("4000"), "2026-01-01", "2026-12-31",
                        BigDecimal.ZERO, null, null));

        List<TaxBracketModel> brackets = List.of(
                new TaxBracketModel("tb1", new BigDecimal("10000"), BigDecimal.ZERO),
                new TaxBracketModel("tb2", null, new BigDecimal("0.20")));

        List<TaxChildModel> children = List.of(
                new TaxChildModel("c1", "Enfant 1", 2015),
                new TaxChildModel("c2", "Sans date", null));

        return new BudgetDataModel(
                settings,
                incomes,
                List.of(), List.of(), List.of(), null,
                children,
                brackets,
                List.of(new TaxRateOverrideModel(2026, new BigDecimal("0.08"))),
                List.of(new TaxActualOverrideModel(2026, new BigDecimal("3500.00"))),
                List.of(), List.of(), List.of(), List.of(), null);
    }

    @Test
    @DisplayName("from() : période de simulation identique à celle de TaxCalculator.findEarliestYear/simulateUntilAge")
    void testPeriodMatchesLegacyComputation() {
        BudgetDataModel data = budgetWithSalaryOnly();

        TaxCalculationInput input = TaxInputFactory.from(data);

        int expectedStart = TaxCalculator.findEarliestYear(data);
        int expectedRetireYear = 1985 + 64;
        int expectedEnd = Math.max(expectedRetireYear + 3, 1985 + 85);
        assertThat(input.period().startYear()).isEqualTo(expectedStart);
        assertThat(input.period().endYear()).isEqualTo(expectedEnd);
    }

    @Test
    @DisplayName("from() : household reprend birthYear/retireAge/childExitAge/taxAbattement des settings")
    void testHouseholdParameters() {
        TaxCalculationInput input = TaxInputFactory.from(budgetWithSalaryOnly());

        assertThat(input.household().birthYear()).isEqualTo(1985);
        assertThat(input.household().retireAge()).isEqualTo(64);
        assertThat(input.household().childExitAge()).isEqualTo(21);
        assertThat(input.household().taxAbattement()).isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("from() : incomes contient le revenu annuel 2026 = 4000 * 12 = 48000, une entrée par année de la période")
    void testAnnualIncomes() {
        BudgetDataModel data = budgetWithSalaryOnly();
        TaxCalculationInput input = TaxInputFactory.from(data);

        int expectedYears = input.period().endYear() - input.period().startYear() + 1;
        assertThat(input.incomes()).hasSize(expectedYears);

        AnnualTaxIncome year2026 = input.incomes().stream()
                .filter(i -> i.year() == 2026).findFirst().orElseThrow();
        assertThat(year2026.amount()).isEqualByComparingTo("48000.00");
    }

    @Test
    @DisplayName("from() : sans revenu variable configuré, variableIncomes est à zéro pour chaque année")
    void testVariableIncomesZeroWhenNoneConfigured() {
        TaxCalculationInput input = TaxInputFactory.from(budgetWithSalaryOnly());

        assertThat(input.variableIncomes())
                .extracting(AnnualVariableIncome::taxableAmount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("from() : sans personne en retraite configurée, retirementIncome est à zéro pour chaque année")
    void testRetirementIncomeZeroWhenNoRetirementConfigured() {
        TaxCalculationInput input = TaxInputFactory.from(budgetWithSalaryOnly());

        assertThat(input.retirementIncome())
                .extracting(AnnualTaxableRetirementIncome::amount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("from() : childBirthYears ne retient que les enfants avec une année de naissance connue")
    void testChildBirthYearsIgnoresNullBirthYear() {
        TaxCalculationInput input = TaxInputFactory.from(budgetWithSalaryOnly());

        assertThat(input.childBirthYears()).containsExactly(2015);
    }

    @Test
    @DisplayName("from() : brackets/rateOverrides/actualOverrides reprennent les montants du modèle source")
    void testBracketsAndOverrides() {
        TaxCalculationInput input = TaxInputFactory.from(budgetWithSalaryOnly());

        assertThat(input.brackets()).hasSize(2);
        assertThat(input.brackets().get(0).upTo()).isEqualByComparingTo("10000");
        assertThat(input.brackets().get(1).rate()).isEqualByComparingTo("0.20");

        assertThat(input.rateOverrides()).hasSize(1);
        assertThat(input.rateOverrides().get(0).year()).isEqualTo(2026);
        assertThat(input.rateOverrides().get(0).rate()).isEqualByComparingTo("0.08");

        assertThat(input.actualOverrides()).hasSize(1);
        assertThat(input.actualOverrides().get(0).year()).isEqualTo(2026);
        assertThat(input.actualOverrides().get(0).amount()).isEqualByComparingTo("3500.00");
    }
}
