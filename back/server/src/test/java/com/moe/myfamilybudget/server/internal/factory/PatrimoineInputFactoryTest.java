package com.moe.myfamilybudget.server.internal.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.AnnualCashflow;
import com.moe.myfamilybudget.server.internal.calculation.PatrimoineProjectionInput;
import com.moe.myfamilybudget.server.internal.calculation.PlacementEvolutionInput;
import com.moe.myfamilybudget.server.internal.calculation.PlacementHistoryPoint;
import com.moe.myfamilybudget.server.internal.calculation.PlacementProjectionInput;
import com.moe.myfamilybudget.server.internal.calculation.PlacementTransfer;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.domain.budget.TransferModel;

/**
 * RF-300 / RF-301 : vérifie la traduction {@code BudgetDataModel → PatrimoineProjectionInput} et
 * {@code → PlacementEvolutionInput} réalisée par {@link PatrimoineInputFactory}. Ce n'est pas un
 * test des moteurs patrimoniaux (voir RF-302).
 */
class PatrimoineInputFactoryTest {

    private static SettingsModel settings(BigDecimal cashCeiling, BigDecimal cashAlertThreshold) {
        return new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual", new BigDecimal("1500"),
                21, BigDecimal.ZERO, Boolean.TRUE, cashCeiling, new BigDecimal("200"), cashAlertThreshold);
    }

    private static BudgetDataModel budget(
            SettingsModel settings,
            List<IncomeModel> incomes,
            List<ChargeModel> charges,
            List<PlacementModel> placements,
            List<OneOffExpenseModel> oneoff,
            List<TransferModel> transfers) {
        return new BudgetDataModel(settings, incomes, charges, placements, List.of(), null, List.of(), List.of(),
                List.of(), List.of(), oneoff, transfers, List.of(), List.of(), null);
    }

    private static PlacementModel placement(String label, String balanceDate, String from, String until) {
        return new PlacementModel("pl_" + label, label, "Livret", new BigDecimal("10000"), balanceDate,
                new BigDecimal("300"), from, until, new BigDecimal("0.01"), new BigDecimal("0.03"),
                new BigDecimal("0.05"), Boolean.TRUE, null, 2, new BigDecimal("50000"), new BigDecimal("5000"), 1, "");
    }

    @Test
    @DisplayName("from() : horizon = première date connue du budget jusqu'à l'année de retraite")
    void horizon() {
        BudgetDataModel data = budget(settings(null, null), List.of(), List.of(),
                List.of(placement("Livret A", "2024-06-15", "2025-01", null)), List.of(), List.of());

        PatrimoineProjectionInput input = PatrimoineInputFactory.from(data);

        assertThat(input.parameters().startYear()).isEqualTo(2024);
        assertThat(input.parameters().endYear()).isEqualTo(1985 + 64);
    }

    @Test
    @DisplayName("from() : si la retraite précède le début, l'horizon vaut début + 40 ans")
    void horizonFallbackWhenRetirementBeforeStart() {
        SettingsModel earlyRetirement = new SettingsModel(
                1950, 60, 85, BigDecimal.ZERO, "2026-01-01", "manual", BigDecimal.ZERO,
                21, BigDecimal.ZERO, null, null, null, null);
        BudgetDataModel data = budget(earlyRetirement, List.of(), List.of(), List.of(), List.of(), List.of());

        PatrimoineProjectionInput input = PatrimoineInputFactory.from(data);

        assertThat(input.parameters().startYear()).isEqualTo(2026);
        assertThat(input.parameters().endYear()).isEqualTo(2066);
    }

    @Test
    @DisplayName("from() : les paramètres reprennent inflation, solde de départ et seuils de trésorerie")
    void parameters() {
        BudgetDataModel data = budget(settings(new BigDecimal("8000"), new BigDecimal("1000")),
                List.of(), List.of(), List.of(), List.of(), List.of());

        PatrimoineProjectionInput input = PatrimoineInputFactory.from(data);

        assertThat(input.parameters().inflationRate()).isEqualByComparingTo("0.02");
        assertThat(input.parameters().startBalance()).isEqualByComparingTo("1500");
        assertThat(input.parameters().cashCeiling()).isEqualByComparingTo("8000");
        assertThat(input.parameters().cashAlertThreshold()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("from() : seuils de trésorerie non configurés => null")
    void unsetCashThresholdsStayNull() {
        BudgetDataModel data = budget(settings(null, null), List.of(), List.of(), List.of(), List.of(), List.of());

        PatrimoineProjectionInput input = PatrimoineInputFactory.from(data);

        assertThat(input.parameters().cashCeiling()).isNull();
        assertThat(input.parameters().cashAlertThreshold()).isNull();
    }

    @Test
    @DisplayName("from() : un placement est traduit champ à champ, y compris la configuration de sweep et de pause")
    void placementMapping() {
        BudgetDataModel data = budget(settings(null, null), List.of(), List.of(),
                List.of(placement("Livret A", "2025-03-10", "2025-04", "2030-12-31")), List.of(), List.of());

        PlacementProjectionInput placement = PatrimoineInputFactory.from(data).placements().get(0);

        assertThat(placement.id()).isEqualTo("pl_Livret A");
        assertThat(placement.label()).isEqualTo("Livret A");
        assertThat(placement.initialBalance()).isEqualByComparingTo("10000");
        assertThat(placement.balanceDate()).isEqualTo(LocalDate.of(2025, 3, 10));
        assertThat(placement.monthlyContribution()).isEqualByComparingTo("300");
        assertThat(placement.contributionFrom()).isEqualTo(YearMonth.of(2025, 4));
        assertThat(placement.contributionUntil()).isEqualTo(YearMonth.of(2030, 12));
        assertThat(placement.pessimisticRate()).isEqualByComparingTo("0.01");
        assertThat(placement.expectedRate()).isEqualByComparingTo("0.03");
        assertThat(placement.optimisticRate()).isEqualByComparingTo("0.05");
        assertThat(placement.excludedFromRetirement()).isTrue();
        assertThat(placement.sweepPriority()).isEqualTo(2);
        assertThat(placement.sweepCap()).isEqualByComparingTo("50000");
        assertThat(placement.pauseTriggerBalance()).isEqualByComparingTo("5000");
        assertThat(placement.pausePriority()).isEqualTo(1);
    }

    @Test
    @DisplayName("from() : dates absentes ou illisibles => null, valeurs numériques absentes => 0")
    void placementWithMissingValues() {
        PlacementModel bare = new PlacementModel("pl1", "Vide", "Livret", null, "pas-une-date", null, null, null,
                null, null, null, null, null);
        BudgetDataModel data = budget(settings(null, null), List.of(), List.of(), List.of(bare), List.of(), List.of());

        PlacementProjectionInput placement = PatrimoineInputFactory.from(data).placements().get(0);

        assertThat(placement.balanceDate()).isNull();
        assertThat(placement.contributionFrom()).isNull();
        assertThat(placement.contributionUntil()).isNull();
        assertThat(placement.initialBalance()).isEqualByComparingTo("0");
        assertThat(placement.monthlyContribution()).isEqualByComparingTo("0");
        assertThat(placement.excludedFromRetirement()).isFalse();
        assertThat(placement.sweepPriority()).isNull();
        assertThat(placement.pausePriority()).isNull();
    }

    @Test
    @DisplayName("from() : les retraits sans placement ou sans date lisible sont écartés")
    void transfers() {
        List<TransferModel> transfers = List.of(
                new TransferModel("t1", "Livret A", "2027-05-20", new BigDecimal("2000"), null),
                new TransferModel("t2", null, "2027-05-20", new BigDecimal("100"), null),
                new TransferModel("t3", "Livret A", null, new BigDecimal("100"), null),
                new TransferModel("t4", "Livret A", "n'importe quoi", new BigDecimal("100"), null),
                new TransferModel("t5", "PEA", "2028-01", null, null));
        BudgetDataModel data = budget(settings(null, null), List.of(), List.of(), List.of(), List.of(), transfers);

        List<PlacementTransfer> result = PatrimoineInputFactory.from(data).transfers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).placementLabel()).isEqualTo("Livret A");
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2027, 5, 20));
        assertThat(result.get(0).amount()).isEqualByComparingTo("2000");
        assertThat(result.get(1).placementLabel()).isEqualTo("PEA");
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(result.get(1).amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("from() : flux net annuel = revenus - charges - dépenses ponctuelles, hors placements")
    void cashflow() {
        List<IncomeModel> incomes = List.of(
                new IncomeModel("i1", "Salaire", new BigDecimal("3000"), "2026-01-01", "2026-12-31",
                        BigDecimal.ZERO, null, null));
        List<ChargeModel> charges = List.of(
                new ChargeModel("c1", "Loyer", new BigDecimal("1000"), "2026-01-01", "2027-12-31",
                        BigDecimal.ZERO, null, null));
        List<OneOffExpenseModel> oneoff = List.of(
                new OneOffExpenseModel("o1", "Voiture", "2026-06-01", new BigDecimal("500"), null));
        BudgetDataModel data = budget(settings(null, null), incomes, charges, List.of(), oneoff, List.of());

        List<AnnualCashflow> years = PatrimoineInputFactory.from(data).cashflow().years();

        assertThat(years.get(0).year()).isEqualTo(2026);
        assertThat(years.get(0).netCashflow()).isEqualByComparingTo("23500");
        assertThat(years.get(1).year()).isEqualTo(2027);
        assertThat(years.get(1).netCashflow()).isEqualByComparingTo("-12000");
        assertThat(years.get(2).netCashflow()).isEqualByComparingTo("0");
        assertThat(years).hasSize(2049 - 2026 + 1);
    }

    @Test
    @DisplayName("from() : une charge sans taux propre suit l'inflation, un revenu sans fin ne compte aucun mois")
    void cashflowInflationAndOpenEndedIncome() {
        List<IncomeModel> incomes = List.of(
                new IncomeModel("i1", "Sans fin", new BigDecimal("3000"), "2026-01-01", null,
                        BigDecimal.ZERO, null, null));
        List<ChargeModel> charges = List.of(
                new ChargeModel("c1", "Loyer", new BigDecimal("1000"), "2026-01-01", "2027-12-31",
                        null, null, null));
        BudgetDataModel data = budget(settings(null, null), incomes, charges, List.of(), List.of(), List.of());

        List<AnnualCashflow> years = PatrimoineInputFactory.from(data).cashflow().years();

        // 2027 : 1000 * 1,02 * 12 mois ; le revenu sans date de fin est ignoré (comportement historique)
        assertThat(years.get(1).netCashflow().doubleValue()).isEqualTo(-12240.0);
    }

    @Test
    @DisplayName("from() : budget vide => entrée exploitable sans placements ni retraits")
    void emptyBudget() {
        BudgetDataModel data = budget(null, null, null, null, null, null);

        PatrimoineProjectionInput input = PatrimoineInputFactory.from(data);

        assertThat(input.placements()).isEmpty();
        assertThat(input.transfers()).isEmpty();
        assertThat(input.cashflow().years()).isNotEmpty();
        assertThat(input.parameters().startYear()).isEqualTo(2026);
    }

    private static PlacementModel placementWithHistory(
            String label, String balance, List<PlacementHistoryEntryModel> history) {
        return new PlacementModel("pl_" + label, label, "Livret", new BigDecimal(balance), "2024-01-01",
                new BigDecimal("100"), "2024-01", null, new BigDecimal("0.01"), new BigDecimal("0.03"),
                new BigDecimal("0.05"), null, "", null, null, null, null, "", history);
    }

    @Test
    @DisplayName("forPlacementEvolution() : historique trié à part, dates illisibles écartées, paramètres repris")
    void placementEvolutionInput() {
        PlacementModel traced = placementWithHistory("Livret A", "1000", List.of(
                new PlacementHistoryEntryModel("h1", "2025-03-01", new BigDecimal("1200"), null),
                new PlacementHistoryEntryModel("h2", "pas une date", new BigDecimal("999"), null),
                new PlacementHistoryEntryModel("h3", "2025-01-01", new BigDecimal("1100"), null)));
        BudgetDataModel data = budget(settings(new BigDecimal("9000"), new BigDecimal("300")), List.of(), List.of(),
                List.of(traced), List.of(), List.of());

        PlacementEvolutionInput input = PatrimoineInputFactory.forPlacementEvolution(
                data, traced, LocalDate.of(2026, 9, 28));

        assertThat(input.placement().label()).isEqualTo("Livret A");
        assertThat(input.history()).extracting(PlacementHistoryPoint::date)
                .containsExactlyInAnyOrder(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 1, 1));
        assertThat(input.parameters().today()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(input.parameters().horizonYears()).isEqualTo(15);
        assertThat(input.parameters().inflationRate()).isEqualByComparingTo("0.02");
        assertThat(input.parameters().cashCeiling()).isEqualByComparingTo("9000");
        assertThat(input.parameters().cashAlertThreshold()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("forPlacementEvolution() : chaque placement porte sa dernière valeur connue, sinon son solde")
    void placementEvolutionBackground() {
        PlacementModel withHistory = placementWithHistory("Livret A", "1000", List.of(
                new PlacementHistoryEntryModel("h1", "2025-01-01", new BigDecimal("1100"), null),
                new PlacementHistoryEntryModel("h2", "2025-03-01", new BigDecimal("1200"), null)));
        PlacementModel withoutHistory = placementWithHistory("PEA", "5000", List.of());
        List<TransferModel> transfers = List.of(
                new TransferModel("t1", "PEA", "2027-05-20", new BigDecimal("2000"), null),
                new TransferModel("t2", null, "2027-05-20", new BigDecimal("100"), null));
        BudgetDataModel data = budget(settings(null, null), List.of(), List.of(),
                List.of(withHistory, withoutHistory), List.of(), transfers);

        PlacementEvolutionInput input = PatrimoineInputFactory.forPlacementEvolution(
                data, withoutHistory, LocalDate.of(2026, 9, 28));

        assertThat(input.background()).hasSize(2);
        assertThat(input.background().get(0).latestKnownBalance()).isEqualByComparingTo("1200");
        assertThat(input.background().get(1).latestKnownBalance()).isEqualByComparingTo("5000");
        assertThat(input.history()).isEmpty();
        assertThat(input.transfers()).hasSize(1);
        assertThat(input.transfers().get(0).placementLabel()).isEqualTo("PEA");
    }
}
