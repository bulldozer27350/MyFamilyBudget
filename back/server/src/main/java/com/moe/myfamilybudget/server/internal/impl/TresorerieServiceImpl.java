package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.TresorerieApi;
import com.moe.myfamilybudget.api.model.TresorerieAjustementRequestDto;
import com.moe.myfamilybudget.api.model.TresorerieResponseDto;
import com.moe.myfamilybudget.server.internal.mapper.TresorerieMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.CashflowYearModel;
import com.moe.myfamilybudget.server.internal.model.CategoryOptionModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.PointageCalculator;
import com.moe.myfamilybudget.server.internal.model.RealAverageModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.model.TresorerieResultModel;
import com.moe.myfamilybudget.server.internal.model.TresorerieSuggestionModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;
import com.moe.myfamilybudget.server.internal.model.VariablePreviewCellModel;
import com.moe.myfamilybudget.server.internal.model.VariablePreviewModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@RestController
public class TresorerieServiceImpl implements TresorerieApi {

    private static final int TRIMESTRES_REQUIS = 172;
    private static final int AGE_TAUX_PLEIN_AUTO = 67;
    private static final BigDecimal DECOTE_PAR_TRIMESTRE = new BigDecimal("0.00625");
    private static final BigDecimal SURCOTE_PAR_TRIMESTRE = new BigDecimal("0.0125");
    private static final BigDecimal TAUX_PLEIN = new BigDecimal("0.50");
    private static final BigDecimal TAUX_MINORE_PLANCHER = new BigDecimal("0.375");
    private static final BigDecimal MAJORATION_3_ENFANTS = new BigDecimal("0.10");

    private final TresorerieMapper mapper;
    private final PersistenceManager persistenceManager;

