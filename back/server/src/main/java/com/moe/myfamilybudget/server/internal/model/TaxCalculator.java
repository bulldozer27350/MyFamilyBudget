package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moteur de calcul domaine métier pour la fiscalité (Impôts).
 * Manipule exclusivement les records du domaine interne et utilise BigDecimal avec RoundingMode.HALF_UP.
 */
public class TaxCalculator {

    private static final int TRIMESTRES_REQUIS = 172;
    private static final int AGE_TAUX_PLEIN_AUTO = 67;
    private static final BigDecimal DECOTE_PAR_TRIMESTRE = new BigDecimal("0.00625");
    private static final BigDecimal SURCOTE_PAR_TRIMESTRE = new BigDecimal("0.0125");
    private static final BigDecimal TAUX_PLEIN = new BigDecimal("0.50");
    private static final BigDecimal TAUX_MINORE_PLANCHER = new BigDecimal("0.375");
    private static final BigDecimal MAJORATION_3_ENFANTS = new BigDecimal("0.10");

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
     * Calcule le simulateur d'impôts complet (TaxResultModel) à partir des données de budget.
     */
    public static TaxResultModel computeTaxResult(BudgetDataModel data) {
        if (data == null) {
            return new TaxResultModel(List.of(), List.of(), List.of(), List.of(), null, List.of());
        }

        SettingsModel settings = data.settings() != null ? data.settings() : new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "", "manual", BigDecimal.ZERO, 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015")
        );

        int birthYear = settings.getEffectiveBirthYear();
        int retireAge = settings.getEffectiveRetireAge();
        int retireYear = birthYear + retireAge;
        int startYear = findEarliestYear(data);
        int wantedEnd = birthYear + settings.getEffectiveSimulateUntilAge();
        int endYear = Math.max(retireYear + 3, wantedEnd);

        List<Integer> years = new ArrayList<>();
        for (int y = startYear; y <= endYear; y++) {
            years.add(y);
        }

        int lastYear = years.isEmpty() ? retireYear : years.get(years.size() - 1);
        List<IncomeModel> effectiveIncomes = new ArrayList<>(data.getEffectiveIncomes());
        effectiveIncomes.addAll(pensionIncomeRows(data, retireYear, lastYear));

        List<TaxYearlyModel> taxYearly = computeTaxYearly(data, years, effectiveIncomes);
        List<TaxYearlyModel> taxPreview = buildTaxPreview(taxYearly, LocalDate.now().getYear());

        return new TaxResultModel(
                data.getEffectiveTaxChildren(),
                data.getEffectiveTaxBrackets(),
                data.getEffectiveTaxRateOverrides(),
                data.getEffectiveTaxActualOverrides(),
                settings,
                taxPreview
        );
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

    public static List<IncomeModel> pensionIncomeRows(BudgetDataModel data, int retireYear, int lastYear) {
        List<IncomeModel> rows = new ArrayList<>();
        if (data == null || data.retirement() == null || data.retirement().people() == null) {
            return rows;
        }
        BigDecimal inflationRate = data.settings() != null ? data.settings().getEffectiveInflationRate() : BigDecimal.ZERO;

        for (RetirementModel.RetirementPersonModel person : data.retirement().people()) {
            BigDecimal monthlyPension = computeRetirementPensionMensuelle(data, person, retireYear);
            if (monthlyPension.compareTo(BigDecimal.ZERO) > 0) {
                rows.add(new IncomeModel(
                        "pension-" + person.id(),
                        "Pension " + (person.name() != null ? person.name() : "retraite") + " (auto)",
                        monthlyPension,
                        retireYear + "-01-01",
                        Math.max(retireYear, lastYear) + "-12-31",
                        inflationRate,
                        null,
                        null
                ));
            }
        }
        return rows;
    }

