package com.moe.myfamilybudget.application.usecase;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.factory.OverviewInputFactory;
import com.moe.myfamilybudget.application.factory.PatrimoineTransferConverter;
import com.moe.myfamilybudget.application.factory.TreasuryInputFactory;
import com.moe.myfamilybudget.application.overview.OverviewCalculationService;
import com.moe.myfamilybudget.application.overview.OverviewInput;
import com.moe.myfamilybudget.application.usecase.overview.OverviewCashflowYearModel;
import com.moe.myfamilybudget.application.usecase.overview.OverviewPatrimoinePerPlacementModel;
import com.moe.myfamilybudget.application.usecase.overview.OverviewPatrimoineProjectionsModel;
import com.moe.myfamilybudget.application.usecase.overview.OverviewPatrimoineYearModel;
import com.moe.myfamilybudget.application.usecase.overview.OverviewResultModel;
import com.moe.myfamilybudget.application.usecase.overview.OverviewTripleAmountModel;
import com.moe.myfamilybudget.application.usecase.overview.OverviewUseCase;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationService;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Implémentation du cas d'usage Overview (R-85, SILO-316, même gabarit que {@code DefaultAnalysisUseCase}).
 *
 * <p>La logique de composition de l'overview vit ici, sans {@code ResponseEntity} ni DTO : elle lit ses
 * fragments via les ports des silos ({@link BudgetReader}, {@link PatrimoineReader}, {@link BankReader},
 * {@link RetirementReader}, {@link TaxReader}, et les cinq ports de paramètres), construit l'entrée du
 * moteur par {@link OverviewInputFactory}, appelle {@link OverviewCalculationService} puis traduit les
 * types du domaine/interne vers les modèles de {@code application-api}.
 *
 * <p>La composition du budget réexposé par l'API ({@code data}, {@link
 * com.moe.myfamilybudget.application.mapper.BudgetFacadeView}) est partagée avec Analyse et l'import/export :
 * elle reste hors de ce cas d'usage et portée par {@code OverviewServiceImpl}.
 */
@Service
public class DefaultOverviewUseCase implements OverviewUseCase {

    private final OverviewInputFactory inputFactory;
    private final OverviewCalculationService calculationService;
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

    public DefaultOverviewUseCase(
            OverviewCalculationService calculationService,
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
            BankReader bankReader) {
        this.inputFactory = new OverviewInputFactory(retirementCalculationService, taxCalculationService,
                patrimoineProjectionService, tresorerieCalculationService);
        this.calculationService = calculationService != null ? calculationService : new OverviewCalculationService();
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
    }

    @Override
    public OverviewResultModel getOverview(Boolean useConstantEuros) {
        var retirementSettings = retirementSettingsReader.getRetirementSettings();
        var taxSettings = taxSettingsReader.getTaxSettings();
        var tresorerieSettings = tresorerieSettingsReader.getTresorerieSettings();
        var simulationSettings = simulationSettingsReader.getSimulationSettings();
        var economicAssumptions = economicAssumptionsReader.getEconomicAssumptions();

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

        OverviewInputFactory.Sources sources = new OverviewInputFactory.Sources(
                new TreasuryInputFactory.Sources(
                        retirementSettings,
                        taxSettings,
                        tresorerieSettings,
                        simulationSettings,
                        economicAssumptions.inflationRate(),
                        retirement, incomes, charges, placements, oneoff, transfers,
                        variableIncomes, variableOverrides, taxChildren, taxBrackets,
                        taxRateOverrides, taxActualOverrides, bankImport),
                realEstate);

        OverviewInput input = inputFactory.from(sources, Boolean.TRUE.equals(useConstantEuros));
        com.moe.myfamilybudget.application.model.OverviewResultModel internalResult =
                calculationService.computeOverview(input);

        return toApplicationModel(internalResult);
    }

    private static OverviewResultModel toApplicationModel(
            com.moe.myfamilybudget.application.model.OverviewResultModel model) {
        if (model == null) {
            return new OverviewResultModel(null, null, null, false, 0, null, null, null, null, null, null, null, null);
        }
        return new OverviewResultModel(
                model.years(),
                toCashflow(model.cashflow()),
                toPatrimoine(model.patrimoine()),
                model.useConstantEuros(),
                model.retireYear(),
                model.pivotBalance(),
                model.patrimoineActuel(),
                model.fluxNetActuel(),
                model.retireCharges(),
                model.totalPensions(),
                toTripleAmount(model.retirePatrimoine()),
                toTripleAmount(model.fireRente()),
                toTripleAmount(model.financialOnlyRente()));
    }

    private static List<OverviewCashflowYearModel> toCashflow(
            List<com.moe.myfamilybudget.domain.treasury.model.CashflowYearModel> rows) {
        if (rows == null) {
            return null;
        }
        List<OverviewCashflowYearModel> result = new ArrayList<>(rows.size());
        for (var r : rows) {
            result.add(r == null ? null : new OverviewCashflowYearModel(
                    r.year(), r.income(), r.variableIncome(), r.savings(), r.charges(),
                    r.oneoff(), r.transfersY(), r.impots(), r.regularisation(), r.net(), r.balance()));
        }
        return result;
    }

    private static OverviewPatrimoineProjectionsModel toPatrimoine(
            com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel p) {
        if (p == null) {
            return null;
        }
        List<OverviewPatrimoinePerPlacementModel> perPlacement = null;
        if (p.perPlacement() != null) {
            perPlacement = new ArrayList<>(p.perPlacement().size());
            for (var pp : p.perPlacement()) {
                perPlacement.add(pp == null ? null : new OverviewPatrimoinePerPlacementModel(
                        pp.label(), toPatrimoineYears(pp.rows())));
            }
        }
        List<OverviewPatrimoineYearModel> totals = toPatrimoineYears(p.totals());
        return new OverviewPatrimoineProjectionsModel(perPlacement, totals);
    }

    private static List<OverviewPatrimoineYearModel> toPatrimoineYears(
            List<com.moe.myfamilybudget.domain.wealth.model.PatrimoineYearModel> rows) {
        if (rows == null) {
            return null;
        }
        List<OverviewPatrimoineYearModel> result = new ArrayList<>(rows.size());
        for (var r : rows) {
            result.add(r == null ? null : new OverviewPatrimoineYearModel(r.year(), r.pess(), r.corr(), r.opti()));
        }
        return result;
    }

    private static OverviewTripleAmountModel toTripleAmount(
            com.moe.myfamilybudget.domain.treasury.model.TripleAmountModel m) {
        if (m == null) {
            return null;
        }
        return new OverviewTripleAmountModel(m.pess(), m.corr(), m.opti());
    }
}