    public TresorerieServiceImpl(TresorerieMapper mapper, PersistenceManager persistenceManager) {
        this.mapper = mapper;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public ResponseEntity<TresorerieResponseDto> getTresorerie(Boolean useConstantEuros) {
        BudgetDataModel data = this.persistenceManager.getBudgetData();
        TresorerieResultModel result = computeTresorerie(data, Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(this.mapper.toTresorerieResponseDto(result));
    }

    public TresorerieResultModel computeTresorerie(BudgetDataModel data, boolean useConstantEuros) {
        SettingsModel settings = data.settings() != null ? data.settings() : new SettingsModel(
                1985, 64, 85, BigDecimal.ZERO, null, null, BigDecimal.ZERO, 21, BigDecimal.ZERO, new BigDecimal("47100"), new BigDecimal("0.015")
        );

        int retireYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();
        FinancialProjections projections = computeFinancialProjections(data, useConstantEuros);
        List<Integer> years = projections.years();
        List<Integer> previewYears = projections.previewYears();

        List<String> incomeLabels = data.getEffectiveIncomes().stream()
                .map(IncomeModel::label)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<String> variableIncomeLabels = data.getEffectiveVariableIncomes().stream()
                .map(VariableIncomeModel::label)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<CategoryOptionModel> categoryOptions = buildCategoryOptions(data);
        List<TresorerieSuggestionModel> suggestions = buildTresorerieSuggestions(data);

        return new TresorerieResultModel(
                data.getEffectiveIncomes(),
                data.getEffectiveCharges(),
                data.getEffectiveOneoff(),
                data.getEffectiveVariableIncomes(),
                data.getEffectiveVariableOverrides(),
                incomeLabels,
                variableIncomeLabels,
                categoryOptions,
                suggestions,
                retireYear,
                years,
                projections.cashflow(),
                projections.variablePreview(),
                previewYears
        );
    }

    @Override
    public ResponseEntity<Object> addTresorerieLigne(String listKey, Object body) {
        Map<String, Object> created = this.persistenceManager.addTresorerieRow(listKey, (Map<String, Object>)body);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Void> updateTresorerieLigne(String listKey, String id, com.moe.myfamilybudget.api.model.UpdateTresorerieLigneRequestDto body) {
        if (body != null) {
            this.persistenceManager.updateTresorerieRow(listKey, id, body.getField(), body.getValue());
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> removeTresorerieLigne(String listKey, String id) {
        this.persistenceManager.removeTresorerieRow(listKey, id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> applyTresorerieAjustement(TresorerieAjustementRequestDto request) {
        if (request != null && request.getLineId() != null && request.getKind() != null && request.getNewMonthly() != null) {
            BigDecimal newMonthly = BigDecimal.valueOf(request.getNewMonthly().doubleValue());
            this.persistenceManager.applyTresorerieAjustement(request.getLineId(), request.getKind(), newMonthly);
        }
        return ResponseEntity.ok().build();
    }

    // --- Logique Métier Purity Java 21 / BigDecimal ---

    public List<CategoryOptionModel> buildCategoryOptions(BudgetDataModel data) {
        List<CategoryOptionModel> list = new ArrayList<>();
        list.add(new CategoryOptionModel("", "— Non liée —"));

        if (data.bankImport() != null && data.bankImport().categories() != null) {
            Collator frCollator = Collator.getInstance(Locale.FRENCH);
            frCollator.setStrength(Collator.PRIMARY);

            List<BankImportModel.CategoryModel> sorted = new ArrayList<>(data.bankImport().categories());
            sorted.sort((a, b) -> frCollator.compare(
                    a.label() != null ? a.label() : "",
                    b.label() != null ? b.label() : ""
            ));

            for (BankImportModel.CategoryModel cat : sorted) {
                list.add(new CategoryOptionModel(cat.id(), cat.label()));
            }
        }
        return list;
    }

    public Map<String, RealAverageModel> computeRealAverages(BudgetDataModel data) {
        if (data.bankImport() == null) {
            return Map.of();
        }

        List<BankImportModel.MatchingModel> matchings = data.bankImport().matchings() != null
                ? data.bankImport().matchings() : List.of();
        List<BankImportModel.BankTransactionModel> transactions = data.bankImport().transactions() != null
                ? data.bankImport().transactions() : List.of();

        Map<String, BankImportModel.BankTransactionModel> txById = new HashMap<>();
        for (BankImportModel.BankTransactionModel tx : transactions) {
            if (tx.id() != null) {
                txById.put(tx.id(), tx);
            }
        }

        Map<String, String> lineKindMap = new HashMap<>();
        for (ChargeModel c : data.getEffectiveCharges()) {
            lineKindMap.put(c.id(), "charge");
        }
        for (IncomeModel i : data.getEffectiveIncomes()) {
            lineKindMap.put(i.id(), "revenu");
        }
        for (PlacementModel p : data.getEffectivePlacements()) {
            lineKindMap.put(p.id(), "placement");
        }

        record MonthEntry(String month, BigDecimal realAmount) {}
        Map<String, List<MonthEntry>> byLine = new HashMap<>();

        for (BankImportModel.MatchingModel m : matchings) {
            String month = m.month();
            if (m.links() == null) continue;
            for (BankImportModel.MatchingLinkModel l : m.links()) {
                if (l.budgetLineId() == null || l.txIds() == null || l.txIds().isEmpty()) continue;
                String kind = lineKindMap.getOrDefault(l.budgetLineId(), "charge");

                BigDecimal sum = BigDecimal.ZERO;
                for (String refId : l.txIds()) {
                    BigDecimal amt = PointageCalculator.resolveAmount(refId, txById);
                    if (amt != null) {
                        sum = sum.add("revenu".equals(kind) ? amt : amt.negate());
                    }
                }

                byLine.computeIfAbsent(l.budgetLineId(), k -> new ArrayList<>())
                        .add(new MonthEntry(month, sum));
            }
        }

        String todayISO = LocalDate.now().toString().substring(0, 7);
        Map<String, RealAverageModel> result = new HashMap<>();

        for (Map.Entry<String, List<MonthEntry>> entry : byLine.entrySet()) {
            String lineId = entry.getKey();
            List<MonthEntry> sorted = new ArrayList<>(entry.getValue());
            sorted.sort((a, b) -> (b.month() != null ? b.month() : "").compareTo(a.month() != null ? a.month() : ""));

            List<MonthEntry> last3 = sorted.stream()
                    .filter(e -> e.month() != null && e.month().compareTo(todayISO) <= 0)
                    .limit(3)
                    .collect(Collectors.toList());

            List<MonthEntry> last12 = sorted.stream()
                    .filter(e -> e.month() != null && e.month().compareTo(todayISO) <= 0)
                    .limit(12)
                    .collect(Collectors.toList());

            BigDecimal avg3m = null;
            if (!last3.isEmpty()) {
                BigDecimal sum3 = last3.stream().map(MonthEntry::realAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                avg3m = sum3.divide(BigDecimal.valueOf(last3.size()), 10, RoundingMode.HALF_UP);
            }

            BigDecimal avg12m = null;
            if (!last12.isEmpty()) {
                BigDecimal sum12 = last12.stream().map(MonthEntry::realAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                avg12m = sum12.divide(BigDecimal.valueOf(last12.size()), 10, RoundingMode.HALF_UP);
            }

            result.put(lineId, new RealAverageModel(avg3m, avg12m, last12.size()));
        }

        return result;
    }

    public List<TresorerieSuggestionModel> buildTresorerieSuggestions(BudgetDataModel data) {
        Map<String, RealAverageModel> realAverages = computeRealAverages(data);
        BigDecimal inflationRate = data.settings() != null ? data.settings().getEffectiveInflationRate() : new BigDecimal("0.02");
        int currentYear = LocalDate.now().getYear();

        List<TresorerieSuggestionModel> lines = new ArrayList<>();

        for (ChargeModel c : data.getEffectiveCharges()) {
            addSuggestionLine(c.id(), c.label(), "charge", c, null, null, realAverages, currentYear, inflationRate, lines);
        }
        for (IncomeModel i : data.getEffectiveIncomes()) {
            addSuggestionLine(i.id(), i.label(), "revenu", null, i, null, realAverages, currentYear, inflationRate, lines);
        }
        for (PlacementModel p : data.getEffectivePlacements()) {
            addSuggestionLine(p.id(), "Épargne : " + p.label(), "placement", null, null, p, realAverages, currentYear, inflationRate, lines);
        }

        lines.sort((a, b) -> b.ecart().abs().compareTo(a.ecart().abs()));
        return lines;
    }

    private void addSuggestionLine(
            String id,
            String displayLabel,
            String kind,
            ChargeModel charge,
            IncomeModel income,
            PlacementModel placement,
            Map<String, RealAverageModel> realAverages,
            int currentYear,
            BigDecimal inflationRate,
            List<TresorerieSuggestionModel> lines
    ) {
        RealAverageModel avg = realAverages.get(id);
        if (avg == null || avg.avg3m() == null) return;

        BigDecimal budgeted;
        if ("charge".equals(kind) && charge != null) {
            budgeted = chargeMonthlyForYear(charge, currentYear, inflationRate);
        } else if ("revenu".equals(kind) && income != null) {
            budgeted = incomeMonthlyForYear(income, currentYear);
        } else if ("placement".equals(kind) && placement != null) {
            budgeted = placement.getEffectiveMonthly();
        } else {
            budgeted = BigDecimal.ZERO;
        }

        if (budgeted.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal ecart = avg.avg3m().subtract(budgeted);
        BigDecimal ecartPct = ecart.divide(budgeted, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        if (ecart.abs().compareTo(BigDecimal.valueOf(10)) >= 0 || ecartPct.abs().compareTo(BigDecimal.valueOf(5)) >= 0) {
            BigDecimal suggested = avg.avg3m().setScale(2, RoundingMode.HALF_UP);
            lines.add(new TresorerieSuggestionModel(
                    id,
                    displayLabel,
                    kind,
                    budgeted.setScale(2, RoundingMode.HALF_UP),
                    avg.avg3m().setScale(2, RoundingMode.HALF_UP),
                    avg.avg12m() != null ? avg.avg12m().setScale(2, RoundingMode.HALF_UP) : null,
                    ecart.setScale(2, RoundingMode.HALF_UP),
                    ecartPct.setScale(2, RoundingMode.HALF_UP),
                    avg.months(),
                    suggested
            ));
        }
    }

    // --- Financial Calculations Helpers ---

    private record FinancialProjections(
            List<Integer> years,
            List<CashflowYearModel> cashflow,
            List<VariablePreviewModel> variablePreview,
            List<Integer> previewYears
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

            cashflow.add(new CashflowYearModel(
                    year, income, variableIncome, savings, charges, oneoff, transfersY, impots, regularisation, net, balance
            ));
        }

        List<Integer> previewYears = years.stream().limit(4).collect(Collectors.toList());
        List<VariablePreviewModel> variablePreview = new ArrayList<>();

        for (VariableIncomeModel v : data.getEffectiveVariableIncomes()) {
            IncomeModel refRow = data.getEffectiveIncomes().stream()
                    .filter(r -> Objects.equals(r.label(), v.refIncomeLabel()))
                    .findFirst()
                    .orElse(null);

            List<VariablePreviewCellModel> cells = new ArrayList<>();
            for (int y : previewYears) {
                int sY = v.startYear() != null ? v.startYear() : 0;
                int eY = v.endYear() != null ? v.endYear() : 9999;
                if (y < sY || y > eY) {
                    cells.add(new VariablePreviewCellModel(y, null, false));
                    continue;
                }

                BigDecimal refAnnual = refRow != null ? incomeAnnualForYear(refRow, y) : BigDecimal.ZERO;
                BigDecimal forecast = refAnnual.multiply(v.getEffectiveRate());

                VariableOverrideModel override = data.getEffectiveVariableOverrides().stream()
                        .filter(o -> Objects.equals(o.label(), v.label()) && o.year() != null && o.year() == y)
                        .findFirst()
                        .orElse(null);

                BigDecimal amt = override != null ? override.getEffectiveAmount() : forecast;
                boolean isReal = override != null;
                cells.add(new VariablePreviewCellModel(y, amt, isReal));
            }
            variablePreview.add(new VariablePreviewModel(v.label(), cells));
        }

        return new FinancialProjections(years, cashflow, variablePreview, previewYears);
    }

    private BigDecimal computeVariableIncomeForYear(BudgetDataModel data, int year) {
        BigDecimal sum = BigDecimal.ZERO;
        for (VariableIncomeModel v : data.getEffectiveVariableIncomes()) {
            int sY = v.startYear() != null ? v.startYear() : 0;
            int eY = v.endYear() != null ? v.endYear() : 9999;
            if (year < sY || year > eY) continue;

            IncomeModel refRow = data.getEffectiveIncomes().stream()
                    .filter(r -> Objects.equals(r.label(), v.refIncomeLabel()))
                    .findFirst()
                    .orElse(null);

            BigDecimal refAnnual = refRow != null ? incomeAnnualForYear(refRow, year) : BigDecimal.ZERO;
            BigDecimal forecast = refAnnual.multiply(v.getEffectiveRate());

            VariableOverrideModel override = data.getEffectiveVariableOverrides().stream()
                    .filter(o -> Objects.equals(o.label(), v.label()) && o.year() != null && o.year() == year)
                    .findFirst()
                    .orElse(null);

            sum = sum.add(override != null ? override.getEffectiveAmount() : forecast);
        }
        return sum;
    }

    private BigDecimal placementsMonthlyAnnualForYear(List<PlacementModel> placements, int year) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PlacementModel p : placements) {
            Integer sY = p.monthlyFrom() != null ? yearOf(p.monthlyFrom()) : null;
            Integer eY = p.monthlyUntil() != null ? yearOf(p.monthlyUntil()) : null;
            boolean within = (sY == null || year >= sY) && (eY == null || year <= eY);
            if (within) {
                sum = sum.add(p.getEffectiveMonthly().multiply(BigDecimal.valueOf(12)));
            }
        }
        return sum;
    }

    public BigDecimal chargeMonthlyForYear(ChargeModel c, int year, BigDecimal inflationRate) {
        Integer sY = c.start() != null ? yearOf(c.start()) : null;
        Integer eY = c.end() != null ? yearOf(c.end()) : null;
        if (sY == null || eY == null || year < sY || year > eY) return BigDecimal.ZERO;
        BigDecimal growth = chargeEffectiveGrowth(c, inflationRate);
        int elapsed = Math.max(0, year - sY);
        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);
        return c.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));
    }

    public BigDecimal chargeAnnualForYear(ChargeModel c, int year, BigDecimal inflationRate) {
        int m = monthsActiveInYear(c.start(), c.end(), year);
        if (m == 0) return BigDecimal.ZERO;
        Integer sY = c.start() != null ? yearOf(c.start()) : null;
        BigDecimal growth = chargeEffectiveGrowth(c, inflationRate);
        int elapsed = sY != null ? Math.max(0, year - sY) : 0;
        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);
        return c.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor)).multiply(BigDecimal.valueOf(m));
    }

    public BigDecimal chargeEffectiveGrowth(ChargeModel c, BigDecimal inflationRate) {
        if (c.growthRate() != null && BigDecimal.ZERO.compareTo(c.growthRate()) != 0) {
            return c.growthRate();
        }
        return inflationRate != null ? inflationRate : new BigDecimal("0.015");
    }

    public BigDecimal incomeMonthlyForYear(IncomeModel i, int year) {
        Integer sY = i.start() != null ? yearOf(i.start()) : null;
        Integer eY = i.end() != null ? yearOf(i.end()) : null;
        if (sY == null || eY == null || year < sY || year > eY) return BigDecimal.ZERO;
        int elapsed = Math.max(0, year - sY);
        double factor = Math.pow(1.0 + i.getEffectiveGrowthRate().doubleValue(), elapsed);
        return i.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));
    }

