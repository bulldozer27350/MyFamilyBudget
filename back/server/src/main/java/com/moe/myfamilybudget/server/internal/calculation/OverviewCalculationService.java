package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.CashflowYearModel;
import com.moe.myfamilybudget.server.internal.model.OverviewResultModel;
import com.moe.myfamilybudget.server.internal.model.RetirementProjection;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;
import com.moe.myfamilybudget.server.internal.model.TripleAmountModel;

/**
 * Calcul pur de l'aperçu financier global (Overview) : composition et transformation de
 * projections déjà calculées par les autres domaines (Trésorerie, Patrimoine, Retraite,
 * Fiscalité, Immobilier).
 *
 * <p>RF-901 (voir doc/architecture/11-domaine-overview.md) : ce moteur ne dépend plus d'aucun
 * modèle persistant ({@code BudgetDataModel}, {@code IncomeModel}, etc.) ni d'aucune logique de
 * recalcul. Il consomme exclusivement {@link OverviewInput} et produit un {@link OverviewResultModel}.
 */
@Component
public class OverviewCalculationService {

    public OverviewResultModel computeOverview(OverviewInput input) {
        Objects.requireNonNull(input, "input");

        OverviewParameters parameters = input.parameters();
        TreasuryProjection treasury = input.treasuryProjection();
        PatrimoineProjection patrimoine = input.patrimoineProjection();
        RetirementProjection retirement = input.retirementProjection();
        RealEstateProjection realEstate = input.realEstateProjection();

        List<Integer> years = treasury.years();
        int retireYear = parameters.retireYear();
        boolean useConstantEuros = parameters.useConstantEuros();
        BigDecimal inflationRate = parameters.inflationRate();

        int startYear = years.isEmpty() ? retireYear : years.get(0);

        BigDecimal retireDeflator;
        if (useConstantEuros) {
            double deflatorVal = Math.pow(1.0 / (1.0 + inflationRate.doubleValue()), retireYear - startYear);
            retireDeflator = BigDecimal.valueOf(deflatorVal);
        } else {
            retireDeflator = BigDecimal.ONE;
        }

        // 1. Patrimoine financier mobilisable à la retraite
        int idx = years.indexOf(retireYear);
        TripleAmountModel financialOnlyPatrimoine = patrimoine.financialOnlyPatrimoine(idx, retireDeflator);

        // 2. Immobilier à la retraite
        BigDecimal realEstateAtRetire = realEstate.nominalValueAtRetire().multiply(retireDeflator);

        // 3. Patrimoine total à la retraite
        TripleAmountModel retirePatrimoine = new TripleAmountModel(
            financialOnlyPatrimoine.pess().add(realEstateAtRetire),
            financialOnlyPatrimoine.corr().add(realEstateAtRetire),
            financialOnlyPatrimoine.opti().add(realEstateAtRetire)
        );

        // 4. Charges mensuelles à la retraite
        Optional<CashflowYearModel> retireYearData = treasury.cashflow().stream()
            .filter(c -> c.year() == retireYear)
            .findFirst();
        BigDecimal retireCharges = retireYearData.map(c -> c.charges()
            .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
            .multiply(retireDeflator))
            .orElse(BigDecimal.ZERO);

        // 5. Pensions de retraite mensuelles totales
        BigDecimal totalPensions = BigDecimal.ZERO;
        for (RetirementProjectionModel proj : retirement.people()) {
            totalPensions = totalPensions.add(proj.pensionTotaleMensuelle());
        }
        totalPensions = totalPensions.multiply(retireDeflator);

        // 6. Patrimoine financier actuel
        BigDecimal patrimoineActuel = patrimoine.currentTotalBalance();

        // 7. Flux net actuel (année civile courante de référence)
        int currentCalendarYear = parameters.currentYear();
        BigDecimal fluxNetActuel = treasury.cashflow().stream()
            .filter(c -> c.year() == currentCalendarYear)
            .findFirst()
            .map(CashflowYearModel::net)
            .orElse(treasury.cashflow().isEmpty() ? BigDecimal.ZERO : treasury.cashflow().get(0).net());

        return new OverviewResultModel(
            years,
            treasury.cashflow(),
            patrimoine.projections(),
            useConstantEuros,
            retireYear,
            parameters.pivotBalance(),
            patrimoineActuel,
            fluxNetActuel,
            retireCharges,
            totalPensions,
            retirePatrimoine,
            fourPercentRule(retirePatrimoine),
            fourPercentRule(financialOnlyPatrimoine)
        );
    }

    private TripleAmountModel fourPercentRule(TripleAmountModel patrimoine) {
        BigDecimal factor = new BigDecimal("0.04").divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        return new TripleAmountModel(
            patrimoine.pess().multiply(factor),
            patrimoine.corr().multiply(factor),
            patrimoine.opti().multiply(factor)
        );
    }
}
