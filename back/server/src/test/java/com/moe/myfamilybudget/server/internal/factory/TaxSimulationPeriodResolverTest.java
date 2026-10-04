package com.moe.myfamilybudget.server.internal.factory;

import com.moe.myfamilybudget.application.factory.TaxSimulationPeriodResolver;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;

/**
 * RF-202 : la période de simulation fiscale est déduite en amont, hors du moteur fiscal.
 */
class TaxSimulationPeriodResolverTest {

    private static TaxSimulationPeriodResolver.Sources budget(String incomeStart) {
        List<IncomeModel> incomes = List.of(
                new IncomeModel("inc1", "Salaire", new BigDecimal("4000"), incomeStart, "2026-12-31",
                        BigDecimal.ZERO, null, null));
        return new TaxSimulationPeriodResolver.Sources(
                new RetirementSettingsModel(1985, 64), new SimulationSettingsModel(85), "2026-01-01",
                incomes, List.of(), List.of(), List.of(), List.of(), null);
    }

    @Test
    @DisplayName("resolve() : début = plus ancienne date connue, fin = max(retraite + 3, âge de simulation)")
    void testResolve() {
        TaxSimulationPeriod period = TaxSimulationPeriodResolver.resolve(budget("2024-03-01"));

        assertThat(period.startYear()).isEqualTo(2024);
        assertThat(period.endYear()).isEqualTo(Math.max(1985 + 64 + 3, 1985 + 85));
    }

    @Test
    @DisplayName("resolve() : date au format AAAA-MM prise en compte")
    void testResolveYearMonthFormat() {
        assertThat(TaxSimulationPeriodResolver.resolve(budget("2022-05")).startYear()).isEqualTo(2022);
    }

    @Test
    @DisplayName("resolve() : la pivotDate des settings participe à la plus ancienne date")
    void testResolveUsesPivotDate() {
        assertThat(TaxSimulationPeriodResolver.resolve(budget("2026-01-01")).startYear()).isEqualTo(2026);
    }
}