    public BigDecimal incomeAnnualForYear(IncomeModel i, int year) {
        int m = monthsActiveInYear(i.start(), i.end(), year);
        if (m == 0) return BigDecimal.ZERO;
        Integer sY = i.start() != null ? yearOf(i.start()) : null;
        int elapsed = sY != null ? Math.max(0, year - sY) : 0;
        double factor = Math.pow(1.0 + i.getEffectiveGrowthRate().doubleValue(), elapsed);
        return i.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor)).multiply(BigDecimal.valueOf(m));
    }

    private record TaxYearlyInfo(int year, BigDecimal withheld, BigDecimal taxActual) {}

    private List<TaxYearlyInfo> computeTaxYearly(BudgetDataModel data, List<Integer> years, List<IncomeModel> effectiveIncomes) {
        List<TaxBracketModel> brackets = !data.getEffectiveTaxBrackets().isEmpty()
                ? data.getEffectiveTaxBrackets()
                : List.of(
                new TaxBracketModel("tb_1", new BigDecimal("11294"), BigDecimal.ZERO),
                new TaxBracketModel("tb_2", new BigDecimal("28797"), new BigDecimal("0.11")),
                new TaxBracketModel("tb_3", new BigDecimal("82341"), new BigDecimal("0.30")),
                new TaxBracketModel("tb_4", new BigDecimal("177106"), new BigDecimal("0.41")),
                new TaxBracketModel("tb_5", null, new BigDecimal("0.45"))
        );

        int exitAge = data.settings() != null ? data.settings().getEffectiveChildExitAge() : 21;
        BigDecimal abattement = data.settings() != null ? data.settings().getEffectiveTaxAbattement() : BigDecimal.ZERO;

        List<TaxYearlyInfo> result = new ArrayList<>();
        for (int year : years) {
            BigDecimal regularIncome = BigDecimal.ZERO;
            for (IncomeModel i : effectiveIncomes) {
                regularIncome = regularIncome.add(incomeAnnualForYear(i, year));
            }

            BigDecimal varTaxable = computeVariableTaxableForYear(data, year);
            BigDecimal grossPayroll = regularIncome.add(varTaxable);
            BigDecimal taxableIncome = grossPayroll.multiply(BigDecimal.ONE.subtract(abattement));

            BigDecimal parts = partsForYear(data.getEffectiveTaxChildren(), exitAge, year);
            BigDecimal taxForecast = BigDecimal.ZERO;
            if (parts.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal incomePerPart = taxableIncome.divide(parts, 10, RoundingMode.HALF_UP);
                taxForecast = taxForOnePart(incomePerPart, brackets).multiply(parts);
            }

            TaxActualOverrideModel actOverride = data.getEffectiveTaxActualOverrides().stream()
                    .filter(o -> o.year() != null && o.year() == year).findFirst().orElse(null);
            BigDecimal taxActual = actOverride != null && actOverride.amount() != null ? actOverride.amount() : taxForecast;

            BigDecimal rateForecast = grossPayroll.compareTo(BigDecimal.ZERO) > 0
                    ? taxForecast.divide(grossPayroll, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            TaxRateOverrideModel rateOverride = data.getEffectiveTaxRateOverrides().stream()
                    .filter(o -> o.year() != null && o.year() == year).findFirst().orElse(null);
            BigDecimal ratePAS = rateOverride != null && rateOverride.rate() != null ? rateOverride.rate() : rateForecast;

            BigDecimal withheld = ratePAS.multiply(grossPayroll);
            result.add(new TaxYearlyInfo(year, withheld, taxActual));
        }
        return result;
    }

    private BigDecimal computeVariableTaxableForYear(BudgetDataModel data, int year) {
        BigDecimal taxable = BigDecimal.ZERO;
        for (VariableIncomeModel v : data.getEffectiveVariableIncomes()) {
            int sY = v.startYear() != null ? v.startYear() : 0;
            int eY = v.endYear() != null ? v.endYear() : 9999;
            if (year < sY || year > eY) continue;

            IncomeModel refRow = data.getEffectiveIncomes().stream()
                    .filter(r -> Objects.equals(r.label(), v.refIncomeLabel()))
                    .findFirst()
                    .orElse(null);

            BigDecimal refAnnual = refRow != null ? incomeAnnualForYear(refRow, year) : BigDecimal.ZERO;
            BigDecimal forecast = refAnnual.multiply(v.getEffectiveRate());

            VariableOverrideModel override = data.getEffectiveVariableOverrides().stream()
                    .filter(o -> Objects.equals(o.label(), v.label()) && o.year() != null && o.year() == year)
                    .findFirst()
                    .orElse(null);

            BigDecimal amount = override != null ? override.getEffectiveAmount() : forecast;
            String isTaxable = override != null && override.taxable() != null && !override.taxable().isBlank()
                    ? override.taxable() : v.taxable();

            if (!"Non".equalsIgnoreCase(isTaxable)) {
                taxable = taxable.add(amount);
            }
        }
        return taxable;
    }

    private BigDecimal partsForYear(List<TaxChildModel> children, int exitAge, int year) {
        long activeCount = children.stream()
                .filter(c -> c.birthYear() != null && (year - c.birthYear() < exitAge))
                .count();

        if (activeCount == 0) return new BigDecimal("2.0");
        if (activeCount == 1) return new BigDecimal("2.5");
        if (activeCount == 2) return new BigDecimal("3.0");
        return BigDecimal.valueOf(3.0 + (activeCount - 2) * 1.0);
    }

    private BigDecimal taxForOnePart(BigDecimal income, List<TaxBracketModel> brackets) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal prevUpTo = BigDecimal.ZERO;

        for (TaxBracketModel b : brackets) {
            BigDecimal rate = b.rate() != null ? b.rate() : BigDecimal.ZERO;
            if (b.upTo() == null) {
                if (income.compareTo(prevUpTo) > 0) {
                    tax = tax.add(income.subtract(prevUpTo).multiply(rate));
                }
                break;
            }
            BigDecimal upTo = b.upTo();
            if (income.compareTo(prevUpTo) <= 0) break;
            BigDecimal taxableChunk = income.min(upTo).subtract(prevUpTo);
            tax = tax.add(taxableChunk.multiply(rate));
            prevUpTo = upTo;
        }
        return tax;
    }

    private List<IncomeModel> pensionIncomeRows(BudgetDataModel data, int retireYear, int lastYear) {
        if (data.retirement() == null || data.retirement().people() == null) return List.of();
        BigDecimal inflation = data.settings() != null ? data.settings().getEffectiveInflationRate() : BigDecimal.ZERO;

        List<IncomeModel> list = new ArrayList<>();
        for (RetirementModel.RetirementPersonModel person : data.retirement().people()) {
            RetirementProjection proj = computeRetirementProjection(data, person, retireYear);
            if (proj.pensionTotaleMensuelle().compareTo(BigDecimal.ZERO) > 0) {
                String name = person.name() != null ? person.name() : "retraite";
                list.add(new IncomeModel(
                        "pension-" + person.id(),
                        "Pension " + name + " (auto)",
                        proj.pensionTotaleMensuelle(),
                        retireYear + "-01-01",
                        Math.max(retireYear, lastYear) + "-12-31",
                        inflation,
                        "",
                        ""
                ));
            }
        }
        return list;
    }

    private record RetirementProjection(BigDecimal pensionTotaleMensuelle) {}

    private RetirementProjection computeRetirementProjection(BudgetDataModel data, RetirementModel.RetirementPersonModel person, int retireYear) {
        int birthYear = person.birthYear() != null ? person.birthYear() : 1985;
        int trimestresValides = person.trimestresValides() != null ? person.trimestresValides() : 0;
        Integer trimestresDateYear = person.trimestresDate() != null ? yearOf(person.trimestresDate()) : null;
        if (trimestresDateYear == null) trimestresDateYear = LocalDate.now().getYear() - 1;

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
            BigDecimal decote = BigDecimal.valueOf(trimestresDecote).multiply(DECOTE_PAR_TRIMESTRE);
            tauxApplique = TAUX_PLEIN.subtract(decote).max(TAUX_MINORE_PLANCHER);
        } else if (trimestresEstimesDepart > TRIMESTRES_REQUIS) {
            BigDecimal surcote = BigDecimal.valueOf(trimestresEstimesDepart - TRIMESTRES_REQUIS).multiply(SURCOTE_PAR_TRIMESTRE);
            tauxApplique = TAUX_PLEIN.add(surcote);
        }

        Map<Integer, BigDecimal> byYear = new HashMap<>();
        if (person.salaryHistory() != null) {
            for (RetirementModel.SalaryHistoryModel h : person.salaryHistory()) {
                if (h.year() != null && h.salary() != null && h.salary().compareTo(BigDecimal.ZERO) > 0) {
                    byYear.put(h.year(), h.salary());
                }
            }
        }
        for (int y = trimestresDateYear + 1; y <= retireYear - 1; y++) {
            BigDecimal s = projectedAnnualSalary(data, person, y);
            if (s.compareTo(BigDecimal.ZERO) > 0 && !byYear.containsKey(y)) {
                byYear.put(y, s);
            }
        }

        List<Map.Entry<Integer, BigDecimal>> sortedSalaries = new ArrayList<>(byYear.entrySet());
        sortedSalaries.sort((a, b) -> b.getKey().compareTo(a.getKey()));
        List<Map.Entry<Integer, BigDecimal>> last25 = sortedSalaries.stream().limit(25).collect(Collectors.toList());

        BigDecimal sam = BigDecimal.ZERO;
        if (!last25.isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Map.Entry<Integer, BigDecimal> e : last25) {
                BigDecimal pass = passForYear(data, e.getKey());
                sum = sum.add(e.getValue().min(pass));
            }
            sam = sum.divide(BigDecimal.valueOf(last25.size()), 10, RoundingMode.HALF_UP);
        }

        BigDecimal majoration = data.getEffectiveTaxChildren().size() >= 3
                ? BigDecimal.ONE.add(MAJORATION_3_ENFANTS)
                : BigDecimal.ONE;

        BigDecimal ratioTrimestres = BigDecimal.valueOf(Math.min(trimestresEstimesDepart, TRIMESTRES_REQUIS))
                .divide(BigDecimal.valueOf(TRIMESTRES_REQUIS), 10, RoundingMode.HALF_UP);

        BigDecimal pensionBaseAnnuelle = sam.multiply(tauxApplique).multiply(ratioTrimestres).multiply(majoration);

        BigDecimal pointsActuels = person.agircPoints() != null ? person.agircPoints() : BigDecimal.ZERO;
        BigDecimal ratioPoints = person.ratioPointsParEuro() != null ? person.ratioPointsParEuro() : new BigDecimal("0.0051");

        BigDecimal pointsFuturs = BigDecimal.ZERO;
        for (int y = trimestresDateYear + 1; y <= retireYear - 1; y++) {
            BigDecimal s = projectedAnnualSalary(data, person, y);
            pointsFuturs = pointsFuturs.add(s.multiply(ratioPoints));
        }
        BigDecimal pointsEstimes = pointsActuels.add(pointsFuturs);
        BigDecimal valeurPoint = agircPointValueForYear(data, retireYear);
        BigDecimal pensionComplementaire = pointsEstimes.multiply(valeurPoint).multiply(majoration);

        BigDecimal pensionTotaleAnnuelle = pensionBaseAnnuelle.add(pensionComplementaire);
        return new RetirementProjection(pensionTotaleAnnuelle.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP));
    }

    private BigDecimal projectedAnnualSalary(BudgetDataModel data, RetirementModel.RetirementPersonModel person, int year) {
        String label = person.incomeLabel();
        if (label == null || label.isBlank()) return BigDecimal.ZERO;
        IncomeModel inc = data.getEffectiveIncomes().stream().filter(i -> Objects.equals(i.label(), label)).findFirst().orElse(null);
        if (inc == null) return BigDecimal.ZERO;
        return incomeAnnualForYear(inc, year);
    }

    private BigDecimal passForYear(BudgetDataModel data, int year) {
        RetirementModel ret = data.retirement();
        BigDecimal pass2026 = (ret != null && ret.pass2026() != null) ? ret.pass2026() : new BigDecimal("47100");
        BigDecimal growth = (ret != null && ret.passGrowthRate() != null) ? ret.passGrowthRate() : new BigDecimal("0.015");
        int elapsed = Math.max(0, year - 2026);
        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);
        return pass2026.multiply(BigDecimal.valueOf(factor));
    }

    private BigDecimal agircPointValueForYear(BudgetDataModel data, int year) {
        RetirementModel ret = data.retirement();
        BigDecimal ptVal = (ret != null && ret.agircPointValue() != null) ? ret.agircPointValue() : new BigDecimal("1.4386");
        BigDecimal growth = (ret != null && ret.agircPointGrowthRate() != null) ? ret.agircPointGrowthRate() : new BigDecimal("0.015");
        int refYear = 2026;
        if (ret != null && ret.agircPointDateGlobal() != null) {
            Integer y = yearOf(ret.agircPointDateGlobal());
            if (y != null) refYear = y;
        }
        int elapsed = Math.max(0, year - refYear);
        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);
        return ptVal.multiply(BigDecimal.valueOf(factor));
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
}
