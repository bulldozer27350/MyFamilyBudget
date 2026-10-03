package com.moe.myfamilybudget.server.internal.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.ChargeProjectionInput;
import com.moe.myfamilybudget.server.internal.calculation.IncomeProjectionInput;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementCashflowInput;
import com.moe.myfamilybudget.server.internal.calculation.TreasuryProjectionInput;
import com.moe.myfamilybudget.server.internal.calculation.VariableIncomeProjection;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;

/**
 * RF-400 : vérifie la traduction {@code BudgetDataModel → TreasuryProjectionInput} réalisée par
 * {@link TreasuryInputFactory}. Ce n'est pas un test du moteur de trésorerie, qui reçoit encore
 * {@code BudgetDataModel} directement jusqu'au branchement (RF-401).
 */
class TreasuryInputFactoryTest {

    private final TreasuryInputFactory factory = new TreasuryInputFactory();

    private static SettingsModel settings() {
        return new SettingsModel(1985, 64, 85, new BigDecimal("0.02"), null, "manual", new BigDecimal("1500"),
                21, BigDecimal.ZERO);
    }

    private static BudgetDataModel budget(
            SettingsModel settings,
            List<IncomeModel> incomes,
            List<ChargeModel> charges,
            List<PlacementModel> placements,
            List<VariableIncomeModel> variableIncomes,
            List<VariableOverrideModel> variableOverrides) {
        return new BudgetDataModel(settings, incomes, charges, placements, List.of(), null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), variableIncomes, variableOverrides, null, List.of(),
                List.of(), List.of());
    }

    @Test
    @DisplayName("period : horizon = première date connue du budget jusqu'à retraite + simulateUntilAge")
    void period() {
        IncomeModel income = new IncomeModel("i1", "Salaire", new BigDecimal("3000"), "2024-06-01", "2050-12-31",
                null, "", "");
        BudgetDataModel data = budget(settings(), List.of(income), List.of(), List.of(), List.of(), List.of());

        TreasuryProjectionInput input = factory.from(data);

        assertThat(input.period().startYear()).isEqualTo(2024);
        assertThat(input.period().endYear()).isEqualTo(1985 + 85);
    }

    @Test
    @DisplayName("incomes/charges : normalisation directe, dates parsées, valeurs nulles à 0")
    void incomesAndCharges() {
        IncomeModel income = new IncomeModel("i1", "Salaire", new BigDecimal("3000"), "2024-06-01", "2050-12-31",
                new BigDecimal("0.01"), "", "");
        ChargeModel charge = new ChargeModel("c1", "Loyer", new BigDecimal("900"), "2024-01-01", "2050-12-31", null, "", "");
        BudgetDataModel data = budget(settings(), List.of(income), List.of(charge), List.of(), List.of(), List.of());

        TreasuryProjectionInput input = factory.from(data);

        assertThat(input.incomes()).containsExactly(new IncomeProjectionInput(
                "Salaire", new BigDecimal("3000"), LocalDate.of(2024, 6, 1), LocalDate.of(2050, 12, 31), new BigDecimal("0.01")));
        assertThat(input.charges()).containsExactly(new ChargeProjectionInput(
                "Loyer", new BigDecimal("900"), LocalDate.of(2024, 1, 1), LocalDate.of(2050, 12, 31), null));
    }

    @Test
    @DisplayName("variableIncomes : rattache les montants réels saisis par label et année")
    void variableIncomeOverrides() {
        VariableIncomeModel v = new VariableIncomeModel("v1", "Prime", "Salaire", new BigDecimal("0.1"), 2025, 2030,
                "Non", "", "");
        VariableOverrideModel override = new VariableOverrideModel("o1", "Prime", 2026, new BigDecimal("500"), "Oui", "");
        VariableOverrideModel otherLabel = new VariableOverrideModel("o2", "Autre", 2026, new BigDecimal("999"), null, "");
        BudgetDataModel data = budget(settings(), List.of(), List.of(), List.of(), List.of(v), List.of(override, otherLabel));

        TreasuryProjectionInput input = factory.from(data);

        assertThat(input.variableIncomes()).containsExactly(new VariableIncomeProjection(
                "Prime", "Salaire", 2025, 2030, new BigDecimal("0.1"), "Non",
                List.of(new VariableIncomeProjection.Override(2026, new BigDecimal("500"), "Oui"))));
    }

    @Test
    @DisplayName("placements : somme des versements mensuels dans la fenêtre, sans pause")
    void placementsIgnorePause() {
        PlacementModel paused = new PlacementModel("p1", "Livret A", "Livret", new BigDecimal("10000"), "2024-01-01",
                new BigDecimal("300"), "2024-01", null, new BigDecimal("0.01"), new BigDecimal("0.02"),
                new BigDecimal("0.03"), Boolean.FALSE, "", 1, null, new BigDecimal("50000"), 1, "");
        BudgetDataModel data = budget(settings(), List.of(), List.of(), List.of(paused), List.of(), List.of());

        TreasuryProjectionInput input = factory.from(data);

        PlacementCashflowInput year2026 = input.placements().stream()
                .filter(p -> p.year() == 2026).findFirst().orElseThrow();
        assertThat(year2026.amount()).isEqualByComparingTo("3600");
    }

    @Test
    @DisplayName("taxProjection et retirementIncome : une entrée par année de la période, aucune levée d'exception à vide")
    void taxAndRetirementProjectionsCoverThePeriod() {
        BudgetDataModel data = budget(settings(), List.of(), List.of(), List.of(), List.of(), List.of());

        TreasuryProjectionInput input = factory.from(data);

        int expectedYears = input.period().endYear() - input.period().startYear() + 1;
        assertThat(input.taxProjection().years()).hasSize(expectedYears);
        assertThat(input.retirementIncome().years()).allMatch(y -> y.amount().signum() == 0);
    }
}
