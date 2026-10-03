package com.moe.myfamilybudget.domain.wealth.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.moe.myfamilybudget.domain.wealth.model.PatrimoineYearModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel;

/**
 * RF-302 : {@link PatrimoineProjectionService} testé uniquement avec {@link PatrimoineProjectionInput}
 * (aucun {@code BudgetDataModel}, voir doc/architecture/05-domaine-patrimoine.md).
 */
class PatrimoineProjectionServiceTest {

    private final PatrimoineProjectionService service = new PatrimoineProjectionService();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static PlacementProjectionInput placement(String label, String initial, String monthly,
            String pess, String corr, String opti, YearMonth from, YearMonth until,
            Integer pausePriority, BigDecimal pauseTriggerBalance) {
        return new PlacementProjectionInput(label, label, bd(initial), LocalDate.of(2026, 1, 1), bd(monthly),
                from, until, bd(pess), bd(corr), bd(opti), false, null, null, pauseTriggerBalance, pausePriority);
    }

    private static PatrimoineProjectionInput input(List<PlacementProjectionInput> placements,
            List<PlacementTransfer> transfers, List<AnnualCashflow> cashflows,
            PatrimoineProjectionParameters parameters) {
        return new PatrimoineProjectionInput(placements, transfers, new CashflowProjection(cashflows), parameters);
    }

    @Test
    @DisplayName("Projection simple sans versement : chaque scénario compose son propre taux annuel")
    void testSimpleGrowthNoContribution() {
        PlacementProjectionInput p = placement("Livret", "1000", "0", "0", "0.1", "0.2", null, null, null, null);
        PatrimoineProjectionInput in = input(List.of(p), List.of(), List.of(),
                new PatrimoineProjectionParameters(2026, 2027, null, null, null, null));

        PatrimoineProjectionsModel result = service.compute(in, false);

        List<PatrimoineYearModel> rows = result.perPlacement().get(0).rows();
        assertThat(rows.get(0).corr()).isEqualByComparingTo("1100.0");
        assertThat(rows.get(1).corr()).isEqualByComparingTo("1210.00");
        assertThat(result.totals().get(1).corr()).isEqualByComparingTo(rows.get(1).corr());
    }

    @Test
    @DisplayName("Le versement mensuel est annualisé (x12) et s'ajoute avant application du taux de l'année suivante")
    void testAnnualContribution() {
        PlacementProjectionInput p = placement("Livret", "1000", "100", "0", "0", "0", null, null, null, null);
        PatrimoineProjectionInput in = input(List.of(p), List.of(), List.of(),
                new PatrimoineProjectionParameters(2026, 2026, null, null, null, null));

        PatrimoineProjectionsModel result = service.compute(in, false);

        assertThat(result.totals().get(0).corr()).isEqualByComparingTo("2200");
    }

    @Test
    @DisplayName("Un retrait rapproché par libellé (insensible à la casse) réduit le solde de l'année du retrait")
    void testTransferReducesBalance() {
        PlacementProjectionInput p = placement("Livret", "1000", "0", "0", "0", "0", null, null, null, null);
        PatrimoineProjectionInput in = input(List.of(p),
                List.of(new PlacementTransfer("LIVRET", LocalDate.of(2026, 6, 1), bd("500"))), List.of(),
                new PatrimoineProjectionParameters(2026, 2026, null, null, null, null));

        PatrimoineProjectionsModel result = service.compute(in, false);

        assertThat(result.totals().get(0).corr()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("contributionUntil borne la fenêtre de versement : plus aucun versement l'année suivante")
    void testContributionUntilBound() {
        PlacementProjectionInput p = placement("Livret", "1000", "100", "0", "0", "0",
                null, YearMonth.of(2026, 12), null, null);
        PatrimoineProjectionInput in = input(List.of(p), List.of(), List.of(),
                new PatrimoineProjectionParameters(2026, 2027, null, null, null, null));

        PatrimoineProjectionsModel result = service.compute(in, false);

        assertThat(result.totals().get(0).corr()).isEqualByComparingTo("2200");
        assertThat(result.totals().get(1).corr()).isEqualByComparingTo(result.totals().get(0).corr());
    }

    @Test
    @DisplayName("Une trésorerie sous le seuil d'alerte déclenche la pause pour l'année suivante seulement")
    void testCashAlertPausesNextYear() {
        PlacementProjectionInput p = placement("Livret", "1000", "100", "0", "0", "0", null, null, 1, null);
        PatrimoineProjectionInput in = input(List.of(p), List.of(),
                List.of(new AnnualCashflow(2026, bd("-5000"))),
                new PatrimoineProjectionParameters(2026, 2027, null, bd("1000"), null, bd("100")));

        PatrimoineProjectionsModel result = service.compute(in, false);

        assertThat(result.totals().get(0).corr()).isEqualByComparingTo("2200");
        assertThat(result.totals().get(1).corr()).isEqualByComparingTo(result.totals().get(0).corr());
    }

    @Test
    @DisplayName("useConstantEuros déflate les totaux par année écoulée, pas les lignes par placement")
    void testConstantEuros() {
        PlacementProjectionInput p = placement("Livret", "1000", "0", "0", "0", "0", null, null, null, null);
        PatrimoineProjectionInput in = input(List.of(p), List.of(), List.of(),
                new PatrimoineProjectionParameters(2026, 2027, bd("0.10"), null, null, null));

        PatrimoineProjectionsModel result = service.compute(in, true);

        assertThat(result.perPlacement().get(0).rows().get(1).corr()).isEqualByComparingTo("1000");
        // Pas de déflation la première année projetée (exposant = année - startYear = 0).
        assertThat(result.totals().get(0).corr()).isEqualByComparingTo("1000");
        assertThat(result.totals().get(1).corr().doubleValue()).isCloseTo(1000 / 1.10, within(0.01));
    }
}
