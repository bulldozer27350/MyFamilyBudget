package com.moe.myfamilybudget.server.internal.factory;

import java.math.BigDecimal;
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
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxCalculator;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;

/**
 * Construit un {@link TaxCalculationInput} à partir de {@link BudgetDataModel} (RF-200, voir
 * doc/architecture/04-domaine-fiscalite.md).
 *
 * <p>Cette classe porte volontairement la dépendance à {@code BudgetDataModel} que le domaine
 * Fiscalité ne doit pas avoir (voir doc/architecture/00-principes.md) : c'est le rôle d'une
 * Factory de frontière, pas d'un moteur de calcul — elle n'est donc pas placée dans le package
 * {@code internal.calculation}, gardé par le garde-fou ArchUnit de RF-001
 * ({@code CalculationDependenciesArchTest}).
 *
 * <p><b>Version 1 (« large », voir 04-domaine-fiscalite.md § « Migration en deux étapes
 * logiques »).</b> Purement additif : aucun appelant existant n'est modifié, {@link TaxCalculator}
 * reste la seule voie utilisée par {@code ImpotsServiceImpl}. Cette Factory réutilise
 * volontairement les méthodes statiques déjà publiques de {@code TaxCalculator}
 * ({@code findEarliestYear}, {@code pensionIncomeRows}, {@code incomeAnnualForYear},
 * {@code variableIncomeDetailForYear}) plutôt que de dupliquer leur logique : la période de
 * simulation et la pension imposable proviennent donc encore, in fine, de
 * {@code BudgetDataModel}. RF-202 remplacera la pension par une projection issue de
 * {@code RetirementCalculationService} (RF-102) et fera calculer la période en amont, côté
 * application, sans changer le contrat {@link TaxCalculationInput} lui-même.
 */
public final class TaxInputFactory {

    private TaxInputFactory() {
    }

    public static TaxCalculationInput from(BudgetDataModel data) {
        Objects.requireNonNull(data, "data");
        SettingsModel settings = data.getEffectiveSettings();

        int birthYear = settings.getEffectiveBirthYear();
        int retireAge = settings.getEffectiveRetireAge();
        int retireYear = birthYear + retireAge;
        int startYear = TaxCalculator.findEarliestYear(data);
        int wantedEnd = birthYear + settings.getEffectiveSimulateUntilAge();
        int endYear = Math.max(retireYear + 3, wantedEnd);

        List<Integer> years = new ArrayList<>();
        for (int y = startYear; y <= endYear; y++) {
            years.add(y);
        }
        int lastYear = years.isEmpty() ? retireYear : years.get(years.size() - 1);

        List<IncomeModel> regularIncomes = data.getEffectiveIncomes();
        List<IncomeModel> pensionRows = TaxCalculator.pensionIncomeRows(data, retireYear, lastYear);

        List<AnnualTaxIncome> incomes = new ArrayList<>();
        List<AnnualVariableIncome> variableIncomes = new ArrayList<>();
        List<AnnualTaxableRetirementIncome> retirementIncome = new ArrayList<>();
        for (int year : years) {
            incomes.add(new AnnualTaxIncome(year, sumAnnual(regularIncomes, year)));
            variableIncomes.add(new AnnualVariableIncome(year,
                    TaxCalculator.variableIncomeDetailForYear(data, year).taxable()));
            retirementIncome.add(new AnnualTaxableRetirementIncome(year, sumAnnual(pensionRows, year)));
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
                new TaxSimulationPeriod(startYear, endYear),
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
