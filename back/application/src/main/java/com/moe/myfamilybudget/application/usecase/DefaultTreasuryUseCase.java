package com.moe.myfamilybudget.application.usecase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.command.TresorerieCommandService;
import com.moe.myfamilybudget.application.factory.PatrimoineTransferConverter;
import com.moe.myfamilybudget.application.factory.TreasuryInputFactory;
import com.moe.myfamilybudget.application.usecase.treasury.CashflowYearModel;
import com.moe.myfamilybudget.application.usecase.treasury.CategoryOptionModel;
import com.moe.myfamilybudget.application.usecase.treasury.ChargeModel;
import com.moe.myfamilybudget.application.usecase.treasury.IncomeModel;
import com.moe.myfamilybudget.application.usecase.treasury.OneOffExpenseModel;
import com.moe.myfamilybudget.application.usecase.treasury.TresorerieResultModel;
import com.moe.myfamilybudget.application.usecase.treasury.TresorerieSuggestionModel;
import com.moe.myfamilybudget.application.usecase.treasury.TreasuryUseCase;
import com.moe.myfamilybudget.application.usecase.treasury.VariableIncomeModel;
import com.moe.myfamilybudget.application.usecase.treasury.VariableOverrideModel;
import com.moe.myfamilybudget.application.usecase.treasury.VariablePreviewCellModel;
import com.moe.myfamilybudget.application.usecase.treasury.VariablePreviewModel;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationService;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.calculation.ChargeProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.IncomeProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.domain.treasury.model.RealAverageModel;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieAdjustmentKind;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieLineField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieList;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.UnknownTresorerieFieldException;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Implémentation du cas d'usage Trésorerie (R-82, même gabarit que {@code DefaultTaxUseCase} /
 * {@code DefaultWealthUseCase}, SILO-313).
 *
 * <p>La logique de composition de la réponse (anciennement {@code TresorerieServiceImpl}) vit ici,
 * sans {@code ResponseEntity} ni DTO : elle lit ses fragments via les ports des silos
 * ({@link RetirementSettingsReader}, {@link TaxSettingsReader}, {@link TresorerieSettingsReader},
 * {@link SimulationSettingsReader}, {@link EconomicAssumptionsReader}, {@link TaxReader},
 * {@link BudgetReader}, {@link PatrimoineReader}, {@link BankReader}), construit l'entrée du moteur par
 * {@link TreasuryInputFactory}, appelle {@link TresorerieCalculationService} /
 * {@link PointageCalculationService} puis traduit les types des silos vers les modèles de
 * {@code application-api}. L'écriture passe par {@link TresorerieCommandService} ; l'interprétation du
 * {@code listKey}, du {@code field} et du {@code kind} (DB-050) se fait ici, à la frontière du cas
 * d'usage, comme auparavant à la frontière REST.
 */
@Service
public class DefaultTreasuryUseCase implements TreasuryUseCase {

    private final TresorerieCalculationService calculationService;
    private final PointageCalculationService pointageCalculationService;
    private final TreasuryInputFactory treasuryInputFactory;
    private final TresorerieCommandService tresorerieCommandService;
    private final RetirementSettingsReader retirementSettingsReader;
    private final TaxSettingsReader taxSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final SimulationSettingsReader simulationSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final TaxReader taxReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final BankReader bankReader;

    public DefaultTreasuryUseCase(
            TresorerieCommandService tresorerieCommandService,
            RetirementCalculationService retirementCalculationService,
            TaxCalculationService taxCalculationService,
            TresorerieCalculationService tresorerieCalculationService,
            PointageCalculationService pointageCalculationService,
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            TaxReader taxReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            BankReader bankReader) {
        this.tresorerieCommandService = tresorerieCommandService;
        this.calculationService = tresorerieCalculationService;
        this.pointageCalculationService = pointageCalculationService;
        this.treasuryInputFactory = new TreasuryInputFactory(retirementCalculationService, taxCalculationService);
        this.retirementSettingsReader = retirementSettingsReader;
        this.taxSettingsReader = taxSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.simulationSettingsReader = simulationSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
        this.taxReader = taxReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.bankReader = bankReader;
    }

