package com.moe.myfamilybudget.domain.wealth.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


import com.moe.myfamilybudget.domain.wealth.calculation.AnnualCashflow;
import com.moe.myfamilybudget.domain.wealth.calculation.CashflowProjection;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionInput;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionParameters;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementProjectionInput;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementTransfer;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineYearModel;

/**
 * Moteur de projection patrimoniale annuelle (RF-301, voir doc/architecture/05-domaine-patrimoine.md).
 *
 * <p>Projette chaque placement année par année dans les trois scénarios (pessimiste / corrigé /
 * optimiste) puis agrège le total, éventuellement en euros constants. La décision de suspendre les
 * versements est déléguée à {@link ContributionPauseRules} ; ce moteur ne connaît que
 * {@link PatrimoineProjectionInput} (aucun {@code BudgetDataModel}, conformément au garde-fou
 * ArchUnit RF-001).
 *
 * <p>Comme avant RF-301, la pause est évaluée en fin d'année à partir d'une trésorerie
 * approximée : flux net annuel hors placements ({@link CashflowProjection}) - versements +
 * retraits de placements.
 */
public class DefaultPatrimoineProjectionService implements PatrimoineProjectionService {

    @Override
    public PatrimoineProjectionsModel compute(PatrimoineProjectionInput input, boolean useConstantEuros) {
        PatrimoineProjectionParameters parameters = Objects.requireNonNull(input.parameters(), "parameters");
        int startYear = parameters.startYear();
        BigDecimal inflationRate = parameters.inflationRate();

        List<Integer> years = new ArrayList<>();
        for (int y = startYear; y <= parameters.endYear(); y++) {
            years.add(y);
        }

        List<PlacementProjectionInput> placements = input.placements();
        int n = placements.size();

        Map<Integer, BigDecimal> netCashflowByYear = new HashMap<>();
        for (AnnualCashflow cashflow : input.cashflow().years()) {
            netCashflowByYear.merge(cashflow.year(), cashflow.netCashflow(), BigDecimal::add);
        }

        // Pour suspendre les versements d'une année selon l'état de fin d'année précédente, les
        // boucles sont : année en dehors, placement en dedans.
        ContributionPauseRules pauseRules = new ContributionPauseRules(
                placements, parameters.cashCeiling(), parameters.cashAlertThreshold());
        PauseState pauseState = pauseRules.initialState();
        BigDecimal treasuryBalance = parameters.startBalance();

        BigDecimal[] pessArr = new BigDecimal[n];
        BigDecimal[] corrArr = new BigDecimal[n];
        BigDecimal[] optiArr = new BigDecimal[n];
        int[] contributionFromYear = new int[n];
        Integer[] contributionUntilYear = new Integer[n];
        List<List<PatrimoineYearModel>> rowsPerPlacement = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PlacementProjectionInput p = placements.get(i);
            pessArr[i] = p.initialBalance();
            corrArr[i] = p.initialBalance();
            optiArr[i] = p.initialBalance();
            contributionFromYear[i] = p.contributionFrom() != null ? p.contributionFrom().getYear() : startYear;
            contributionUntilYear[i] = p.contributionUntil() != null ? p.contributionUntil().getYear() : null;
            rowsPerPlacement.add(new ArrayList<>());
        }

        for (int year : years) {
            BigDecimal totalContribThisYear = BigDecimal.ZERO;
            BigDecimal totalWithdrawThisYear = BigDecimal.ZERO;

            for (int i = 0; i < n; i++) {
                PlacementProjectionInput p = placements.get(i);
                BigDecimal withdraw = BigDecimal.ZERO;
                for (PlacementTransfer t : input.transfers()) {
                    if (p.label() != null && p.label().equalsIgnoreCase(t.placementLabel())
                            && t.date() != null && t.date().getYear() == year) {
                        withdraw = withdraw.add(t.amount());
                    }
                }

                boolean withinWindow = year >= contributionFromYear[i]
                        && (contributionUntilYear[i] == null || year <= contributionUntilYear[i]);
                BigDecimal annualContrib = withinWindow && !pauseRules.isPaused(p, pauseState)
                        ? p.monthlyContribution().multiply(BigDecimal.valueOf(12))
                        : BigDecimal.ZERO;

                pessArr[i] = pessArr[i].multiply(BigDecimal.ONE.add(p.pessimisticRate())).add(annualContrib).subtract(withdraw);
                corrArr[i] = corrArr[i].multiply(BigDecimal.ONE.add(p.expectedRate())).add(annualContrib).subtract(withdraw);
                optiArr[i] = optiArr[i].multiply(BigDecimal.ONE.add(p.optimisticRate())).add(annualContrib).subtract(withdraw);

                rowsPerPlacement.get(i).add(new PatrimoineYearModel(year, pessArr[i], corrArr[i], optiArr[i]));

                totalContribThisYear = totalContribThisYear.add(annualContrib);
                totalWithdrawThisYear = totalWithdrawThisYear.add(withdraw);
            }

            // La décision prise en fin d'année s'applique à l'année suivante (même décalage d'une
            // période que le moteur JS).
            BigDecimal annualNet = netCashflowByYear.getOrDefault(year, BigDecimal.ZERO)
                    .subtract(totalContribThisYear).add(totalWithdrawThisYear);
            treasuryBalance = treasuryBalance.add(annualNet);
            pauseState = pauseRules.next(pauseState, treasuryBalance, idx -> corrArr[idx]);
        }

        List<PatrimoinePerPlacementModel> perPlacement = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            perPlacement.add(new PatrimoinePerPlacementModel(placements.get(i).label(), rowsPerPlacement.get(i)));
        }

        List<PatrimoineYearModel> totals = new ArrayList<>();
        for (int idx = 0; idx < years.size(); idx++) {
            int year = years.get(idx);
            double deflatorVal = useConstantEuros
                    ? Math.pow(1.0 / (1.0 + inflationRate.doubleValue()), year - startYear)
                    : 1.0;
            BigDecimal deflator = BigDecimal.valueOf(deflatorVal);

            BigDecimal totalPess = BigDecimal.ZERO;
            BigDecimal totalCorr = BigDecimal.ZERO;
            BigDecimal totalOpti = BigDecimal.ZERO;
            for (PatrimoinePerPlacementModel pp : perPlacement) {
                PatrimoineYearModel row = pp.rows().get(idx);
                totalPess = totalPess.add(row.pess());
                totalCorr = totalCorr.add(row.corr());
                totalOpti = totalOpti.add(row.opti());
            }
            totals.add(new PatrimoineYearModel(year,
                    totalPess.multiply(deflator), totalCorr.multiply(deflator), totalOpti.multiply(deflator)));
        }

        return new PatrimoineProjectionsModel(perPlacement, totals);
    }
}
