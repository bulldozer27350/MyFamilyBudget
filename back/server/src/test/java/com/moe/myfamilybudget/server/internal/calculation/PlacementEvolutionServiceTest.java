package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.PlacementEvolutionInput.BackgroundPlacement;

/**
 * RF-302 : {@link PlacementEvolutionService} testé uniquement avec {@link PlacementEvolutionInput}
 * (aucun {@code BudgetDataModel}, voir doc/architecture/05-domaine-patrimoine.md).
 */
class PlacementEvolutionServiceTest {

    private final PlacementEvolutionService service = new PlacementEvolutionService();
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static PlacementProjectionInput placement(String label, String initial, LocalDate balanceDate,
            String monthly, String rate, YearMonth from, YearMonth until, Integer pausePriority) {
        return new PlacementProjectionInput(label, label, bd(initial), balanceDate, bd(monthly), from, until,
                bd(rate), bd(rate), bd(rate), false, null, null, null, pausePriority);
    }

    private static PlacementEvolutionParameters params(int horizonYears, BigDecimal inflationRate,
            BigDecimal cashCeiling, BigDecimal cashAlertThreshold) {
        return new PlacementEvolutionParameters(TODAY, horizonYears, inflationRate, BigDecimal.ZERO,
                cashCeiling, cashAlertThreshold);
    }

    @Test
    @DisplayName("Sans historique : l'ancrage est le solde/date de référence du placement, projeté mensuellement")
    void testNoHistoryAnchorsOnBalanceDate() {
        PlacementProjectionInput p = placement("Livret", "1000", LocalDate.of(2026, 1, 1), "0", "0.12",
                null, null, null);
        PlacementEvolutionInput in = new PlacementEvolutionInput(p, List.of(),
                List.of(new BackgroundPlacement(p, bd("1000"))), List.of(), params(1, null, null, null));

        PlacementEvolution evolution = service.compute(in, false);

        assertThat(evolution.anchorDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(evolution.points()).hasSize(13);
        assertThat(evolution.points().get(0).real()).isEqualByComparingTo("1000");
        assertThat(evolution.points().get(0).corr()).isEqualByComparingTo("1000");
        assertThat(evolution.points().get(12).corr().doubleValue())
                .isCloseTo(1000 * Math.pow(1.01, 12), within(0.01));
    }

    @Test
    @DisplayName("Avec historique : l'ancrage repart de la dernière valeur réelle, triée par date")
    void testHistoryAnchorsOnLastRealValue() {
        PlacementProjectionInput p = placement("Livret", "1000", LocalDate.of(2026, 1, 1), "0", "0", null, null,
                null);
        List<PlacementHistoryPoint> history = List.of(
                new PlacementHistoryPoint(LocalDate.of(2026, 3, 1), bd("1300")),
                new PlacementHistoryPoint(LocalDate.of(2026, 2, 1), bd("1200")));
        PlacementEvolutionInput in = new PlacementEvolutionInput(p, history,
                List.of(new BackgroundPlacement(p, bd("1300"))), List.of(), params(1, null, null, null));

        PlacementEvolution evolution = service.compute(in, false);

        assertThat(evolution.anchorDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(evolution.points().get(0).date()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(evolution.points().get(0).real()).isEqualByComparingTo("1200");
        assertThat(evolution.points().get(1).real()).isEqualByComparingTo("1300");
        assertThat(evolution.points().get(1).corr()).isEqualByComparingTo("1300");
    }

    @Test
    @DisplayName("Un retrait rapproché par libellé réduit le solde le mois du retrait")
    void testTransferReducesRunningBalance() {
        PlacementProjectionInput p = placement("Livret", "1000", LocalDate.of(2026, 1, 1), "0", "0", null, null,
                null);
        PlacementEvolutionInput in = new PlacementEvolutionInput(p, List.of(),
                List.of(new BackgroundPlacement(p, bd("1000"))),
                List.of(new PlacementTransfer("livret", LocalDate.of(2026, 2, 15), bd("300"))),
                params(1, null, null, null));

        PlacementEvolution evolution = service.compute(in, false);

        // Le retrait de la fenêtre 2026-02 est appliqué à la fin de l'itération 2026-02, donc
        // reflété par le point daté 2026-03-01 (chaque point porte le solde de fin de mois écoulé).
        assertThat(evolution.points().get(2).date()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(evolution.points().get(2).corr()).isEqualByComparingTo("700");
    }

    @Test
    @DisplayName("Un placement en arrière-plan sous son seuil de déclenchement suspend le placement tracé")
    void testBackgroundPauseTriggersTracedPlacement() {
        PlacementProjectionInput traced = placement("Livret", "1000", LocalDate.of(2026, 1, 1), "100", "0",
                null, null, 1);
        PlacementProjectionInput watcher = new PlacementProjectionInput("w", "Watcher", bd("50"),
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false, null, null, bd("100"), null);
        PlacementEvolutionInput in = new PlacementEvolutionInput(traced, List.of(),
                List.of(new BackgroundPlacement(traced, bd("1000")), new BackgroundPlacement(watcher, bd("50"))),
                List.of(), params(1, null, null, null));

        PlacementEvolution evolution = service.compute(in, false);

        // Le watcher est sous son seuil dès le premier mois : la pause s'applique dès le mois suivant.
        assertThat(evolution.points().get(1).corr()).isEqualByComparingTo("1100");
        assertThat(evolution.points().get(2).corr()).isEqualByComparingTo("1100");
    }

    @Test
    @DisplayName("useConstantEuros déflate les projections mais jamais les valeurs réelles")
    void testConstantEurosDeflatesProjectionsOnly() {
        PlacementProjectionInput p = placement("Livret", "1000", LocalDate.of(2026, 1, 1), "0", "0", null, null,
                null);
        PlacementEvolutionInput in = new PlacementEvolutionInput(p,
                List.of(new PlacementHistoryPoint(LocalDate.of(2026, 1, 1), bd("1000"))),
                List.of(new BackgroundPlacement(p, bd("1000"))), List.of(), params(1, bd("0.12"), null, null));

        PlacementEvolution evolution = service.compute(in, true);

        assertThat(evolution.points().get(0).real()).isEqualByComparingTo("1000");
        assertThat(evolution.points().get(12).corr().doubleValue())
                .isCloseTo(1000 * Math.pow(1.0 / 1.12, 1.0), within(0.5));
    }
}
