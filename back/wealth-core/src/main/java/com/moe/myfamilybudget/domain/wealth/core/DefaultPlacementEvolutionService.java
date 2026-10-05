package com.moe.myfamilybudget.domain.wealth.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolution;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionInput;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionParameters;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionService;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementHistoryPoint;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementProjectionInput;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementTransfer;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionInput.BackgroundPlacement;

/**
 * Moteur de chronologie d'un placement (RF-301, voir doc/architecture/05-domaine-patrimoine.md).
 *
 * <p>Construit un segment « réel » (valeurs saisies, triées par date) suivi de trois projections
 * mensuelles (pessimiste / corrigé / optimiste) qui repartent du dernier point réel connu — ou, à
 * défaut d'historique, du solde/date de référence du placement. Portage de
 * {@code buildPlacementTimeline} (view/js/calculations.js). Le déflateur « euros constants »
 * s'applique aux trois projections uniquement.
 *
 * <p>La pause automatique des versements est déléguée à {@link ContributionPauseRules} : comme le
 * placement tracé n'est qu'un des placements du budget, tous sont simulés en arrière-plan
 * (scénario corrigé uniquement), car n'importe lequel peut déclencher la pause. La trésorerie est
 * approximée à partir des seuls mouvements de placements (ce niveau de détail ne dispose pas des
 * revenus/charges du foyer).
 *
 * <p>Ne reçoit que {@link PlacementEvolutionInput} : aucun {@code BudgetDataModel}, aucun DTO.
 */
public class DefaultPlacementEvolutionService implements PlacementEvolutionService {