    private static BigDecimal computeRetirementPensionMensuelle(BudgetDataModel data, RetirementModel.RetirementPersonModel person, int retireYear) {
        int birthYear = person.birthYear() != null ? person.birthYear() : (data.settings() != null ? data.settings().getEffectiveBirthYear() : 1985);
        int trimestresValides = person.getEffectiveTrimestresValides();
        int trimestresDateYear = yearOf(person.trimestresDate()) != null ? yearOf(person.trimestresDate()) : LocalDate.now().getYear() - 1;

        int trimestresFuturs = 0;
        for (int y = trimestresDateYear + 1; y <= retireYear; y++) {
            if (projectedAnnualSalary(data, person, y).compareTo(BigDecimal.ZERO) > 0) {
                trimestresFuturs += 4;
            }
        }

        int trimestresEstimesDepart = trimestresValides + trimestresFuturs;
        int ageDepart = retireYear - birthYear;
        int trimestresJusquTauxPleinAuto = Math.max(0, (AGE_TAUX_PLEIN_AUTO - ageDepart) * 4);

        BigDecimal tauxApplique = TAUX_PLEIN;
        if (trimestresEstimesDepart < TRIMESTRES_REQUIS) {
            int manquants = TRIMESTRES_REQUIS - trimestresEstimesDepart;
            int trimestresDecote = Math.min(manquants, trimestresJusquTauxPleinAuto);
            BigDecimal decote = DECOTE_PAR_TRIMESTRE.multiply(BigDecimal.valueOf(trimestresDecote));
            tauxApplique = TAUX_PLEIN.subtract(decote).max(TAUX_MINORE_PLANCHER);
        } else if (trimestresEstimesDepart > TRIMESTRES_REQUIS) {
            int surplus = trimestresEstimesDepart - TRIMESTRES_REQUIS;
            BigDecimal surcote = SURCOTE_PAR_TRIMESTRE.multiply(BigDecimal.valueOf(surplus));
            tauxApplique = TAUX_PLEIN.add(surcote);
        }

        Map<Integer, BigDecimal> byYear = new HashMap<>();
        for (RetirementModel.SalaryHistoryModel h : person.getEffectiveSalaryHistory()) {
            if (h.year() != null && h.getEffectiveSalary().compareTo(BigDecimal.ZERO) > 0) {
                byYear.put(h.year(), h.getEffectiveSalary());
            }
        }

        List<YearSalary> futureYears = new ArrayList<>();
        for (int y = trimestresDateYear + 1; y <= retireYear - 1; y++) {
            BigDecimal s = projectedAnnualSalary(data, person, y);
            if (s.compareTo(BigDecimal.ZERO) > 0) {
                futureYears.add(new YearSalary(y, s));
                if (!byYear.containsKey(y)) {
                    byYear.put(y, s);
                }
            }
        }

        List<YearSalary> allEntries = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : byYear.entrySet()) {
            allEntries.add(new YearSalary(e.getKey(), e.getValue()));
        }
        allEntries.sort((a, b) -> Integer.compare(b.year(), a.year()));

        List<YearSalary> last25 = allEntries.subList(0, Math.min(25, allEntries.size()));
        BigDecimal sumCapped = BigDecimal.ZERO;
        for (YearSalary ys : last25) {
            BigDecimal pass = passForYear(data, ys.year());
            BigDecimal capped = ys.salary().min(pass);
            sumCapped = sumCapped.add(capped);
        }

        BigDecimal sam = last25.isEmpty() ? BigDecimal.ZERO : sumCapped.divide(BigDecimal.valueOf(last25.size()), 10, RoundingMode.HALF_UP);
        BigDecimal majoration = data.getEffectiveTaxChildren().size() >= 3 ? BigDecimal.ONE.add(MAJORATION_3_ENFANTS) : BigDecimal.ONE;

        double ratioTrimestresVal = Math.min(trimestresEstimesDepart, TRIMESTRES_REQUIS) / (double) TRIMESTRES_REQUIS;
        BigDecimal ratioTrimestres = BigDecimal.valueOf(ratioTrimestresVal);

        BigDecimal pensionBaseAnnuelle = sam.multiply(tauxApplique).multiply(ratioTrimestres).multiply(majoration);

        BigDecimal pointsActuels = person.getEffectiveAgircPoints();
        BigDecimal ratioPointsParEuro = person.getEffectiveRatioPointsParEuro();

