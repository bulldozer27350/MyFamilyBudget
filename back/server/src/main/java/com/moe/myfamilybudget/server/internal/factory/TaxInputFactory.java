package com.moe.myfamilybudget.server.internal.factory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.moe.myfamilybudget.server.internal.calculation.AnnualTaxIncome;
import com.moe.myfamilybudget.server.internal.calculation.AnnualTaxableRetirementIncome;
import com.moe.myfamilybudget.server.internal.calculation.AnnualVariableIncome;
import com.moe.myfamilybudget.server.internal.calculation.TaxActualOverride;
import com.moe.myfamilybudget.server.internal.calculation.TaxBracket;
import com.moe.myfamilybudget.server.internal.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.server.internal.calculation.TaxHouseholdParameters;
import com.moe.myfamilybudget.server.internal.calculation.TaxRateOverride;
import com.moe.myfamilybudget.server.internal.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.RetirementProjection;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxCalculator;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;

/**
 * Construit un {@link TaxCalculationInput} à partir de {@link BudgetDataModel} (RF-200, réduit
 * par RF-202 ; voir doc/architecture/04-domaine-fiscalite.md).
 *
 * <p>Cette classe porte volontairement la dépendance à {@code BudgetDataModel} que le domaine
 * Fiscalité ne doit pas avoir (voir doc/architecture/00-principes.md) : c'est le rôle d'une
 * Factory de frontière, pas d'un moteur de calcul — elle n'est donc pas placée dans le package
 * {@code internal.calculation}, gardé par le garde-fou ArchUnit de RF-001
 * ({@code CalculationDependenciesArchTest}).
 *
 * <p><b>Version 2 (RF-202).</b> Deux couplages ont disparu de {@link TaxCalculator} :
 * <ul>
 *   <li>la période de simulation est fournie explicitement par l'appelant (voir
 *       {@link TaxSimulationPeriodResolver}) au lieu d'être déduite ici de {@code findEarliestYear} ;</li>
 *   <li>la pension imposable provient de {@link RetirementProjection} (produite par
 *       {@code RetirementCalculationService}, RF-102) et non plus d'un calcul de pension interne au
 *       moteur fiscal.</li>
 * </ul>
 * {@link TaxCalculationInput} ne transporte ni charges, ni placements, ni opérations ponctuelles,
 * ni virements, ni import bancaire, ni {@code pivotDate}/{@code inflationRate} : aucun champ
 * supplémentaire n'était à retirer du contrat.
 */
public final class TaxInputFactory {

    private TaxInputFactory() {
    }

    public static TaxCalculationInput from(
            BudgetDataModel data, TaxSimulationPeriod period, RetirementProjection retirementProjection) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(period, "period");
        SettingsModel settings = data.getEffectiveSettings();

        int birthYear = settings.getEffectiveBirthYear();
        int retireAge = settings.getEffectiveRetireAge();
        int retireYear = birthYear + retireAge;
        BigDecimal inflationRate = settings.getEffectiveInflationRate();
        List<BigDecimal> monthlyPensions = retirementProjection == null
                ? List.of()
                : retirementProjection.people().stream()
                        .map(RetirementProjectionModel::pensionTotaleMensuelle)
                        .filter(monthly -> monthly != null && monthly.compareTo(BigDecimal.ZERO) > 0)
                        .toList();

        List<IncomeModel> regularIncomes = data.getEffectiveIncomes();

        List<AnnualTaxIncome> incomes = new ArrayList<>();
        List<AnnualVariableIncome> variableIncomes = new ArrayList<>();
        List<AnnualTaxableRetirementIncome> retirementIncome = new ArrayList<>();
        for (int year = period.startYear(); year <= period.endYear(); year++) {
            incomes.add(new AnnualTaxIncome(year, sumAnnual(regularIncomes, year)));
            variableIncomes.add(new AnnualVariableIncome(year,
                    TaxCalculator.variableIncomeDetailForYear(data, year).taxable()));
            retirementIncome.add(new AnnualTaxableRetirementIncome(year,
                    annualPension(monthlyPensions, retireYear, period.endYear(), inflationRate, year)));
        }

        List<Integer> childBirthYears = data.getEffectiveTaxChildren().stream()
                .map(TaxChildModel::birthYear)
                .filter(Objects::nonNull)
                .toList();

        List<TaxBracket> brackets = data.getEffectiveTaxBrackets().stream()
                .map(TaxInputFactory::toBracket)
                .toList();

        List<TaxRateOverride> rateOverrides = data.getEffectiveTaxRateOverrides().stream()
                .filter(o -> o.year() != null)
                .map(o -> new TaxRateOverride(o.year(), o.rate()))
                .toList();

        List<TaxActualOverride> actualOverrides = data.getEffectiveTaxActualOverrides().stream()
                .filter(o -> o.year() != null)
                .map(o -> new TaxActualOverride(o.year(), o.amount()))
                .toList();

        return new TaxCalculationInput(
                period,
                new TaxHouseholdParameters(birthYear, retireAge, settings.getEffectiveChildExitAge(),
                        settings.getEffectiveTaxAbattement()),
                incomes,
                variableIncomes,
                childBirthYears,
                brackets,
                rateOverrides,
                actualOverrides,
                retirementIncome);
    }

    /**
     * Pension annuelle d'une année : pension mensuelle de départ indexée sur l'inflation depuis
     * l'année de départ à la retraite, versée 12 mois par an de {@code retireYear} à
     * {@code max(retireYear, lastYear)}, arrondie par personne (même règle que l'ancienne ligne de
     * revenu « pension auto »).
     */
    private static BigDecimal annualPension(
            List<BigDecimal> monthlyPensions, int retireYear, int lastYear, BigDecimal inflationRate, int year) {
        if (year < retireYear || year > Math.max(retireYear, lastYear)) {
            return BigDecimal.ZERO;
        }
        BigDecimal factor = BigDecimal.valueOf(Math.pow(1.0 + inflationRate.doubleValue(), year - retireYear));
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal monthly : monthlyPensions) {
            total = total.add(monthly.multiply(factor).multiply(BigDecimal.valueOf(12))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return total;
    }

    private static BigDecimal sumAnnual(List<IncomeModel> rows, int year) {
        BigDecimal total = BigDecimal.ZERO;
        for (IncomeModel row : rows) {
            total = total.add(TaxCalculator.incomeAnnualForYear(row, year));
        }
        return total;
    }

    private static TaxBracket toBracket(TaxBracketModel model) {
        return new TaxBracket(model.upTo(), model.getEffectiveRate());
    }
}
