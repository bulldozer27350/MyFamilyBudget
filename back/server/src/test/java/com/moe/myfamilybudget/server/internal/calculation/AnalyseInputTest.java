package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyseInputTest {

    @Test
    @DisplayName("AnalysisPeriod remplace une date nulle par aujourd'hui et interdit un historique négatif")
    void testAnalysisPeriodDefaults() {
        AnalysisPeriod withNullDate = new AnalysisPeriod(null, 12);
        assertThat(withNullDate.today()).isEqualTo(LocalDate.now());
        assertThat(withNullDate.monthsBack()).isEqualTo(12);

        AnalysisPeriod negative = new AnalysisPeriod(LocalDate.of(2026, 5, 1), -3);
        assertThat(negative.monthsBack()).isZero();
    }

    @Test
    @DisplayName("MonthlyBudgetLines et BudgetLineKind remplacent les valeurs nulles par des valeurs vides")
    void testMonthlyBudgetLinesAndBudgetLineKindDefaults() {
        MonthlyBudgetLines empty = new MonthlyBudgetLines(null, null);
        assertThat(empty.monthISO()).isEmpty();
        assertThat(empty.lines()).isEmpty();

        BudgetLineKind kind = new BudgetLineKind(null, null);
        assertThat(kind.lineId()).isEmpty();
        assertThat(kind.kind()).isEmpty();
    }

    @Test
    @DisplayName("AnalyseInput remplace toutes les listes et la période nulles par des valeurs vides")
    void testAnalyseInputDefaults() {
        AnalyseInput input = new AnalyseInput(null, null, null, null, null, null, null);

        assertThat(input.period().monthsBack()).isEqualTo(12);
        assertThat(input.transactions()).isEmpty();
        assertThat(input.categories()).isEmpty();
        assertThat(input.matchings()).isEmpty();
        assertThat(input.pendingOperations()).isEmpty();
        assertThat(input.monthlyBudgetLines()).isEmpty();
        assertThat(input.lineKinds()).isEmpty();
    }

    @Test
    @DisplayName("AnalyseInput conserve les valeurs fournies")
    void testAnalyseInputKeepsProvidedValues() {
        AnalysisPeriod period = new AnalysisPeriod(LocalDate.of(2026, 5, 1), 24);
        MonthlyBudgetLines mayLines = new MonthlyBudgetLines("2026-05", List.of(
                new BudgetLineProjection("c1", "Loyer", "charge", java.math.BigDecimal.valueOf(800), "cat1")));
        BudgetLineKind kind = new BudgetLineKind("c1", "charge");

        AnalyseInput input = new AnalyseInput(period, List.of(), List.of(), List.of(), List.of(),
                List.of(mayLines), List.of(kind));

        assertThat(input.period()).isEqualTo(period);
        assertThat(input.monthlyBudgetLines()).containsExactly(mayLines);
        assertThat(input.lineKinds()).containsExactly(kind);
    }
}
