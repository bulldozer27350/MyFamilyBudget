package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.OverviewApi;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.CashflowYearModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.OverviewResultModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineYearModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.model.TripleAmountModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;

@RestController
public class OverviewServiceImpl implements OverviewApi{

    private static final int TRIMESTRES_REQUIS = 172;
    private static final int AGE_TAUX_PLEIN_AUTO = 67;
    private static final BigDecimal DECOTE_PAR_TRIMESTRE = new BigDecimal("0.00625");
    private static final BigDecimal SURCOTE_PAR_TRIMESTRE = new BigDecimal("0.0125");
    private static final BigDecimal TAUX_PLEIN = new BigDecimal("0.50");
    private static final BigDecimal TAUX_MINORE_PLANCHER = new BigDecimal("0.375");
    private static final BigDecimal MAJORATION_3_ENFANTS = new BigDecimal("0.10");

    private final OverviewMapper mapper;

    public OverviewServiceImpl(OverviewMapper mapper) {
        this.mapper = mapper;
    }
    
    public ResponseEntity<OverviewResponseDto> buildOverview(BudgetDataDto parameters, Boolean useConstantEuros) {
        checkParameters(parameters);
        BudgetDataModel internalData = this.mapper.toInternalModel(parameters);

        OverviewResultModel internalResult = computeOverview(internalData, useConstantEuros);
        return ResponseEntity.ok(this.mapper.toDto(internalResult));
    }

