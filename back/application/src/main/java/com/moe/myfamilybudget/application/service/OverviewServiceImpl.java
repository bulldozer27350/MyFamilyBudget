package com.moe.myfamilybudget.application.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.application.factory.PatrimoineTransferConverter;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.api.controller.OverviewApi;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.application.overview.OverviewCalculationService;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationService;
import com.moe.myfamilybudget.domain.treasury.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.mapper.BudgetFacadeView;
import com.moe.myfamilybudget.application.mapper.OverviewMapper;
import com.moe.myfamilybudget.application.settings.SettingsComposer;
import com.moe.myfamilybudget.application.settings.SettingsView;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.application.usecase.DefaultOverviewUseCase;
import com.moe.myfamilybudget.application.usecase.overview.OverviewResultModel;
import com.moe.myfamilybudget.application.usecase.overview.OverviewUseCase;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;

/**
 * Contrôleur REST de l'aperçu financier global (Overview) : orchestration HTTP uniquement
 * (délégation du calcul au cas d'usage {@link OverviewUseCase}, assemblage de la vue de façade
 * {@link BudgetFacadeView} réexposée par l'API, et mapping du résultat en DTO).
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. Les lectures passent par les ports de domaine ; comme la réponse
 * Overview réexpose l'intégralité du budget (voir {@code OverviewMapper#toBudgetDataDto}), les
 * huit ports sont nécessaires ici, contrairement aux domaines qui n'en composent qu'un
 * sous-ensemble (Trésorerie, Fiscalité).
 *
 * <p>SILO-117 : plus de {@code BudgetDataModel} ; les fragments lus par les ports sont transmis à
 * la vue de façade {@link BudgetFacadeView} assemblée directement. R-52 : les paramètres des silos
 * (Retraite, Fiscalité, Trésorerie, Simulation, Hypothèses économiques) sont lus directement chez
 * leurs propriétaires ; le bloc {@code settings} de la réponse REST (contrat inchangé) est composé
 * depuis ces mêmes lectures ({@link SettingsComposer}), sans passer par {@code transition.port.SettingsReader}.
 *
 * <p>R-85 (même gabarit que {@code AnalyseServiceImpl}, SILO-316) : ce service ne garde que la conversion
 * vers le monde HTTP ({@link ResponseEntity}, DTO) et la composition du budget réexposé. Le calcul de
 * l'aperçu (construction de l'entrée du moteur, appel du moteur, traduction des types) est porté par le
 * cas d'usage {@link OverviewUseCase} ({@code application-api}), implémenté par {@link DefaultOverviewUseCase}.
 */
@RestController
public class OverviewServiceImpl implements OverviewApi {

    private final OverviewMapper mapper;
    private final OverviewUseCase overviewUseCase;
    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;
    private final RetirementSettingsReader retirementSettingsReader;
    private final TaxSettingsReader taxSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final SimulationSettingsReader simulationSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final RetirementReader retirementReader;
    private final TaxReader taxReader;
    private final BankReader bankReader;
    private final LoanReader loanReader;
    private final GoalReader goalReader;

    @Autowired
    public OverviewServiceImpl(
            OverviewMapper mapper,
            OverviewUseCase overviewUseCase,
            RetirementCalculationService retirementCalculationService,
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            TaxReader taxReader,
            BankReader bankReader,
            LoanReader loanReader,
            GoalReader goalReader) {
        this.mapper = mapper;
        this.overviewUseCase = overviewUseCase;
        this.retirementInputFactory = new RetirementInputFactory();
        this.retirementCalculationService = retirementCalculationService;
        this.retirementSettingsReader = retirementSettingsReader;
        this.taxSettingsReader = taxSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.simulationSettingsReader = simulationSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.retirementReader = retirementReader;
        this.taxReader = taxReader;
        this.bankReader = bankReader;
        this.loanReader = loanReader;
        this.goalReader = goalReader;
    }

    public OverviewServiceImpl(
            OverviewMapper mapper,
            RetirementCalculationService retirementCalculationService,
            TaxCalculationService taxCalculationService,
            PatrimoineProjectionService patrimoineProjectionService,
            TresorerieCalculationService tresorerieCalculationService,
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            TaxReader taxReader,
            BankReader bankReader,
            LoanReader loanReader,
            GoalReader goalReader) {
        this(
                mapper,
                new DefaultOverviewUseCase(
                        new OverviewCalculationService(),
                        retirementCalculationService,
                        taxCalculationService,
                        patrimoineProjectionService,
                        tresorerieCalculationService,
                        retirementSettingsReader,
                        taxSettingsReader,
                        tresorerieSettingsReader,
                        simulationSettingsReader,
                        economicAssumptionsReader,
                        budgetReader,
                        patrimoineReader,
                        retirementReader,
                        taxReader,
                        bankReader),
                retirementCalculationService,
                retirementSettingsReader,
                taxSettingsReader,
                tresorerieSettingsReader,
                simulationSettingsReader,
                economicAssumptionsReader,
                budgetReader,
                patrimoineReader,
                retirementReader,
                taxReader,
                bankReader,
                loanReader,
                goalReader);
    }

    @Override
    public ResponseEntity<OverviewResponseDto> getOverview(Boolean useConstantEuros) {
        RetirementSettingsModel retirementSettings = retirementSettingsReader.getRetirementSettings();
        TaxSettingsModel taxSettings = taxSettingsReader.getTaxSettings();
        TresorerieSettingsModel tresorerieSettings = tresorerieSettingsReader.getTresorerieSettings();
        SimulationSettingsModel simulationSettings = simulationSettingsReader.getSimulationSettings();
        var economicAssumptions = economicAssumptionsReader.getEconomicAssumptions();
        SettingsView settings = SettingsComposer.compose(
                retirementSettings, taxSettings, tresorerieSettings, simulationSettings, economicAssumptions);
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

        OverviewResultModel internalResult = overviewUseCase.getOverview(useConstantEuros);
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