    @Override
    public PlacementEvolution compute(PlacementEvolutionInput input, boolean useConstantEuros) {
        PlacementProjectionInput placement = input.placement();
        PlacementEvolutionParameters parameters = input.parameters();
        List<PlacementTransfer> transfers = input.transfers();
        BigDecimal inflationRate = parameters.inflationRate();
        LocalDate today = parameters.today();

        List<PlacementHistoryPoint> history = new ArrayList<>(input.history());
        history.removeIf(h -> h.date() == null);
        history.sort(Comparator.comparing(PlacementHistoryPoint::date));

        LocalDate anchorDate;
        BigDecimal anchorValue;
        if (!history.isEmpty()) {
            PlacementHistoryPoint last = history.get(history.size() - 1);
            anchorDate = last.date();
            anchorValue = last.value();
        } else if (placement.balanceDate() != null) {
            anchorDate = placement.balanceDate();
            anchorValue = placement.initialBalance();
        } else {
            anchorDate = today;
            anchorValue = placement.initialBalance();
        }

        List<MutablePoint> points = new ArrayList<>();
        for (PlacementHistoryPoint h : history) {
            MutablePoint pt = new MutablePoint(h.date());
            pt.real = h.value();
            points.add(pt);
        }

        MutablePoint anchorPoint = points.stream()
                .filter(p -> p.date.equals(anchorDate))
                .findFirst()
                .orElse(null);
        if (anchorPoint == null) {
            anchorPoint = new MutablePoint(anchorDate);
            anchorPoint.real = anchorValue;
            points.add(anchorPoint);
            points.sort(Comparator.comparing(p -> p.date));
        }
        anchorPoint.pess = anchorValue;
        anchorPoint.corr = anchorValue;
        anchorPoint.opti = anchorValue;

        BigDecimal runningPess = anchorValue;
        BigDecimal runningCorr = anchorValue;
        BigDecimal runningOpti = anchorValue;
        BigDecimal monthlyContrib = placement.monthlyContribution();
        YearMonth monthlyFrom = placement.contributionFrom() != null
                ? placement.contributionFrom() : YearMonth.from(anchorDate);
        YearMonth monthlyUntil = placement.contributionUntil();

        List<BackgroundPlacement> background = input.background();
        List<PlacementProjectionInput> allPlacements = background.stream()
                .map(BackgroundPlacement::placement).toList();
        ContributionPauseRules pauseRules = new ContributionPauseRules(
                allPlacements, parameters.cashCeiling(), parameters.cashAlertThreshold());
        PauseState pauseState = pauseRules.initialState();

        Map<String, BigDecimal> backgroundCorr = new HashMap<>();
        Map<String, YearMonth> backgroundFrom = new HashMap<>();
        Map<String, YearMonth> backgroundUntil = new HashMap<>();
        for (BackgroundPlacement bp : background) {
            PlacementProjectionInput p = bp.placement();
            if (p.label() == null) continue;
            backgroundCorr.put(p.label(), bp.latestKnownBalance());
            backgroundFrom.put(p.label(), p.contributionFrom() != null
                    ? p.contributionFrom() : YearMonth.from(anchorDate));
            backgroundUntil.put(p.label(), p.contributionUntil());
        }

        BigDecimal treasuryBalance = parameters.startBalance();

        YearMonth cursor = YearMonth.from(anchorDate);
        YearMonth end = cursor.plusYears(parameters.horizonYears());
        int elapsedMonths = 0;
        while (cursor.isBefore(end)) {
            boolean withinContribWindow = !cursor.isBefore(monthlyFrom)
                    && (monthlyUntil == null || !cursor.isAfter(monthlyUntil));
            boolean isPaused = pauseRules.isPaused(placement, pauseState);
            BigDecimal effectiveContrib = withinContribWindow && !isPaused ? monthlyContrib : BigDecimal.ZERO;
            YearMonth cursorFinal = cursor;
            BigDecimal withdrawn = placement.label() == null ? BigDecimal.ZERO : withdrawnIn(
                    transfers, placement.label(), cursorFinal);

            runningPess = applyMonth(runningPess, placement.pessimisticRate(), effectiveContrib, withdrawn);
            runningCorr = applyMonth(runningCorr, placement.expectedRate(), effectiveContrib, withdrawn);
            runningOpti = applyMonth(runningOpti, placement.optimisticRate(), effectiveContrib, withdrawn);

            // Avance tous les placements en arrière-plan (scénario corrigé) puis réévalue le niveau
            // de pause pour le mois suivant, comme le moteur JS en fin de mois.
            BigDecimal totalContribThisMonth = BigDecimal.ZERO;
            BigDecimal totalWithdrawnThisMonth = BigDecimal.ZERO;
            for (PlacementProjectionInput p : allPlacements) {
                if (p.label() == null) continue;
                BigDecimal cur = backgroundCorr.get(p.label());
                YearMonth pFrom = backgroundFrom.get(p.label());
                YearMonth pUntil = backgroundUntil.get(p.label());
                boolean pWithin = !cursor.isBefore(pFrom) && (pUntil == null || !cursor.isAfter(pUntil));
                BigDecimal pContrib = pWithin && !pauseRules.isPaused(p, pauseState)
                        ? p.monthlyContribution() : BigDecimal.ZERO;
                BigDecimal pWithdrawn = withdrawnIn(transfers, p.label(), cursorFinal);
                backgroundCorr.put(p.label(), applyMonth(cur, p.expectedRate(), pContrib, pWithdrawn));
                totalContribThisMonth = totalContribThisMonth.add(pContrib);
                totalWithdrawnThisMonth = totalWithdrawnThisMonth.add(pWithdrawn);
            }

            treasuryBalance = treasuryBalance.subtract(totalContribThisMonth).add(totalWithdrawnThisMonth);
            pauseState = pauseRules.next(pauseState, treasuryBalance,
                    idx -> backgroundCorr.get(allPlacements.get(idx).label()));

            cursor = cursor.plusMonths(1);
            elapsedMonths++;
            double deflatorVal = useConstantEuros
                    ? Math.pow(1.0 / (1.0 + inflationRate.doubleValue()), elapsedMonths / 12.0)
                    : 1.0;
            BigDecimal deflator = BigDecimal.valueOf(deflatorVal);

            MutablePoint pt = new MutablePoint(cursor.atDay(1));
            pt.pess = runningPess.multiply(deflator);
            pt.corr = runningCorr.multiply(deflator);
            pt.opti = runningOpti.multiply(deflator);
            points.add(pt);
        }

        points.sort(Comparator.comparing(p -> p.date));

        return new PlacementEvolution(
                placement.id(),
                anchorPoint.date,
                today,
                points.stream().map(p -> new PlacementEvolution.Point(p.date, p.real, p.pess, p.corr, p.opti)).toList());
    }

    private static BigDecimal withdrawnIn(List<PlacementTransfer> transfers, String label, YearMonth month) {
        return transfers.stream()
                .filter(t -> label.equalsIgnoreCase(t.placementLabel())
                        && t.date() != null && YearMonth.from(t.date()).equals(month))
                .map(PlacementTransfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal applyMonth(
            BigDecimal running, BigDecimal annualRate, BigDecimal monthlyContrib, BigDecimal withdrawn) {
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), MathContext.DECIMAL64);
        BigDecimal next = running.multiply(BigDecimal.ONE.add(monthlyRate)).add(monthlyContrib).subtract(withdrawn);
        return next.max(BigDecimal.ZERO);
    }

    private static final class MutablePoint {
        private final LocalDate date;
        private BigDecimal real;
        private BigDecimal pess;
        private BigDecimal corr;
        private BigDecimal opti;

        private MutablePoint(LocalDate date) {
            this.date = date;
        }
    }
}
