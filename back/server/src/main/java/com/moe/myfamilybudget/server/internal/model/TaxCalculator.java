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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moteur de calcul domaine métier pour la fiscalité (Impôts).
 * Manipule exclusivement les records du domaine interne et utilise BigDecimal avec RoundingMode.HALF_UP.
 */
public class TaxCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(TaxCalculator.class);

    /**
     * Calcule le nombre de parts fiscales pour une année donnée.
     * Base = 2.0 parts (couple marié/pacsé).
     * Les 2 premiers enfants rattachés (< exitAge) ajoutent 0.5 part chacun.
     * Les enfants rattachés suivants ajoutent 1.0 part chacun.
     */
    public static double partsForYear(List<TaxChildModel> children, int exitAge, int year) {
        if (children == null || children.isEmpty()) {
            return 2.0;
        }
        long attached = children.stream()
                .filter(c -> c.birthYear() != null && (year - c.birthYear()) < exitAge)
                .count();
        double parts = 2.0;
        for (int i = 1; i <= attached; i++) {
            parts += (i <= 2) ? 0.5 : 1.0;
        }
        return parts;
    }

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
     * Calcule l'impôt progressif pour une part fiscale.
     */
    public static BigDecimal taxForOnePart(BigDecimal q, List<TaxBracketModel> brackets) {
        if (q == null || q.compareTo(BigDecimal.ZERO) <= 0 || brackets == null || brackets.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        List<TaxBracketModel> sorted = new ArrayList<>(brackets);
        sorted.sort((a, b) -> {
            if (a.upTo() == null) return 1;
            if (b.upTo() == null) return -1;
            return a.upTo().compareTo(b.upTo());
        });

        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal prevThreshold = BigDecimal.ZERO;

        for (TaxBracketModel b : sorted) {
            if (q.compareTo(prevThreshold) > 0) {
                BigDecimal upper = b.upTo() != null ? q.min(b.upTo()) : q;
                BigDecimal taxableInBracket = upper.subtract(prevThreshold);
                tax = tax.add(taxableInBracket.multiply(b.getEffectiveRate()));
            }
            if (b.upTo() != null) {
                prevThreshold = b.upTo();
                if (q.compareTo(b.upTo()) <= 0) {
                    break;
                }
            } else {
                break;
            }
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcule les projections fiscales à partir du contrat Fiscalité.
     *
     * <p>La surcharge historique qui accepte {@link BudgetDataModel} reste disponible pendant
     * la migration ; le chemin applicatif construit désormais le contrat via
     * {@code TaxInputFactory} et utilise cette méthode.
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
     * Calcule la projection d'imposition annuelle pour une liste d'années.
     */
    public static List<TaxYearlyModel> computeTaxYearly(
            BudgetDataModel data,
            List<Integer> years,
            List<IncomeModel> effectiveIncomes
    ) {
        if (data == null || years == null) {
            return List.of();
        }

        List<TaxBracketModel> brackets = data.getEffectiveTaxBrackets();
        List<TaxChildModel> children = data.getEffectiveTaxChildren();
        int exitAge = data.settings() != null ? data.settings().getEffectiveChildExitAge() : 21;
        BigDecimal abattement = data.settings() != null ? data.settings().getEffectiveTaxAbattement() : BigDecimal.ZERO;

        List<TaxYearlyModel> result = new ArrayList<>();

        for (int year : years) {
            BigDecimal regularIncome = BigDecimal.ZERO;
            if (effectiveIncomes != null) {
                for (IncomeModel i : effectiveIncomes) {
                    regularIncome = regularIncome.add(incomeAnnualForYear(i, year));
                }
            }

            VariableDetail varDetail = variableIncomeDetailForYear(data, year);
            BigDecimal grossPayroll = regularIncome.add(varDetail.taxable());

            BigDecimal taxableIncome = grossPayroll.multiply(BigDecimal.ONE.subtract(abattement))
                    .setScale(2, RoundingMode.HALF_UP);
            double parts = partsForYear(children, exitAge, year);

            BigDecimal taxForecast = BigDecimal.ZERO;
            if (parts > 0) {
                BigDecimal q = taxableIncome.divide(BigDecimal.valueOf(parts), 10, RoundingMode.HALF_UP);
                taxForecast = taxForOnePart(q, brackets).multiply(BigDecimal.valueOf(parts))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            Optional<TaxActualOverrideModel> taxOverride = data.getEffectiveTaxActualOverrides().stream()
                    .filter(o -> o.year() != null && o.year() == year)
                    .findFirst();
            BigDecimal taxActual = taxOverride.map(TaxActualOverrideModel::amount)
                    .orElse(taxForecast)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal rateForecast = grossPayroll.compareTo(BigDecimal.ZERO) > 0
                    ? taxForecast.divide(grossPayroll, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Optional<TaxRateOverrideModel> rateOverride = data.getEffectiveTaxRateOverrides().stream()
                    .filter(o -> o.year() != null && o.year() == year)
                    .findFirst();
            BigDecimal ratePAS = rateOverride.map(TaxRateOverrideModel::rate)
                    .orElse(rateForecast);

            BigDecimal withheld = ratePAS.multiply(grossPayroll).setScale(2, RoundingMode.HALF_UP);

            result.add(new TaxYearlyModel(
                    year,
                    parts,
                    taxableIncome,
                    taxForecast,
                    taxActual,
                    ratePAS,
                    withheld
            ));
        }

        return result;
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

    public record VariableDetail(BigDecimal total, BigDecimal taxable) {}

    public static VariableDetail variableIncomeDetailForYear(BudgetDataModel data, int year) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;

        if (data == null || data.getEffectiveVariableIncomes() == null) {
            return new VariableDetail(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        for (VariableIncomeModel v : data.getEffectiveVariableIncomes()) {
            if (v.startYear() != null && year < v.startYear()) continue;
            if (v.endYear() != null && year > v.endYear()) continue;

            Optional<IncomeModel> refRow = data.getEffectiveIncomes().stream()
                    .filter(r -> v.refIncomeLabel() != null && v.refIncomeLabel().equalsIgnoreCase(r.label()))
                    .findFirst();
            BigDecimal refAnnual = refRow.map(r -> incomeAnnualForYear(r, year)).orElse(BigDecimal.ZERO);
            BigDecimal forecast = refAnnual.multiply(v.getEffectiveRate());

            Optional<VariableOverrideModel> override = data.getEffectiveVariableOverrides().stream()
                    .filter(o -> v.label() != null && v.label().equalsIgnoreCase(o.label()) && o.year() != null && o.year() == year)
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

            total = total.add(amount);
            if (isTaxable) {
                taxable = taxable.add(amount);
            }
        }
        return new VariableDetail(
                total.setScale(2, RoundingMode.HALF_UP),
                taxable.setScale(2, RoundingMode.HALF_UP)
        );
    }

    public static BigDecimal incomeAnnualForYear(IncomeModel row, int year) {
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

    public static int monthsActiveInYear(String startISO, String endISO, int year) {
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
}
