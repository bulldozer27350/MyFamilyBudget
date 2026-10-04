package com.moe.myfamilybudget.application.service;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.application.mapper.PatrimoineTransferConverter;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.api.controller.OverviewApi;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.application.overview.OverviewCalculationService;
import com.moe.myfamilybudget.application.overview.OverviewInput;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.application.factory.OverviewInputFactory;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.mapper.BudgetFacadeView;
import com.moe.myfamilybudget.application.mapper.OverviewMapper;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.application.factory.TreasuryInputFactory;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import java.math.BigDecimal;
import com.moe.myfamilybudget.application.model.OverviewResultModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;

/**
 * Contrôleur REST de l'aperçu financier global (Overview) : orchestration HTTP uniquement
 * (composition de l'{@link OverviewInput} via {@link OverviewInputFactory}, délégation au moteur
 * {@link OverviewCalculationService}, et mapping du résultat en DTO).
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. Les lectures passent par les ports de domaine ; comme la réponse
 * Overview réexpose l'intégralité du budget (voir {@code OverviewMapper#toBudgetDataDto}), les
 * huit ports sont nécessaires ici, contrairement aux domaines qui n'en composent qu'un
 * sous-ensemble (Trésorerie, Fiscalité).
 *
 * <p>SILO-117 : plus de {@code BudgetDataModel} ; les fragments lus par les ports sont transmis à
 * {@link OverviewInputFactory} sous forme de {@link OverviewInputFactory.Sources} et la vue de façade est
 * assemblée directement. {@link SettingsReader} reste lu pour le bloc {@code settings} de la réponse REST
 * (contrat inchangé) et pour en dériver les paramètres des silos, jusqu'à SILO-118/SILO-120.
 */
@RestController
public class OverviewServiceImpl implements OverviewApi {

    private final OverviewMapper mapper;
    private final OverviewInputFactory inputFactory;
    private final OverviewCalculationService calculationService;
    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;
    private final SettingsReader settingsReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final RetirementReader retirementReader;
    private final TaxReader taxReader;
    private final BankReader bankReader;
    private final LoanReader loanReader;
    private final GoalReader goalReader;

    public OverviewServiceImpl(
            OverviewMapper mapper,
            SettingsReader settingsReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            TaxReader taxReader,
            BankReader bankReader,
            LoanReader loanReader,
            GoalReader goalReader) {
        this.mapper = mapper;
        this.inputFactory = new OverviewInputFactory();
        this.calculationService = new OverviewCalculationService();
        this.retirementInputFactory = new RetirementInputFactory();
        this.retirementCalculationService = new RetirementCalculationService();
        this.settingsReader = settingsReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.retirementReader = retirementReader;
        this.taxReader = taxReader;
        this.bankReader = bankReader;
        this.loanReader = loanReader;
        this.goalReader = goalReader;
    }

    /** Paramètres par défaut historiques, appliqués quand aucun paramètre n'est persisté. */
    private static SettingsModel orDefault(SettingsModel settings) {
        if (settings != null) {
            return settings;
        }
        return new SettingsModel(1985, 64, 85, new BigDecimal("0.02"), "", "manual",
                BigDecimal.ZERO, 21, new BigDecimal("0.10"), null, null, null);
    }

    @Override
    public ResponseEntity<OverviewResponseDto> getOverview(Boolean useConstantEuros) {
        SettingsModel settings = settingsReader.getSettings();
        SettingsModel s = orDefault(settings);
        var incomes = budgetReader.getIncomes();
        var charges = budgetReader.getCharges();
        var placements = patrimoineReader.getPlacements();
        var realEstate = patrimoineReader.getRealEstate();
        var retirement = retirementReader.getRetirement();
        var taxChildren = taxReader.getTaxChildren();
        var taxBrackets = taxReader.getTaxBrackets();
        var taxRateOverrides = taxReader.getTaxRateOverrides();
        var taxActualOverrides = taxReader.getTaxActualOverrides();
        var oneoff = budgetReader.getOneoffExpenses();
        var transfers = PatrimoineTransferConverter.toBudget(patrimoineReader.getTransfers());
        var variableIncomes = budgetReader.getVariableIncomes();
        var variableOverrides = budgetReader.getVariableOverrides();
        var bankImport = bankReader.getBankImport();
        var assetCategories = patrimoineReader.getAssetCategories();
        var loans = loanReader.getLoans();
        var goals = goalReader.getGoals();

        OverviewInputFactory.Sources sources = new OverviewInputFactory.Sources(
                new TreasuryInputFactory.Sources(
                        new RetirementSettingsModel(s.birthYear(), s.retireAge()),
                        new TaxSettingsModel(s.childExitAge(), s.taxAbattement()),
                        new TresorerieSettingsModel(s.pivotDate(), s.pivotMode(), s.startBalance(),
                                s.sweepEnabled(), s.cashCeiling(), s.cashFloor(), s.cashAlertThreshold()),
                        new SimulationSettingsModel(s.simulateUntilAge()),
                        s.inflationRate(),
                        retirement, incomes, charges, placements, oneoff, transfers,
                        variableIncomes, variableOverrides, taxChildren, taxBrackets,
                        taxRateOverrides, taxActualOverrides, bankImport),
                realEstate);

        OverviewInput input = inputFactory.from(sources, Boolean.TRUE.equals(useConstantEuros));
        OverviewResultModel internalResult = calculationService.computeOverview(input);
        BudgetFacadeView view = new BudgetFacadeView(
                settings, incomes, charges, placements, realEstate, retirement, taxChildren, taxBrackets,
                taxRateOverrides, taxActualOverrides, oneoff, transfers, variableIncomes, variableOverrides,
                bankImport, assetCategories, loans, goals, ObjectifsParameters.defaults());
        return ResponseEntity.ok(this.mapper.toDto(internalResult, view));
    }

    /**
     * Conservé pour compatibilité avec les tests existants qui exercent directement le calcul de
     * projection de retraite d'une personne (voir {@code OverviewServiceImplTest} et
     * {@code BusinessLogicIntegrationTest}). Délègue entièrement au moteur de calcul centralisé
     * {@link RetirementCalculationService} via {@link RetirementInputFactory}. SILO-117 : prend les
     * fragments Retraite (et non plus le snapshot global).
     */
    public RetirementProjectionModel computeRetirementProjection(
            RetirementSettingsModel retirementSettings, RetirementModel retirement, List<IncomeModel> incomes,
            int taxChildrenCount, RetirementModel.RetirementPersonModel person) {
        List<RetirementModel.RetirementPersonModel> people = retirement != null ? retirement.getEffectivePeople() : List.of();
        int index = people.indexOf(person);
        RetirementProjection projection = retirementCalculationService.compute(retirementInputFactory.create(
                retirementSettings, retirement, incomes != null ? incomes : List.of(), taxChildrenCount));
        return index >= 0 && index < projection.people().size() ? projection.people().get(index) : null;
    }
}
