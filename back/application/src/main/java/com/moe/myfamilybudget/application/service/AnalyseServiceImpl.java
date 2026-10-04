package com.moe.myfamilybudget.application.service;

import com.moe.myfamilybudget.application.factory.PatrimoineTransferConverter;
import com.moe.myfamilybudget.api.controller.AnalyseApi;
import com.moe.myfamilybudget.api.model.AnalyseResponseDto;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseInput;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.factory.AnalyseInputFactory;
import com.moe.myfamilybudget.application.factory.PointageInputFactory;
import com.moe.myfamilybudget.application.mapper.AnalyseMapper;
import com.moe.myfamilybudget.application.mapper.BudgetFacadeView;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseCalculator;
import com.moe.myfamilybudget.domain.analysis.model.AnalyseResultModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
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
 * est assemblée directement depuis les ports et {@link SettingsReader} n'est conservé que pour le bloc
 * {@code settings} de la réponse REST (contrat inchangé) et le taux d'inflation des lignes budgétaires.
 */
@RestController
public class AnalyseServiceImpl implements AnalyseApi {

    private final AnalyseMapper analyseMapper;
    private final ObjectifsSettingsService objectifsSettingsService;
    private final AnalyseInputFactory analyseInputFactory;
    private final SettingsReader settingsReader;
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
            SettingsReader settingsReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            TaxReader taxReader,
            LoanReader loanReader,
            BankReader bankReader,
            GoalReader goalReader) {
        this.analyseMapper = analyseMapper;
        this.objectifsSettingsService = objectifsSettingsService;
        this.analyseInputFactory = new AnalyseInputFactory();
        this.settingsReader = settingsReader;
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
        SettingsModel settings = settingsReader.getSettings();
        List<IncomeModel> incomes = budgetReader.getIncomes();
        List<ChargeModel> charges = budgetReader.getCharges();
        List<PlacementModel> placements = patrimoineReader.getPlacements();
        BankImportModel bankImport = bankReader.getBankImport();

        PointageInputFactory.Sources sources = new PointageInputFactory.Sources(
                charges, incomes, placements, settings != null ? settings.inflationRate() : null);
        AnalyseInput input = analyseInputFactory.from(bankImport, sources, monthsBack);
        AnalyseResultModel resultModel = AnalyseCalculator.computeAnalyse(input);

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
