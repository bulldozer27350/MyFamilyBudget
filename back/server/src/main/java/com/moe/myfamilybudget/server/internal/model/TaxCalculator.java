package com.moe.myfamilybudget.server.internal.model;

import com.moe.myfamilybudget.server.internal.calculation.AnnualTaxIncome;
import com.moe.myfamilybudget.server.internal.calculation.AnnualTaxableRetirementIncome;
import com.moe.myfamilybudget.server.internal.calculation.AnnualVariableIncome;
import com.moe.myfamilybudget.server.internal.calculation.TaxActualOverride;
import com.moe.myfamilybudget.server.internal.calculation.TaxBracket;
import com.moe.myfamilybudget.server.internal.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.server.internal.calculation.TaxRateOverride;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Moteur de calcul domaine métier pour la fiscalité (Impôts).
 * Manipule exclusivement {@link TaxCalculationInput} et les records du contrat Fiscalité
 * (aucun modèle persistant) et utilise BigDecimal avec RoundingMode.HALF_UP.
 */
public class TaxCalculator {

    /**
     * Calcule les parts fiscales à partir du contrat Fiscalité, qui transporte uniquement
     * les années de naissance des enfants et non les modèles de persistance.
     */
    private static double partsForCalculationYear(List<Integer> childBirthYears, int exitAge, int year) {
        if (childBirthYears == null || childBirthYears.isEmpty()) {
            return 2.0;
        }
        long attached = childBirthYears.stream()
                .filter(birthYear -> birthYear != null && (year - birthYear) < exitAge)
                .count();
        double parts = 2.0;
        for (int i = 1; i <= attached; i++) {
            parts += (i <= 2) ? 0.5 : 1.0;
        }
        return parts;
    }

    /**
     * Calcule les projections fiscales à partir du contrat Fiscalité.
     *
     * <p>Unique point d'entrée du moteur (RF-203) : il ne reçoit que le contrat
     * {@link TaxCalculationInput}, construit en amont par {@code TaxInputFactory}.
     */
    public static List<TaxYearlyModel> computeTaxYearly(TaxCalculationInput input) {
        if (input == null || input.period() == null || input.household() == null) {
            return List.of();
        }

        Map<Integer, BigDecimal> incomes = input.incomes() == null
                ? Map.of()
                : input.incomes().stream().collect(java.util.stream.Collectors.toMap(
                        AnnualTaxIncome::year, AnnualTaxIncome::amount, BigDecimal::add));
        Map<Integer, BigDecimal> variableIncomes = input.variableIncomes() == null
                ? Map.of()
                : input.variableIncomes().stream().collect(java.util.stream.Collectors.toMap(
                        AnnualVariableIncome::year, AnnualVariableIncome::taxableAmount, BigDecimal::add));
        Map<Integer, BigDecimal> retirementIncomes = input.retirementIncome() == null
                ? Map.of()
                : input.retirementIncome().stream().collect(java.util.stream.Collectors.toMap(
                        AnnualTaxableRetirementIncome::year, AnnualTaxableRetirementIncome::amount, BigDecimal::add));
        Map<Integer, BigDecimal> rateOverrides = input.rateOverrides() == null
                ? Map.of()
                : input.rateOverrides().stream().collect(java.util.stream.Collectors.toMap(
                        TaxRateOverride::year, TaxRateOverride::rate, (a, b) -> b));
        Map<Integer, BigDecimal> actualOverrides = input.actualOverrides() == null
                ? Map.of()
                : input.actualOverrides().stream().collect(java.util.stream.Collectors.toMap(
                        TaxActualOverride::year, TaxActualOverride::amount, (a, b) -> b));

        List<TaxYearlyModel> result = new ArrayList<>();
        for (int year = input.period().startYear(); year <= input.period().endYear(); year++) {
            BigDecimal grossIncome = incomes.getOrDefault(year, BigDecimal.ZERO)
                    .add(variableIncomes.getOrDefault(year, BigDecimal.ZERO))
                    .add(retirementIncomes.getOrDefault(year, BigDecimal.ZERO));
            BigDecimal abattement = input.household().taxAbattement() == null
                    ? BigDecimal.ZERO : input.household().taxAbattement();
            BigDecimal taxableIncome = grossIncome.multiply(BigDecimal.ONE.subtract(abattement))
                    .setScale(2, RoundingMode.HALF_UP);

            double parts = partsForCalculationYear(
                    input.childBirthYears() == null ? List.of() : input.childBirthYears(),
                    input.household().childExitAge(), year);
            BigDecimal taxForecast = BigDecimal.ZERO;
            if (parts > 0) {
                BigDecimal quotient = taxableIncome.divide(BigDecimal.valueOf(parts), 10, RoundingMode.HALF_UP);
                taxForecast = taxForCalculationPart(quotient, input.brackets())
                        .multiply(BigDecimal.valueOf(parts)).setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal taxActual = actualOverrides.getOrDefault(year, taxForecast)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal rateForecast = grossIncome.compareTo(BigDecimal.ZERO) > 0
                    ? taxForecast.divide(grossIncome, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal ratePAS = rateOverrides.getOrDefault(year, rateForecast);
            BigDecimal withheld = ratePAS.multiply(grossIncome).setScale(2, RoundingMode.HALF_UP);

            result.add(new TaxYearlyModel(
                    year, parts, taxableIncome, taxForecast, taxActual, ratePAS, withheld));
        }
        return result;
    }

    private static BigDecimal taxForCalculationPart(BigDecimal q, List<TaxBracket> brackets) {
        if (q == null || q.compareTo(BigDecimal.ZERO) <= 0 || brackets == null || brackets.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<TaxBracket> sorted = new ArrayList<>(brackets);
        sorted.sort((a, b) -> {
            if (a.upTo() == null) return 1;
            if (b.upTo() == null) return -1;
            return a.upTo().compareTo(b.upTo());
        });
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal previous = BigDecimal.ZERO;
        for (TaxBracket bracket : sorted) {
            if (q.compareTo(previous) > 0) {
                BigDecimal upper = bracket.upTo() == null ? q : q.min(bracket.upTo());
                BigDecimal rate = bracket.rate() == null ? BigDecimal.ZERO : bracket.rate();
                tax = tax.add(upper.subtract(previous).multiply(rate));
            }
            if (bracket.upTo() != null) {
                previous = bracket.upTo();
                if (q.compareTo(bracket.upTo()) <= 0) break;
            } else {
                break;
            }
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Sélectionne la fenêtre de prévisualisation (jusqu'à 6 ans à partir de l'année courante ou les 6 dernières).
     */
    public static List<TaxYearlyModel> buildTaxPreview(List<TaxYearlyModel> taxYearly, int currentYear) {
        if (taxYearly == null || taxYearly.isEmpty()) {
            return List.of();
        }
        List<TaxYearlyModel> futureTax = taxYearly.stream()
                .filter(t -> t.year() >= currentYear)
                .toList();

        if (!futureTax.isEmpty()) {
            return futureTax.subList(0, Math.min(6, futureTax.size()));
        } else {
            int start = Math.max(0, taxYearly.size() - 6);
            return taxYearly.subList(start, taxYearly.size());
        }
    }
}