        BigDecimal pointsFuturs = BigDecimal.ZERO;
        for (YearSalary fy : futureYears) {
            pointsFuturs = pointsFuturs.add(fy.salary().multiply(ratioPointsParEuro));
        }

        BigDecimal pointsEstimes = pointsActuels.add(pointsFuturs);
        BigDecimal valeurPointDepart = agircPointValueForYear(data, retireYear);
        BigDecimal pensionComplementaireAnnuelle = pointsEstimes.multiply(valeurPointDepart).multiply(majoration);

        BigDecimal pensionTotaleAnnuelle = pensionBaseAnnuelle.add(pensionComplementaireAnnuelle);
        return pensionTotaleAnnuelle.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
    }

    private record YearSalary(int year, BigDecimal salary) {}

    private static BigDecimal passForYear(BudgetDataModel data, int year) {
        BigDecimal base = data.retirement() != null ? data.retirement().getEffectivePass2026() : new BigDecimal("47100");
        BigDecimal growth = data.retirement() != null ? data.retirement().getEffectivePassGrowthRate() : new BigDecimal("0.015");
        double factor = Math.pow(1.0 + growth.doubleValue(), year - 2026);
        return base.multiply(BigDecimal.valueOf(factor));
    }

    private static BigDecimal agircPointValueForYear(BudgetDataModel data, int year) {
        BigDecimal base = data.retirement() != null ? data.retirement().getEffectiveAgircPointValue() : new BigDecimal("1.4386");
        Integer baseYear = yearOf(data.retirement() != null ? data.retirement().agircPointDateGlobal() : "2025-11-01");
        if (baseYear == null) baseYear = 2025;
        BigDecimal growth = data.retirement() != null ? data.retirement().getEffectiveAgircPointGrowthRate() : new BigDecimal("0.01");
        double factor = Math.pow(1.0 + growth.doubleValue(), Math.max(0, year - baseYear));
        return base.multiply(BigDecimal.valueOf(factor));
    }

    private static BigDecimal projectedAnnualSalary(BudgetDataModel data, RetirementModel.RetirementPersonModel person, int year) {
        if (person == null || person.incomeLabel() == null || person.incomeLabel().isBlank()) {
            return BigDecimal.ZERO;
        }
        Optional<IncomeModel> row = data.getEffectiveIncomes().stream()
                .filter(r -> person.incomeLabel().equalsIgnoreCase(r.label()))
                .findFirst();
        return row.map(r -> incomeAnnualForYear(r, year)).orElse(BigDecimal.ZERO);
    }

    public static int findEarliestYear(BudgetDataModel data) {
        if (data == null) return 2026;
        List<String> dates = new ArrayList<>();

        for (IncomeModel i : data.getEffectiveIncomes()) if (i.start() != null) dates.add(i.start());
        for (ChargeModel c : data.getEffectiveCharges()) if (c.start() != null) dates.add(c.start());
        for (PlacementModel p : data.getEffectivePlacements()) {
            if (p.monthlyFrom() != null) dates.add(p.monthlyFrom());
            if (p.balanceDate() != null) dates.add(p.balanceDate());
        }
        for (OneOffExpenseModel o : data.getEffectiveOneoff()) if (o.date() != null) dates.add(o.date());
        for (TransferModel t : data.getEffectiveTransfers()) if (t.date() != null) dates.add(t.date());
        if (data.settings() != null && data.settings().pivotDate() != null) dates.add(data.settings().pivotDate());

        if (data.bankImport() != null && data.bankImport().transactions() != null) {
            for (BankImportModel.BankTransactionModel t : data.bankImport().transactions()) {
                if (t.date() != null) dates.add(t.date());
            }
        }

        int earliestYear = 2026;
        boolean found = false;

        for (String d : dates) {
            Integer y = yearOf(d);
            if (y != null) {
                if (!found || y < earliestYear) {
                    earliestYear = y;
                    found = true;
                }
            }
        }

        return found ? earliestYear : 2026;
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
            return null;
        }
    }
}
