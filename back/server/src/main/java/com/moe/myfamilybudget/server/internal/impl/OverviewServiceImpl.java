package com.moe.myfamilybudget.server.internal.impl;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.OverviewApi;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.server.internal.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.server.internal.calculation.OverviewCalculationService;
import com.moe.myfamilybudget.server.internal.calculation.OverviewInput;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.server.internal.factory.OverviewInputFactory;
import com.moe.myfamilybudget.server.internal.factory.RetirementInputFactory;
import com.moe.myfamilybudget.server.internal.mapper.BudgetFacadeView;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.OverviewResultModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.server.internal.port.BankReader;
import com.moe.myfamilybudget.server.internal.port.BudgetReader;
import com.moe.myfamilybudget.server.internal.port.GoalReader;
import com.moe.myfamilybudget.server.internal.port.LoanReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;
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

    private BudgetDataModel composeBudgetData() {
        return new BudgetDataModel(
                settingsReader.getSettings(), budgetReader.getIncomes(), budgetReader.getCharges(),
                patrimoineReader.getPlacements(), patrimoineReader.getRealEstate(), retirementReader.getRetirement(),
                taxReader.getTaxChildren(), taxReader.getTaxBrackets(), taxReader.getTaxRateOverrides(),
                taxReader.getTaxActualOverrides(), budgetReader.getOneoffExpenses(), patrimoineReader.getTransfers(),
                budgetReader.getVariableIncomes(), budgetReader.getVariableOverrides(), bankReader.getBankImport(),
                patrimoineReader.getAssetCategories(), loanReader.getLoans(), goalReader.getGoals());
    }

    @Override
    public ResponseEntity<OverviewResponseDto> getOverview(Boolean useConstantEuros) {
        BudgetDataModel internalData = composeBudgetData();
        OverviewInput input = inputFactory.from(internalData, Boolean.TRUE.equals(useConstantEuros));
        OverviewResultModel internalResult = calculationService.computeOverview(input);
        return ResponseEntity.ok(this.mapper.toDto(internalResult,
                BudgetFacadeView.from(internalData, ObjectifsParameters.defaults())));
    }

    /**
     * Conservé pour compatibilité avec les tests existants qui exercent directement le calcul de
     * projection de retraite d'une personne (voir {@code OverviewServiceImplTest} et
     * {@code BusinessLogicIntegrationTest}). Délègue entièrement au moteur de calcul centralisé
     * {@link RetirementCalculationService} via {@link RetirementInputFactory}.
     */
    public RetirementProjectionModel computeRetirementProjection(
            BudgetDataModel data, RetirementModel.RetirementPersonModel person, int retireYear) {
        RetirementModel retirement = data.retirement();
        List<RetirementModel.RetirementPersonModel> people = retirement != null ? retirement.getEffectivePeople() : List.of();
        int index = people.indexOf(person);
        RetirementProjection projection = retirementCalculationService.compute(retirementInputFactory.create(data));
        return index >= 0 && index < projection.people().size() ? projection.people().get(index) : null;
    }
}