    private TreasuryInputFactory.Sources readSources() {
        return new TreasuryInputFactory.Sources(
                retirementSettingsReader.getRetirementSettings(),
                taxSettingsReader.getTaxSettings(),
                tresorerieSettingsReader.getTresorerieSettings(),
                simulationSettingsReader.getSimulationSettings(),
                economicAssumptionsReader.getEconomicAssumptions().inflationRate(),
                null, // /tresorerie ne projette aucune pension (comportement historique, cf. VT-110)
                budgetReader.getIncomes(),
                budgetReader.getCharges(),
                patrimoineReader.getPlacements(),
                budgetReader.getOneoffExpenses(),
                PatrimoineTransferConverter.toBudget(patrimoineReader.getTransfers()),
                budgetReader.getVariableIncomes(),
                budgetReader.getVariableOverrides(),
                taxReader.getTaxChildren(),
                taxReader.getTaxBrackets(),
                taxReader.getTaxRateOverrides(),
                taxReader.getTaxActualOverrides(),
                bankReader.getBankImport());
    }

    @Override
    public TresorerieResultModel getTresorerie(boolean useConstantEuros) {
        return toResultModel(computeTresorerie(readSources(), useConstantEuros));
    }

    @Override
    public Map<String, Object> addTresorerieLigne(String listKey, Map<String, Object> row) {
        return tresorerieCommandService.addTresorerieRow(TresorerieList.fromKey(listKey), row);
    }

    @Override
    public void updateTresorerieLigne(String listKey, String id, String field, Object value) {
        TresorerieLineField lineField = TresorerieLineField.find(field)
                .orElseThrow(() -> new UnknownTresorerieFieldException(listKey, field));
        tresorerieCommandService.updateTresorerieRow(TresorerieList.fromKey(listKey), id, lineField, value);
    }

    @Override
    public void removeTresorerieLigne(String listKey, String id) {
        tresorerieCommandService.removeTresorerieRow(TresorerieList.fromKey(listKey), id);
    }