    private void checkParameters(BudgetDataDto parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("BudgetDataDto cannot be null");
        }
    }

    private OverviewResultModel computeOverview(BudgetDataModel data, boolean useConstantEuros) {
        SettingsModel settings = data.settings() != null ? data.settings() : new SettingsModel(
            1985, 64, 85, BigDecimal.ZERO, null, null, BigDecimal.ZERO, 21, BigDecimal.ZERO, new BigDecimal("47100"), new BigDecimal("0.015")
        );

        int retireYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();
        FinancialProjections projections = computeFinancialProjections(data, useConstantEuros);
        List<Integer> years = projections.years();

        int startYear = years.isEmpty() ? retireYear : years.get(0);
        BigDecimal inflationRate = settings.getEffectiveInflationRate();

        BigDecimal retireDeflator;
        if (useConstantEuros) {
            double deflatorVal = Math.pow(1.0 / (1.0 + inflationRate.doubleValue()), retireYear - startYear);
            retireDeflator = BigDecimal.valueOf(deflatorVal);
        } else {
            retireDeflator = BigDecimal.ONE;
        }

        TripleAmountModel financialOnlyPatrimoine = computeFinancialOnlyPatrimoine(data, projections.patrimoine(), years, retireYear, retireDeflator);
        BigDecimal realEstateAtRetire = computeRealEstateAtRetire(data, retireYear, retireDeflator);

        TripleAmountModel retirePatrimoine = new TripleAmountModel(
            financialOnlyPatrimoine.pess().add(realEstateAtRetire),
            financialOnlyPatrimoine.corr().add(realEstateAtRetire),
            financialOnlyPatrimoine.opti().add(realEstateAtRetire)
        );

        Optional<CashflowYearModel> retireYearData = projections.cashflow().stream().filter(c -> c.year() == retireYear).findFirst();
        BigDecimal retireCharges = retireYearData.map(c -> c.charges().divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP).multiply(retireDeflator))
            .orElse(BigDecimal.ZERO);

        BigDecimal totalPensions = BigDecimal.ZERO;
        RetirementModel retirement = data.retirement();
        if (retirement != null && retirement.people() != null) {
            for (RetirementModel.RetirementPersonModel person : retirement.people()) {
                RetirementProjection proj = computeRetirementProjection(data, person, retireYear);
                totalPensions = totalPensions.add(proj.pensionTotaleMensuelle());
            }
        }
        totalPensions = totalPensions.multiply(retireDeflator);

        BigDecimal patrimoineActuel = data.getEffectivePlacements().stream()
            .map(PlacementModel::getEffectiveBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fluxNetActuel = projections.cashflow().isEmpty() ? BigDecimal.ZERO : projections.cashflow().get(0).net();

        return new OverviewResultModel(
            data,
            years,
            projections.cashflow(),
            projections.patrimoine(),
            useConstantEuros,
            retireYear,
            computePivotBalance(data),
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

    private TripleAmountModel computeFinancialOnlyPatrimoine(BudgetDataModel data, PatrimoineProjectionsModel patrimoine, List<Integer> years, int retireYear, BigDecimal deflator) {
        int idx = years.indexOf(retireYear);
        if (idx == -1) {
            return new TripleAmountModel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Set<String> excludedLabels = new HashSet<>();
        for (PlacementModel p : data.getEffectivePlacements()) {
            if (p.isExcludedFromRetirement()) {
                excludedLabels.add(p.label());
            }
        }

        BigDecimal pess = BigDecimal.ZERO;
        BigDecimal corr = BigDecimal.ZERO;
        BigDecimal opti = BigDecimal.ZERO;

        for (PatrimoinePerPlacementModel pp : patrimoine.perPlacement()) {
            if (excludedLabels.contains(pp.label())) {
                continue;
            }
            if (idx < pp.rows().size()) {
                PatrimoineYearModel row = pp.rows().get(idx);
                pess = pess.add(row.pess());
                corr = corr.add(row.corr());
                opti = opti.add(row.opti());
            }
        }

        return new TripleAmountModel(
            pess.multiply(deflator),
            corr.multiply(deflator),
            opti.multiply(deflator)
        );
    }

    private BigDecimal computeRealEstateAtRetire(BudgetDataModel data, int retireYear, BigDecimal deflator) {
        int currentYear = LocalDate.now().getYear();
        BigDecimal total = BigDecimal.ZERO;

        for (RealEstateModel r : data.getEffectiveRealEstate()) {
            int valuationYear = r.valuationYear() != null ? r.valuationYear() : currentYear;
            int elapsed = Math.max(0, retireYear - valuationYear);
            double growthFactor = Math.pow(1.0 + r.getEffectiveAnnualGrowthRate().doubleValue(), elapsed);
            BigDecimal val = r.getEffectiveCurrentValue().multiply(BigDecimal.valueOf(growthFactor)).multiply(deflator);
            total = total.add(val);
        }
        return total;
    }

    private BigDecimal computePivotBalance(BudgetDataModel data) {
        if (data.settings() == null || data.settings().pivotDate() == null) {
            return null;
        }
        if ("manual".equalsIgnoreCase(data.settings().pivotMode())) {
            return data.settings().getEffectiveStartBalance();
        }
        BigDecimal base = data.settings().getEffectiveStartBalance();
        String pivotDate = data.settings().pivotDate();

        BigDecimal sum = BigDecimal.ZERO;
        if (data.bankImport() != null && data.bankImport().transactions() != null) {
            for (BankImportModel.BankTransactionModel t : data.bankImport().transactions()) {
                if (t.date() != null && t.date().compareTo(pivotDate) <= 0) {
                    sum = sum.add(t.amount() != null ? t.amount() : BigDecimal.ZERO);
                }
            }
        }
        return base.add(sum);
    }

    private record FinancialProjections(
        List<Integer> years,
        List<CashflowYearModel> cashflow,
        PatrimoineProjectionsModel patrimoine
    ) {}

    private FinancialProjections computeFinancialProjections(BudgetDataModel data, boolean useConstantEuros) {
        SettingsModel settings = data.settings() != null ? data.settings() : new SettingsModel(
            1985, 64, 85, BigDecimal.ZERO, null, null, BigDecimal.ZERO, 21, BigDecimal.ZERO, new BigDecimal("47100"), new BigDecimal("0.015")
        );

        int retireYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();
        int startYear = findEarliestYear(data);
        int wantedEnd = settings.getEffectiveBirthYear() + settings.getEffectiveSimulateUntilAge();
        int endYear = Math.max(retireYear + 3, wantedEnd);

        List<Integer> years = new ArrayList<>();
        for (int y = startYear; y <= endYear; y++) {
            years.add(y);
        }

        int lastYear = years.isEmpty() ? retireYear : years.get(years.size() - 1);
        List<IncomeModel> effectiveIncomes = new ArrayList<>(data.getEffectiveIncomes());
        effectiveIncomes.addAll(pensionIncomeRows(data, retireYear, lastYear));

        List<TaxYearlyInfo> taxYearly = computeTaxYearly(data, years, effectiveIncomes);

        BigDecimal pivotBalanceValue = computePivotBalance(data);
        BigDecimal balance = pivotBalanceValue != null ? pivotBalanceValue : settings.getEffectiveStartBalance();

        List<CashflowYearModel> cashflow = new ArrayList<>();
        for (int idx = 0; idx < years.size(); idx++) {
            int year = years.get(idx);

            BigDecimal income = BigDecimal.ZERO;
            for (IncomeModel i : effectiveIncomes) {
                income = income.add(incomeAnnualForYear(i, year));
            }

            BigDecimal variableIncome = computeVariableIncomeForYear(data, year);
            BigDecimal savings = placementsMonthlyAnnualForYear(data.getEffectivePlacements(), year);

            BigDecimal charges = BigDecimal.ZERO;
            for (ChargeModel c : data.getEffectiveCharges()) {
                charges = charges.add(chargeAnnualForYear(c, year, settings.getEffectiveInflationRate()));
            }

            BigDecimal oneoff = BigDecimal.ZERO;
            for (OneOffExpenseModel o : data.getEffectiveOneoff()) {
                if (yearOf(o.date()) != null && yearOf(o.date()) == year) {
                    oneoff = oneoff.add(o.getEffectiveAmount());
                }
            }

            BigDecimal transfersY = BigDecimal.ZERO;
            for (TransferModel t : data.getEffectiveTransfers()) {
                if (yearOf(t.date()) != null && yearOf(t.date()) == year) {
                    transfersY = transfersY.add(t.getEffectiveAmount());
                }
            }

            TaxYearlyInfo taxInfo = taxYearly.get(idx);
            BigDecimal impots = taxInfo.withheld();

            BigDecimal regularisation = BigDecimal.ZERO;
            if (idx > 0) {
                TaxYearlyInfo prevTax = taxYearly.get(idx - 1);
                regularisation = prevTax.taxActual().subtract(prevTax.withheld());
            }

            BigDecimal net = income.add(variableIncome)
                .subtract(savings)
                .subtract(charges)
                .subtract(oneoff)
                .add(transfersY)
                .subtract(impots)
                .subtract(regularisation);

            balance = balance.add(net);

            cashflow.add(new CashflowYearModel(year, income, variableIncome, savings, charges, oneoff, transfersY, impots, regularisation, net, balance));
        }

        PatrimoineProjectionsModel patrimoine = computePatrimoineProjections(data, years, useConstantEuros, settings.getEffectiveInflationRate());

        return new FinancialProjections(years, cashflow, patrimoine);
    }

    private record TaxYearlyInfo(
        int year,
        double parts,
        BigDecimal taxableIncome,
        BigDecimal taxForecast,
        BigDecimal taxActual,
        BigDecimal ratePAS,
        BigDecimal withheld
    ) {}

    private List<TaxYearlyInfo> computeTaxYearly(BudgetDataModel data, List<Integer> years, List<IncomeModel> effectiveIncomes) {
        List<TaxBracketModel> brackets = data.getEffectiveTaxBrackets();
        List<TaxChildModel> children = data.getEffectiveTaxChildren();
        int exitAge = data.settings() != null ? data.settings().getEffectiveChildExitAge() : 21;
        BigDecimal abattement = data.settings() != null ? data.settings().getEffectiveTaxAbattement() : BigDecimal.ZERO;

        List<TaxYearlyInfo> result = new ArrayList<>();

        for (int year : years) {
            BigDecimal regularIncome = BigDecimal.ZERO;
            for (IncomeModel i : effectiveIncomes) {
                regularIncome = regularIncome.add(incomeAnnualForYear(i, year));
            }

            VariableDetail varDetail = variableIncomeDetailForYear(data, year);
            BigDecimal grossPayroll = regularIncome.add(varDetail.taxable());

            BigDecimal taxableIncome = grossPayroll.multiply(BigDecimal.ONE.subtract(abattement));
            double parts = partsForYear(children, exitAge, year);

            BigDecimal taxForecast = BigDecimal.ZERO;
            if (parts > 0) {
                BigDecimal q = taxableIncome.divide(BigDecimal.valueOf(parts), 10, RoundingMode.HALF_UP);
                taxForecast = taxForOnePart(q, brackets).multiply(BigDecimal.valueOf(parts));
            }

            Optional<TaxActualOverrideModel> taxOverride = data.getEffectiveTaxActualOverrides().stream().filter(o -> o.year() != null && o.year() == year).findFirst();
            BigDecimal taxActual = taxOverride.map(TaxActualOverrideModel::amount).orElse(taxForecast);

            BigDecimal rateForecast = grossPayroll.compareTo(BigDecimal.ZERO) > 0 ? taxForecast.divide(grossPayroll, 10, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            Optional<TaxRateOverrideModel> rateOverride = data.getEffectiveTaxRateOverrides().stream().filter(o -> o.year() != null && o.year() == year).findFirst();
            BigDecimal ratePAS = rateOverride.map(TaxRateOverrideModel::rate).orElse(rateForecast);

            BigDecimal withheld = ratePAS.multiply(grossPayroll);

            result.add(new TaxYearlyInfo(year, parts, taxableIncome, taxForecast, taxActual, ratePAS, withheld));
        }

        return result;
    }

    private PatrimoineProjectionsModel computePatrimoineProjections(BudgetDataModel data, List<Integer> years, boolean useConstantEuros, BigDecimal inflationRate) {
        List<PatrimoinePerPlacementModel> perPlacement = new ArrayList<>();

        for (PlacementModel p : data.getEffectivePlacements()) {
            BigDecimal pess = p.getEffectiveBalance();
            BigDecimal corr = p.getEffectiveBalance();
            BigDecimal opti = p.getEffectiveBalance();

            Integer monthlyFromYear = yearOf(p.monthlyFrom());
            if (monthlyFromYear == null) monthlyFromYear = years.isEmpty() ? 2026 : years.get(0);

            Integer monthlyUntilYear = yearOf(p.monthlyUntil());

            List<PatrimoineYearModel> rows = new ArrayList<>();
            for (int year : years) {
                BigDecimal withdraw = BigDecimal.ZERO;
                for (TransferModel t : data.getEffectiveTransfers()) {
                    if (p.label() != null && p.label().equalsIgnoreCase(t.placement()) && yearOf(t.date()) != null && yearOf(t.date()) == year) {
                        withdraw = withdraw.add(t.getEffectiveAmount());
                    }
                }

                boolean withinWindow = year >= monthlyFromYear && (monthlyUntilYear == null || year <= monthlyUntilYear);
                BigDecimal monthlyContrib = withinWindow ? p.getEffectiveMonthly().multiply(BigDecimal.valueOf(12)) : BigDecimal.ZERO;

                pess = pess.multiply(BigDecimal.ONE.add(p.getEffectiveRatePess())).add(monthlyContrib).subtract(withdraw);
                corr = corr.multiply(BigDecimal.ONE.add(p.getEffectiveRateCorr())).add(monthlyContrib).subtract(withdraw);
                opti = opti.multiply(BigDecimal.ONE.add(p.getEffectiveRateOpti())).add(monthlyContrib).subtract(withdraw);

                rows.add(new PatrimoineYearModel(year, pess, corr, opti));
            }
            perPlacement.add(new PatrimoinePerPlacementModel(p.label(), rows));
        }

        List<PatrimoineYearModel> totals = new ArrayList<>();
        int startYear = years.isEmpty() ? 2026 : years.get(0);

        for (int idx = 0; idx < years.size(); idx++) {
            int year = years.get(idx);
            double deflatorVal = useConstantEuros ? Math.pow(1.0 / (1.0 + inflationRate.doubleValue()), year - startYear) : 1.0;
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

            totals.add(new PatrimoineYearModel(year, totalPess.multiply(deflator), totalCorr.multiply(deflator), totalOpti.multiply(deflator)));
        }

        return new PatrimoineProjectionsModel(perPlacement, totals);
    }

    private List<IncomeModel> pensionIncomeRows(BudgetDataModel data, int retireYear, int lastYear) {
        List<IncomeModel> rows = new ArrayList<>();
        BigDecimal inflationRate = data.settings() != null ? data.settings().getEffectiveInflationRate() : BigDecimal.ZERO;

        RetirementModel retirement = data.retirement();
        if (retirement != null && retirement.people() != null) {
            for (RetirementModel.RetirementPersonModel person : retirement.people()) {
                RetirementProjection proj = computeRetirementProjection(data, person, retireYear);
                if (proj.pensionTotaleMensuelle().compareTo(BigDecimal.ZERO) > 0) {
                    rows.add(new IncomeModel(
                        "pension-" + person.id(),
                        "Pension " + (person.name() != null ? person.name() : "retraite") + " (auto)",
                        proj.pensionTotaleMensuelle(),
                        retireYear + "-01-01",
                        Math.max(retireYear, lastYear) + "-12-31",
                        inflationRate,
                        null,
                        null
                    ));
                }
            }
        }
        return rows;
    }

    public record RetirementProjection(
        int ageDepart,
        int trimestresValides,
        int trimestresEstimesDepart,
        int trimestresRequis,
        boolean manqueTauxPlein,
        BigDecimal tauxApplique,
        BigDecimal decote,
        BigDecimal surcote,
        BigDecimal SAM,
        BigDecimal majoration,
        BigDecimal pensionBaseAnnuelle,
        BigDecimal pointsEstimes,
        BigDecimal valeurPointDepart,
        BigDecimal pensionComplementaireAnnuelle,
        BigDecimal pensionTotaleAnnuelle,
        BigDecimal pensionTotaleMensuelle
    ) {}

    public RetirementProjection computeRetirementProjection(BudgetDataModel data, RetirementModel.RetirementPersonModel person, int retireYear) {
        int defaultBirthYear = data.settings() != null ? data.settings().getEffectiveBirthYear() : 1985;
        int birthYear = person.birthYear() != null ? person.birthYear() : defaultBirthYear;

        int trimestresValides = person.getEffectiveTrimestresValides();
        Integer trimestresDateYear = yearOf(person.trimestresDate());
        if (trimestresDateYear == null) {
            trimestresDateYear = LocalDate.now().getYear() - 1;
        }

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
        BigDecimal decote = BigDecimal.ZERO;
        BigDecimal surcote = BigDecimal.ZERO;

        if (trimestresEstimesDepart < TRIMESTRES_REQUIS) {
            int manquants = TRIMESTRES_REQUIS - trimestresEstimesDepart;
            int trimestresDecote = Math.min(manquants, trimestresJusquTauxPleinAuto);
            decote = BigDecimal.valueOf(trimestresDecote).multiply(DECOTE_PAR_TRIMESTRE);
            tauxApplique = TAUX_PLEIN.subtract(decote).max(TAUX_MINORE_PLANCHER);
        } else if (trimestresEstimesDepart > TRIMESTRES_REQUIS) {
            surcote = BigDecimal.valueOf(trimestresEstimesDepart - TRIMESTRES_REQUIS).multiply(SURCOTE_PAR_TRIMESTRE);
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

        BigDecimal SAM = last25.isEmpty() ? BigDecimal.ZERO : sumCapped.divide(BigDecimal.valueOf(last25.size()), 10, RoundingMode.HALF_UP);
        BigDecimal majoration = data.getEffectiveTaxChildren().size() >= 3 ? BigDecimal.ONE.add(MAJORATION_3_ENFANTS) : BigDecimal.ONE;

        double ratioTrimestresVal = Math.min(trimestresEstimesDepart, TRIMESTRES_REQUIS) / (double) TRIMESTRES_REQUIS;
        BigDecimal ratioTrimestres = BigDecimal.valueOf(ratioTrimestresVal);

        BigDecimal pensionBaseAnnuelle = SAM.multiply(tauxApplique).multiply(ratioTrimestres).multiply(majoration);

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
        BigDecimal pensionTotaleMensuelle = pensionTotaleAnnuelle.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        return new RetirementProjection(
            ageDepart,
            trimestresValides,
            trimestresEstimesDepart,
            TRIMESTRES_REQUIS,
            trimestresEstimesDepart < TRIMESTRES_REQUIS,
            tauxApplique,
            decote,
            surcote,
            SAM,
            majoration,
            pensionBaseAnnuelle,
            pointsEstimes,
            valeurPointDepart,
            pensionComplementaireAnnuelle,
            pensionTotaleAnnuelle,
            pensionTotaleMensuelle
        );
    }

    private record YearSalary(int year, BigDecimal salary) {}

    private BigDecimal projectedAnnualSalary(BudgetDataModel data, RetirementModel.RetirementPersonModel person, int year) {
        if (person.incomeLabel() == null || person.incomeLabel().isBlank()) {
            return BigDecimal.ZERO;
        }
        Optional<IncomeModel> income = data.getEffectiveIncomes().stream().filter(i -> person.incomeLabel().equalsIgnoreCase(i.label())).findFirst();
        return income.map(incomeModel -> incomeAnnualForYear(incomeModel, year)).orElse(BigDecimal.ZERO);
    }

    private BigDecimal passForYear(BudgetDataModel data, int year) {
        BigDecimal base = (data.retirement() != null) ? data.retirement().getEffectivePass2026() : new BigDecimal("47100");
        BigDecimal growth = (data.retirement() != null) ? data.retirement().getEffectivePassGrowthRate() : new BigDecimal("0.015");

        double factor = Math.pow(1.0 + growth.doubleValue(), year - 2026);
        return base.multiply(BigDecimal.valueOf(factor));
    }

    private BigDecimal agircPointValueForYear(BudgetDataModel data, int year) {
        BigDecimal base = (data.retirement() != null) ? data.retirement().getEffectiveAgircPointValue() : new BigDecimal("1.4386");
        Integer baseYear = (data.retirement() != null) ? yearOf(data.retirement().agircPointDateGlobal()) : 2025;
        if (baseYear == null) baseYear = 2025;

        BigDecimal growth = (data.retirement() != null) ? data.retirement().getEffectiveAgircPointGrowthRate() : new BigDecimal("0.01");
        int elapsed = Math.max(0, year - baseYear);

        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);
        return base.multiply(BigDecimal.valueOf(factor));
    }

    private BigDecimal incomeAnnualForYear(IncomeModel row, int year) {
        Integer startYear = yearOf(row.start());
        if (startYear == null) startYear = year;

        int yearsElapsed = Math.max(0, year - startYear);
        double factor = Math.pow(1.0 + row.getEffectiveGrowthRate().doubleValue(), yearsElapsed);
        BigDecimal effectiveMonthly = row.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));

        int monthsActive = monthsActiveInYear(row.start(), row.end(), year);
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActive));
    }

    private BigDecimal chargeAnnualForYear(ChargeModel row, int year, BigDecimal defaultInflation) {
        Integer startYear = yearOf(row.start());
        if (startYear == null) startYear = year;

        BigDecimal growth = row.getEffectiveGrowthRate(defaultInflation);
        int yearsElapsed = Math.max(0, year - startYear);
        double factor = Math.pow(1.0 + growth.doubleValue(), yearsElapsed);

        BigDecimal effectiveMonthly = row.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));
        int monthsActive = monthsActiveInYear(row.start(), row.end(), year);
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActive));
    }

    private BigDecimal placementsMonthlyAnnualForYear(List<PlacementModel> placements, int year) {
        BigDecimal total = BigDecimal.ZERO;
        for (PlacementModel p : placements) {
            BigDecimal monthly = p.getEffectiveMonthly();
            if (monthly.compareTo(BigDecimal.ZERO) == 0) continue;

            LocalDate fromDate = parseDate(p.monthlyFrom());
            LocalDate untilDate = parseDate(p.monthlyUntil());

            int startMonth = 0;
            int endMonth = 11;

            if (fromDate != null) {
                if (year < fromDate.getYear()) continue;
                if (year == fromDate.getYear()) startMonth = fromDate.getMonthValue() - 1;
            }

            if (untilDate != null) {
                if (year > untilDate.getYear()) continue;
                if (year == untilDate.getYear()) endMonth = untilDate.getMonthValue() - 1;
            }

            int months = Math.max(0, endMonth - startMonth + 1);
            total = total.add(monthly.multiply(BigDecimal.valueOf(months)));
        }
        return total;
    }

    private double partsForYear(List<TaxChildModel> children, int exitAge, int year) {
        long attached = children.stream().filter(c -> c.birthYear() != null && (year - c.birthYear()) < exitAge).count();
        double parts = 2.0; // Couple marié/pacsé
        for (int i = 1; i <= attached; i++) {
            parts += (i <= 2) ? 0.5 : 1.0;
        }
        return parts;
    }

    private BigDecimal taxForOnePart(BigDecimal q, List<TaxBracketModel> brackets) {
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
                if (q.compareTo(b.upTo()) <= 0) break;
            } else {
                break;
            }
        }
        return tax;
    }

    private record VariableDetail(BigDecimal total, BigDecimal taxable) {}

    private VariableDetail variableIncomeDetailForYear(BudgetDataModel data, int year) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;

        for (VariableIncomeModel v : data.getEffectiveVariableIncomes()) {
            if (v.startYear() != null && year < v.startYear()) continue;
            if (v.endYear() != null && year > v.endYear()) continue;

            Optional<IncomeModel> refRow = data.getEffectiveIncomes().stream().filter(r -> v.refIncomeLabel() != null && v.refIncomeLabel().equalsIgnoreCase(r.label())).findFirst();
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
        return new VariableDetail(total, taxable);
    }

    private BigDecimal computeVariableIncomeForYear(BudgetDataModel data, int year) {
        return variableIncomeDetailForYear(data, year).total();
    }

    private int findEarliestYear(BudgetDataModel data) {
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

    private int monthsActiveInYear(String startISO, String endISO, int year) {
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

    private Integer yearOf(String dateISO) {
        LocalDate d = parseDate(dateISO);
        return d != null ? d.getYear() : null;
    }

    private LocalDate parseDate(String dateISO) {
        if (dateISO == null || dateISO.isBlank()) return null;
        try {
            if (dateISO.length() == 7) {
                YearMonth ym = YearMonth.parse(dateISO);
                return ym.atDay(1);
            }
            return LocalDate.parse(dateISO.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ResponseEntity<OverviewResponseDto> getOverview(Boolean useConstantEuros) {
        return this.buildOverview(new BudgetDataDto(), Boolean.TRUE.equals(useConstantEuros));
    }

}
