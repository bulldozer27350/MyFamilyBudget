package com.moe.myfamilybudget.application.service;

import com.moe.myfamilybudget.application.factory.PatrimoineTransferConverter;
import com.moe.myfamilybudget.api.controller.AnalyseApi;
import com.moe.myfamilybudget.api.model.AnalyseResponseDto;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.mapper.AnalyseMapper;
import com.moe.myfamilybudget.application.mapper.BudgetFacadeView;
import com.moe.myfamilybudget.application.usecase.analysis.AnalyseResultModel;
import com.moe.myfamilybudget.application.usecase.analysis.AnalysisUseCase;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.application.settings.SettingsComposer;
import com.moe.myfamilybudget.application.settings.SettingsView;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service et Contrôleur REST implémentant le contrat OpenAPI AnalyseApi (Tag: Analyse).
 * Les traitements et calculs sont exécutés exclusivement sur le Modèle Interne du domaine.
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. La réponse recopie la quasi-totalité de {@code BudgetDataModel}
 * (voir {@code OverviewMapper.toBudgetDataDto(BudgetFacadeView)}), donc tous les ports de lecture sont
 * composés ici. RES-010 : le mapper ne reçoit plus {@code BudgetDataModel}, mais une
 * {@code BudgetFacadeView} assemblée ici. SILO-115 : plus aucun {@code BudgetDataModel} ; la vue de façade
 * est assemblée directement depuis les ports. R-52 : le bloc {@code settings} de la réponse REST (contrat
 * inchangé) et le taux d'inflation des lignes budgétaires sont composés directement depuis les cinq ports
 * propriétaires ({@link SettingsComposer}), sans passer par {@code transition.port.SettingsReader}.
 *
 * <p>R-84 (même gabarit que {@code ImpotsServiceImpl}, {@code TresorerieServiceImpl}, SILO-315) : ce
 * service ne garde que la conversion vers le monde HTTP ({@link ResponseEntity}, DTO). Le calcul de
 * l'analyse (construction de l'entrée du moteur, appel du moteur, traduction des types du silo) est
 * porté par le cas d'usage {@link AnalysisUseCase} ({@code application-api}), implémenté par
 * {@code DefaultAnalysisUseCase}. La composition du budget réexposé par l'API ({@code data}, voir
 * {@link BudgetFacadeView}) reste ici : elle est partagée avec Overview et l'import/export
 * ({@code GlobalBudgetSnapshotService}), hors périmètre de ce cas d'usage.
 */
@RestController
public class AnalyseServiceImpl implements AnalyseApi {

    private final AnalyseMapper analyseMapper;
    private final ObjectifsSettingsService objectifsSettingsService;
    private final AnalysisUseCase analysisUseCase;
    private final RetirementSettingsReader retirementSettingsReader;
    private final TaxSettingsReader taxSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final SimulationSettingsReader simulationSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final RetirementReader retirementReader;
    private final TaxReader taxReader;
    private final LoanReader loanReader;
    private final BankReader bankReader;
    private final GoalReader goalReader;

    public AnalyseServiceImpl(
            AnalyseMapper analyseMapper,
            ObjectifsSettingsService objectifsSettingsService,
            AnalysisUseCase analysisUseCase,
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            TaxReader taxReader,
            LoanReader loanReader,
            BankReader bankReader,
            GoalReader goalReader) {
        this.analyseMapper = analyseMapper;
        this.objectifsSettingsService = objectifsSettingsService;
        this.analysisUseCase = analysisUseCase;
        this.retirementSettingsReader = retirementSettingsReader;
        this.taxSettingsReader = taxSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.simulationSettingsReader = simulationSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.retirementReader = retirementReader;
        this.taxReader = taxReader;
        this.loanReader = loanReader;
        this.bankReader = bankReader;
        this.goalReader = goalReader;
    }

    @Override
    public ResponseEntity<AnalyseResponseDto> getAnalyse(Integer monthsBack) {
        SettingsView settings = SettingsComposer.compose(
                retirementSettingsReader.getRetirementSettings(), taxSettingsReader.getTaxSettings(),
                tresorerieSettingsReader.getTresorerieSettings(), simulationSettingsReader.getSimulationSettings(),
                economicAssumptionsReader.getEconomicAssumptions());
        List<IncomeModel> incomes = budgetReader.getIncomes();
        List<ChargeModel> charges = budgetReader.getCharges();
        List<PlacementModel> placements = patrimoineReader.getPlacements();
        BankImportModel bankImport = bankReader.getBankImport();

        AnalyseResultModel resultModel = analysisUseCase.getAnalyse(monthsBack);

        BudgetFacadeView view = new BudgetFacadeView(
                settings, incomes, charges, placements, patrimoineReader.getRealEstate(),
                retirementReader.getRetirement(), taxReader.getTaxChildren(), taxReader.getTaxBrackets(),
                taxReader.getTaxRateOverrides(), taxReader.getTaxActualOverrides(), budgetReader.getOneoffExpenses(),
                PatrimoineTransferConverter.toBudget(patrimoineReader.getTransfers()),
                budgetReader.getVariableIncomes(), budgetReader.getVariableOverrides(),
                bankImport, patrimoineReader.getAssetCategories(), loanReader.getLoans(), goalReader.getGoals(),
                objectifsSettingsService.current());
        AnalyseResponseDto responseDto = analyseMapper.toDto(resultModel, view);

        return ResponseEntity.ok(responseDto);
    }
}
