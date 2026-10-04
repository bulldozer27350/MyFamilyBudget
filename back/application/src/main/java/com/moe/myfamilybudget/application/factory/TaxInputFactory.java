package com.moe.myfamilybudget.application.factory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.tax.calculation.AnnualTaxIncome;
import com.moe.myfamilybudget.domain.retirement.calculation.AnnualTaxableRetirementIncome;
import com.moe.myfamilybudget.domain.tax.calculation.AnnualVariableIncome;
import com.moe.myfamilybudget.domain.tax.calculation.TaxActualOverride;
import com.moe.myfamilybudget.domain.tax.calculation.TaxBracket;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.domain.tax.calculation.TaxHouseholdParameters;
import com.moe.myfamilybudget.domain.tax.calculation.TaxRateOverride;
import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;

/**
 * Construit un {@link TaxCalculationInput} à partir de fragments lus chez leurs propriétaires
 * (RF-200, réduit par RF-202 ; voir doc/architecture/04-domaine-fiscalite.md). SILO-111 : cette
 * Factory ne connaît plus {@code BudgetDataModel} ; l'appelant lui fournit des {@link Sources}.
 *
 * <p>C'est le rôle d'une Factory de frontière, pas d'un moteur de calcul — elle n'est donc pas
 * placée dans le package {@code internal.calculation}, gardé par le garde-fou ArchUnit de RF-001
 * ({@code CalculationDependenciesArchTest}).
 *
 * <p><b>Version 2 (RF-202).</b> Deux couplages ont disparu de {@code TaxCalculator} :
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

    private static final Logger LOG = LoggerFactory.getLogger(TaxInputFactory.class);

    private TaxInputFactory() {
    }

    /**
     * Fragments nécessaires au calcul fiscal (SILO-111). Les listes absentes sont lues comme vides,
     * une inflation absente vaut zéro ; {@code retirementSettings} et {@code taxSettings} sont
     * obligatoires. Les tranches sont prises telles quelles : l'appelant applique le barème par défaut
     * si la configuration est vide (voir {@link TaxBracketDefaults}).
     */
    public record Sources(
            RetirementSettingsModel retirementSettings,
            TaxSettingsModel taxSettings,
            BigDecimal inflationRate,
            List<IncomeModel> incomes,
            List<VariableIncomeModel> variableIncomes,
            List<VariableOverrideModel> variableOverrides,
            List<TaxChildModel> taxChildren,
            List<TaxBracketModel> taxBrackets,
            List<TaxRateOverrideModel> taxRateOverrides,
            List<TaxActualOverrideModel> taxActualOverrides) {
    }

    public static TaxCalculationInput from(
            Sources sources, TaxSimulationPeriod period, RetirementProjection retirementProjection) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(period, "period");
        RetirementSettingsModel retirementSettings = sources.retirementSettings();
        TaxSettingsModel taxSettings = sources.taxSettings();

        int birthYear = retirementSettings.getEffectiveBirthYear();
        int retireAge = retirementSettings.getEffectiveRetireAge();
        int retireYear = birthYear + retireAge;
        BigDecimal inflationRate = sources.inflationRate() != null ? sources.inflationRate() : BigDecimal.ZERO;
        List<BigDecimal> monthlyPensions = retirementProjection == null
                ? List.of()
                : retirementProjection.people().stream()
                        .map(RetirementProjectionModel::pensionTotaleMensuelle)
                        .filter(monthly -> monthly != null && monthly.compareTo(BigDecimal.ZERO) > 0)
                        .toList();

        List<IncomeModel> regularIncomes = orEmpty(sources.incomes());

        List<AnnualTaxIncome> incomes = new ArrayList<>();
        List<AnnualVariableIncome> variableIncomes = new ArrayList<>();
        List<AnnualTaxableRetirementIncome> retirementIncome = new ArrayList<>();
        for (int year = period.startYear(); year <= period.endYear(); year++) {
            incomes.add(new AnnualTaxIncome(year, sumAnnual(regularIncomes, year)));
            variableIncomes.add(new AnnualVariableIncome(year,
                    variableTaxableForYear(regularIncomes, orEmpty(sources.variableIncomes()),
                            orEmpty(sources.variableOverrides()), year)));
            retirementIncome.add(new AnnualTaxableRetirementIncome(year,
                    annualPension(monthlyPensions, retireYear, period.endYear(), inflationRate, year)));
        }

        List<Integer> childBirthYears = orEmpty(sources.taxChildren()).stream()
                .map(TaxChildModel::birthYear)
                .filter(Objects::nonNull)
                .toList();

        List<TaxBracket> brackets = orEmpty(sources.taxBrackets()).stream()
                .map(TaxInputFactory::toBracket)
                .toList();

        List<TaxRateOverride> rateOverrides = orEmpty(sources.taxRateOverrides()).stream()
                .filter(o -> o.year() != null)
                .map(o -> new TaxRateOverride(o.year(), o.rate()))
                .toList();

        List<TaxActualOverride> actualOverrides = orEmpty(sources.taxActualOverrides()).stream()
                .filter(o -> o.year() != null)
                .map(o -> new TaxActualOverride(o.year(), o.amount()))
                .toList();

        return new TaxCalculationInput(
                period,
                new TaxHouseholdParameters(birthYear, retireAge, taxSettings.getEffectiveChildExitAge(),
                        taxSettings.getEffectiveTaxAbattement()),
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

    /**
     * Part imposable des revenus variables d'une année. Logique déplacée ici depuis le moteur
     * fiscal par RF-203 : projeter les modèles persistants est le rôle de la Factory.
     */
    private static BigDecimal variableTaxableForYear(
            List<IncomeModel> incomes, List<VariableIncomeModel> variableIncomes,
            List<VariableOverrideModel> variableOverrides, int year) {
        BigDecimal taxable = BigDecimal.ZERO;

        for (VariableIncomeModel v : variableIncomes) {
            if (v.startYear() != null && year < v.startYear()) continue;
            if (v.endYear() != null && year > v.endYear()) continue;

            Optional<IncomeModel> refRow = incomes.stream()
                    .filter(r -> v.refIncomeLabel() != null && v.refIncomeLabel().equalsIgnoreCase(r.label()))
                    .findFirst();
            BigDecimal refAnnual = refRow.map(r -> incomeAnnualForYear(r, year)).orElse(BigDecimal.ZERO);
            BigDecimal forecast = refAnnual.multiply(v.getEffectiveRate());

            Optional<VariableOverrideModel> override = variableOverrides.stream()
                    .filter(o -> v.label() != null && v.label().equalsIgnoreCase(o.label())
                            && o.year() != null && o.year() == year)
                    .findFirst();

            BigDecimal amount = override.map(VariableOverrideModel::getEffectiveAmount).orElse(forecast);

            boolean isTaxable;
            if (override.isPresent() && "Non".equalsIgnoreCase(override.get().taxable())) {
                isTaxable = false;
            } else if (override.isPresent() && "Oui".equalsIgnoreCase(override.get().taxable())) {
                isTaxable = true;
            } else {
                isTaxable = !"Non".equalsIgnoreCase(v.taxable());
            }

            if (isTaxable) {
                taxable = taxable.add(amount);
            }
        }
        return taxable.setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    private static BigDecimal incomeAnnualForYear(IncomeModel row, int year) {
        if (row == null || row.getEffectiveMonthly().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Integer startYear = yearOf(row.start());
        if (startYear == null) startYear = year;

        int yearsElapsed = Math.max(0, year - startYear);
        double factor = Math.pow(1.0 + row.getEffectiveGrowthRate().doubleValue(), yearsElapsed);
        BigDecimal effectiveMonthly = row.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));

        int monthsActive = monthsActiveInYear(row.start(), row.end(), year);
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActive)).setScale(2, RoundingMode.HALF_UP);
    }

    private static int monthsActiveInYear(String startISO, String endISO, int year) {
        LocalDate start = parseDate(startISO);
        LocalDate end = parseDate(endISO);
        if (start == null || end == null) return 0;

        LocalDate yStart = LocalDate.of(year, 1, 1);
        LocalDate yEnd = LocalDate.of(year, 12, 31);

        LocalDate s = start.isAfter(yStart) ? start : yStart;
        LocalDate e = end.isBefore(yEnd) ? end : yEnd;

        if (e.isBefore(s)) return 0;
        return (e.getYear() - s.getYear()) * 12 + (e.getMonthValue() - s.getMonthValue()) + 1;
    }

    private static Integer yearOf(String dateISO) {
        LocalDate d = parseDate(dateISO);
        return d != null ? d.getYear() : null;
    }

    private static LocalDate parseDate(String dateISO) {
        if (dateISO == null || dateISO.isBlank()) return null;
        try {
            if (dateISO.length() == 7) {
                return YearMonth.parse(dateISO).atDay(1);
            }
            return LocalDate.parse(dateISO.substring(0, 10));
        } catch (Exception e) {
            LOG.warn("Date ISO illisible dans un calcul fiscal, ignorée : '{}'", dateISO, e);
            return null;
        }
    }

    private static BigDecimal sumAnnual(List<IncomeModel> rows, int year) {
        BigDecimal total = BigDecimal.ZERO;
        for (IncomeModel row : rows) {
            total = total.add(incomeAnnualForYear(row, year));
        }
        return total;
    }

    private static TaxBracket toBracket(TaxBracketModel model) {
        return new TaxBracket(model.upTo(), model.getEffectiveRate());
    }
}