    @Override
    public void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly) {
        tresorerieCommandService.applyTresorerieAjustement(lineId, TresorerieAdjustmentKind.fromKind(kind), newMonthly);
    }

    /**
     * Calcule la projection et compose le modèle de résultat complet (RF-401).
     */
    private com.moe.myfamilybudget.domain.treasury.model.TresorerieResultModel computeTresorerie(
            TreasuryInputFactory.Sources data, boolean useConstantEuros) {
        TreasuryProjectionInput input = this.treasuryInputFactory.from(data);
        TreasuryProjection projections = this.calculationService.compute(input);

        int retireYear = input.parameters().retireYear();
        List<Integer> years = projections.years();
        List<Integer> previewYears = projections.previewYears();

        List<String> incomeLabels = data.incomes().stream()
                .map(com.moe.myfamilybudget.domain.treasury.model.IncomeModel::label)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<String> variableIncomeLabels = data.variableIncomes().stream()
                .map(com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel::label)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<com.moe.myfamilybudget.domain.treasury.model.CategoryOptionModel> categoryOptions =
                buildCategoryOptions(data.bankImport());
        List<com.moe.myfamilybudget.domain.treasury.model.TresorerieSuggestionModel> suggestions =
                buildTresorerieSuggestions(data);

        return new com.moe.myfamilybudget.domain.treasury.model.TresorerieResultModel(
                data.incomes(),
                data.charges(),
                data.oneoff(),
                data.variableIncomes(),
                data.variableOverrides(),
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

    private List<com.moe.myfamilybudget.domain.treasury.model.CategoryOptionModel> buildCategoryOptions(
            BankImportModel bankImport) {
        List<com.moe.myfamilybudget.domain.treasury.model.CategoryOptionModel> list = new ArrayList<>();
        list.add(new com.moe.myfamilybudget.domain.treasury.model.CategoryOptionModel("", "— Non liée —"));

        if (bankImport != null && bankImport.categories() != null) {
            Collator frCollator = Collator.getInstance(Locale.FRENCH);
            frCollator.setStrength(Collator.PRIMARY);

            List<BankImportModel.CategoryModel> sorted = new ArrayList<>(bankImport.categories());
            sorted.sort((a, b) -> frCollator.compare(
                    a.label() != null ? a.label() : "",
                    b.label() != null ? b.label() : ""
            ));

            for (BankImportModel.CategoryModel cat : sorted) {
                list.add(new com.moe.myfamilybudget.domain.treasury.model.CategoryOptionModel(cat.id(), cat.label()));
            }
        }
        return list;
    }

    private Map<String, RealAverageModel> computeRealAverages(TreasuryInputFactory.Sources data) {
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
        for (com.moe.myfamilybudget.domain.treasury.model.ChargeModel c : data.charges()) {
            lineKindMap.put(c.id(), "charge");
        }
        for (com.moe.myfamilybudget.domain.treasury.model.IncomeModel i : data.incomes()) {
            lineKindMap.put(i.id(), "revenu");
        }
        for (PlacementModel p : data.placements()) {
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
                    BigDecimal amt = pointageCalculationService.resolveAmount(refId, txById);
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

    private List<com.moe.myfamilybudget.domain.treasury.model.TresorerieSuggestionModel> buildTresorerieSuggestions(
            TreasuryInputFactory.Sources data) {
        Map<String, RealAverageModel> realAverages = computeRealAverages(data);
        BigDecimal inflationRate = data.inflationRate();
        int currentYear = LocalDate.now().getYear();

        List<com.moe.myfamilybudget.domain.treasury.model.TresorerieSuggestionModel> lines = new ArrayList<>();

        for (com.moe.myfamilybudget.domain.treasury.model.ChargeModel c : data.charges()) {
            addSuggestionLine(c.id(), c.label(), "charge", c, null, null, realAverages, currentYear, inflationRate, lines);
        }
        for (com.moe.myfamilybudget.domain.treasury.model.IncomeModel i : data.incomes()) {
            addSuggestionLine(i.id(), i.label(), "revenu", null, i, null, realAverages, currentYear, inflationRate, lines);
        }
        for (PlacementModel p : data.placements()) {
            addSuggestionLine(p.id(), "Épargne : " + p.label(), "placement", null, null, p, realAverages, currentYear, inflationRate, lines);
        }

        lines.sort((a, b) -> b.ecart().abs().compareTo(a.ecart().abs()));
        return lines;
    }

    private void addSuggestionLine(
            String id,
            String displayLabel,
            String kind,
            com.moe.myfamilybudget.domain.treasury.model.ChargeModel charge,
            com.moe.myfamilybudget.domain.treasury.model.IncomeModel income,
            PlacementModel placement,
            Map<String, RealAverageModel> realAverages,
            int currentYear,
            BigDecimal inflationRate,
            List<com.moe.myfamilybudget.domain.treasury.model.TresorerieSuggestionModel> lines
    ) {
        RealAverageModel avg = realAverages.get(id);
        if (avg == null || avg.avg3m() == null) return;

        BigDecimal budgeted;
        if ("charge".equals(kind) && charge != null) {
            ChargeProjectionInput cInput = TreasuryInputFactory.toCharge(charge);
            budgeted = calculationService.chargeMonthlyForYear(cInput, currentYear, inflationRate);
        } else if ("revenu".equals(kind) && income != null) {
            IncomeProjectionInput iInput = TreasuryInputFactory.toIncome(income);
            budgeted = calculationService.incomeMonthlyForYear(iInput, currentYear);
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
            lines.add(new com.moe.myfamilybudget.domain.treasury.model.TresorerieSuggestionModel(
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

    private static TresorerieResultModel toResultModel(
            com.moe.myfamilybudget.domain.treasury.model.TresorerieResultModel m) {
        return new TresorerieResultModel(
                toIncomes(m.incomes()),
                toCharges(m.charges()),
                toOneoff(m.oneoff()),
                toVariableIncomes(m.variableIncomes()),
                toVariableOverrides(m.variableOverrides()),
                m.incomeLabels() != null ? List.copyOf(m.incomeLabels()) : List.of(),
                m.variableIncomeLabels() != null ? List.copyOf(m.variableIncomeLabels()) : List.of(),
                toCategoryOptions(m.categoryOptions()),
                toSuggestions(m.suggestions()),
                m.retireYear(),
                m.years() != null ? List.copyOf(m.years()) : List.of(),
                toCashflow(m.cashflow()),
                toVariablePreview(m.variablePreview()),
                m.previewYears() != null ? List.copyOf(m.previewYears()) : List.of());
    }

    private static List<IncomeModel> toIncomes(List<com.moe.myfamilybudget.domain.treasury.model.IncomeModel> incomes) {
        if (incomes == null) {
            return List.of();
        }
        List<IncomeModel> result = new ArrayList<>(incomes.size());
        for (com.moe.myfamilybudget.domain.treasury.model.IncomeModel i : incomes) {
            result.add(i == null ? null : new IncomeModel(
                    i.id(), i.label(), i.monthly(), i.start(), i.end(), i.growthRate(), i.categoryId(), i.notes()));
        }
        return result;
    }

    private static List<ChargeModel> toCharges(List<com.moe.myfamilybudget.domain.treasury.model.ChargeModel> charges) {
        if (charges == null) {
            return List.of();
        }
        List<ChargeModel> result = new ArrayList<>(charges.size());
        for (com.moe.myfamilybudget.domain.treasury.model.ChargeModel c : charges) {
            result.add(c == null ? null : new ChargeModel(
                    c.id(), c.label(), c.monthly(), c.start(), c.end(), c.growthRate(), c.categoryId(), c.notes()));
        }
        return result;
    }

    private static List<OneOffExpenseModel> toOneoff(
            List<com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel> oneoff) {
        if (oneoff == null) {
            return List.of();
        }
        List<OneOffExpenseModel> result = new ArrayList<>(oneoff.size());
        for (com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel o : oneoff) {
            result.add(o == null ? null : new OneOffExpenseModel(o.id(), o.label(), o.date(), o.amount(), o.notes()));
        }
        return result;
    }

    private static List<VariableIncomeModel> toVariableIncomes(
            List<com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel> variableIncomes) {
        if (variableIncomes == null) {
            return List.of();
        }
        List<VariableIncomeModel> result = new ArrayList<>(variableIncomes.size());
        for (com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel v : variableIncomes) {
            result.add(v == null ? null : new VariableIncomeModel(
                    v.id(), v.label(), v.refIncomeLabel(), v.rate(), v.startYear(), v.endYear(), v.taxable(),
                    v.type(), v.notes()));
        }
        return result;
    }

    private static List<VariableOverrideModel> toVariableOverrides(
            List<com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel> variableOverrides) {
        if (variableOverrides == null) {
            return List.of();
        }
        List<VariableOverrideModel> result = new ArrayList<>(variableOverrides.size());
        for (com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel v : variableOverrides) {
            result.add(v == null ? null : new VariableOverrideModel(
                    v.id(), v.label(), v.year(), v.amount(), v.taxable(), v.notes()));
        }
        return result;
    }

    private static List<CategoryOptionModel> toCategoryOptions(
            List<com.moe.myfamilybudget.domain.treasury.model.CategoryOptionModel> categoryOptions) {
        if (categoryOptions == null) {
            return List.of();
        }
        List<CategoryOptionModel> result = new ArrayList<>(categoryOptions.size());
        for (com.moe.myfamilybudget.domain.treasury.model.CategoryOptionModel c : categoryOptions) {
            result.add(c == null ? null : new CategoryOptionModel(c.value(), c.label()));
        }
        return result;
    }

    private static List<TresorerieSuggestionModel> toSuggestions(
            List<com.moe.myfamilybudget.domain.treasury.model.TresorerieSuggestionModel> suggestions) {
        if (suggestions == null) {
            return List.of();
        }
        List<TresorerieSuggestionModel> result = new ArrayList<>(suggestions.size());
        for (com.moe.myfamilybudget.domain.treasury.model.TresorerieSuggestionModel s : suggestions) {
            result.add(s == null ? null : new TresorerieSuggestionModel(
                    s.id(), s.label(), s.kind(), s.budgeted(), s.avg3m(), s.avg12m(), s.ecart(), s.ecartPct(),
                    s.months(), s.suggested()));
        }
        return result;
    }

    private static List<CashflowYearModel> toCashflow(
            List<com.moe.myfamilybudget.domain.treasury.model.CashflowYearModel> cashflow) {
        if (cashflow == null) {
            return List.of();
        }
        List<CashflowYearModel> result = new ArrayList<>(cashflow.size());
        for (com.moe.myfamilybudget.domain.treasury.model.CashflowYearModel c : cashflow) {
            result.add(c == null ? null : new CashflowYearModel(
                    c.year(), c.income(), c.variableIncome(), c.savings(), c.charges(), c.oneoff(), c.transfersY(),
                    c.impots(), c.regularisation(), c.net(), c.balance()));
        }
        return result;
    }

    private static List<VariablePreviewModel> toVariablePreview(
            List<com.moe.myfamilybudget.domain.treasury.model.VariablePreviewModel> variablePreview) {
        if (variablePreview == null) {
            return List.of();
        }
        List<VariablePreviewModel> result = new ArrayList<>(variablePreview.size());
        for (com.moe.myfamilybudget.domain.treasury.model.VariablePreviewModel v : variablePreview) {
            result.add(v == null ? null : new VariablePreviewModel(v.label(), toCells(v.cells())));
        }
        return result;
    }

    private static List<VariablePreviewCellModel> toCells(
            List<com.moe.myfamilybudget.domain.treasury.model.VariablePreviewCellModel> cells) {
        if (cells == null) {
            return List.of();
        }
        List<VariablePreviewCellModel> result = new ArrayList<>(cells.size());
        for (com.moe.myfamilybudget.domain.treasury.model.VariablePreviewCellModel c : cells) {
            result.add(c == null ? null : new VariablePreviewCellModel(c.year(), c.amount(), c.isReal()));
        }
        return result;
    }
}
