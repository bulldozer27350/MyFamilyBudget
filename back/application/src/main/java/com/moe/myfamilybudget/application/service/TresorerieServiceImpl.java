package com.moe.myfamilybudget.application.service;

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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.application.factory.PatrimoineTransferConverter;
import com.moe.myfamilybudget.api.controller.TresorerieApi;
import com.moe.myfamilybudget.api.model.TresorerieAjustementRequestDto;
import com.moe.myfamilybudget.api.model.TresorerieResponseDto;
import com.moe.myfamilybudget.domain.treasury.calculation.ChargeProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.IncomeProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.application.factory.TreasuryInputFactory;
import com.moe.myfamilybudget.application.mapper.TresorerieMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.treasury.model.CategoryOptionModel;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageCalculator;
import com.moe.myfamilybudget.domain.treasury.model.RealAverageModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieResultModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSuggestionModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.application.command.TresorerieCommandService;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.transition.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.transition.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieAdjustmentKind;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieLineField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieList;
import com.moe.myfamilybudget.transition.error.UnknownTresorerieFieldException;

/**
 * Contrôleur REST de la trésorerie prévisionnelle (Trésorerie) : orchestration HTTP uniquement
 * (lectures via les ports propriétaires, mutations via {@link TresorerieCommandService},
 * mapping du résultat en DTO).
 *
 * <p>RF-401 (voir doc/architecture/06-domaine-tresorerie.md) : la projection de flux est déléguée
 * à {@link TresorerieCalculationService} via {@link TreasuryInputFactory}. Les moyennes réelles
 * du pointage bancaire, les suggestions budgétaires et la liste des catégories d'options sont
 * assemblées ici au niveau application.
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. SILO-113 : plus de {@code BudgetDataModel} ; chaque fragment est lu chez son
 * propriétaire ({@link RetirementSettingsReader}, {@link TaxSettingsReader},
 * {@link TresorerieSettingsReader}, {@link SimulationSettingsReader}, {@link EconomicAssumptionsReader},
 * {@link TaxReader}, {@link BudgetReader}, {@link PatrimoineReader},
 * {@link BankReader}) et transmis à {@link TreasuryInputFactory} sous forme de
 * {@link TreasuryInputFactory.Sources}.
 */
@RestController
public class TresorerieServiceImpl implements TresorerieApi {

    private final TresorerieMapper mapper;
    private final TresorerieCalculationService calculationService;
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

    public TresorerieServiceImpl(
            TresorerieMapper mapper,
            TresorerieCommandService tresorerieCommandService,
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            TaxReader taxReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            BankReader bankReader) {
        this.mapper = mapper;
        this.tresorerieCommandService = tresorerieCommandService;
        this.calculationService = new TresorerieCalculationService();
        this.treasuryInputFactory = new TreasuryInputFactory();
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
    public ResponseEntity<TresorerieResponseDto> getTresorerie(Boolean useConstantEuros) {
        TresorerieResultModel result = computeTresorerie(readSources(), Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(this.mapper.toTresorerieResponseDto(result));
    }

    @Override
    public ResponseEntity<Object> addTresorerieLigne(String listKey, Object body) {
        Map<String, Object> created = this.tresorerieCommandService.addTresorerieRow(TresorerieList.fromKey(listKey), (Map<String, Object>)body);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Void> updateTresorerieLigne(String listKey, String id, com.moe.myfamilybudget.api.model.UpdateTresorerieLigneRequestDto body) {
        if (body != null) {
            TresorerieList list = TresorerieList.fromKey(listKey);
            TresorerieLineField field = TresorerieLineField.find(body.getField())
                    .orElseThrow(() -> new UnknownTresorerieFieldException(listKey, body.getField()));
            this.tresorerieCommandService.updateTresorerieRow(list, id, field, body.getValue());
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> removeTresorerieLigne(String listKey, String id) {
        this.tresorerieCommandService.removeTresorerieRow(TresorerieList.fromKey(listKey), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> applyTresorerieAjustement(TresorerieAjustementRequestDto request) {
        if (request != null && request.getLineId() != null && request.getKind() != null && request.getNewMonthly() != null) {
            BigDecimal newMonthly = BigDecimal.valueOf(request.getNewMonthly().doubleValue());
            this.tresorerieCommandService.applyTresorerieAjustement(request.getLineId(),
                    TresorerieAdjustmentKind.fromKind(request.getKind()), newMonthly);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Calcule la projection et compose le modèle de résultat complet (RF-401).
     * Visibilité réduite au package ({@code TresorerieServiceImplTest} est dans le même package).
     */
    TresorerieResultModel computeTresorerie(TreasuryInputFactory.Sources data, boolean useConstantEuros) {
        TreasuryProjectionInput input = this.treasuryInputFactory.from(data);
        TreasuryProjection projections = this.calculationService.compute(input);

        int retireYear = input.parameters().retireYear();
        List<Integer> years = projections.years();
        List<Integer> previewYears = projections.previewYears();

        List<String> incomeLabels = data.incomes().stream()
                .map(IncomeModel::label)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<String> variableIncomeLabels = data.variableIncomes().stream()
                .map(VariableIncomeModel::label)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<CategoryOptionModel> categoryOptions = buildCategoryOptions(data.bankImport());
        List<TresorerieSuggestionModel> suggestions = buildTresorerieSuggestions(data);

        return new TresorerieResultModel(
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

    List<CategoryOptionModel> buildCategoryOptions(BankImportModel bankImport) {
        List<CategoryOptionModel> list = new ArrayList<>();
        list.add(new CategoryOptionModel("", "— Non liée —"));

        if (bankImport != null && bankImport.categories() != null) {
            Collator frCollator = Collator.getInstance(Locale.FRENCH);
            frCollator.setStrength(Collator.PRIMARY);

            List<BankImportModel.CategoryModel> sorted = new ArrayList<>(bankImport.categories());
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

    Map<String, RealAverageModel> computeRealAverages(TreasuryInputFactory.Sources data) {
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
        for (ChargeModel c : data.charges()) {
            lineKindMap.put(c.id(), "charge");
        }
        for (IncomeModel i : data.incomes()) {
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

    List<TresorerieSuggestionModel> buildTresorerieSuggestions(TreasuryInputFactory.Sources data) {
        Map<String, RealAverageModel> realAverages = computeRealAverages(data);
        BigDecimal inflationRate = data.inflationRate();
        int currentYear = LocalDate.now().getYear();

        List<TresorerieSuggestionModel> lines = new ArrayList<>();

        for (ChargeModel c : data.charges()) {
            addSuggestionLine(c.id(), c.label(), "charge", c, null, null, realAverages, currentYear, inflationRate, lines);
        }
        for (IncomeModel i : data.incomes()) {
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
            ChargeProjectionInput cInput = TreasuryInputFactory.toCharge(charge);
            budgeted = TresorerieCalculationService.chargeMonthlyForYear(cInput, currentYear, inflationRate);
        } else if ("revenu".equals(kind) && income != null) {
            IncomeProjectionInput iInput = TreasuryInputFactory.toIncome(income);
            budgeted = TresorerieCalculationService.incomeMonthlyForYear(iInput, currentYear);
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
}
