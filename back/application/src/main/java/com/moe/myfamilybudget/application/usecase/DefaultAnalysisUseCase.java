package com.moe.myfamilybudget.application.usecase;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.factory.AnalyseInputFactory;
import com.moe.myfamilybudget.application.factory.PointageInputFactory;
import com.moe.myfamilybudget.application.settings.SettingsComposer;
import com.moe.myfamilybudget.application.settings.SettingsView;
import com.moe.myfamilybudget.application.usecase.analysis.AnalyseCategorySummaryModel;
import com.moe.myfamilybudget.application.usecase.analysis.AnalyseDriftRowModel;
import com.moe.myfamilybudget.application.usecase.analysis.AnalyseKpiModel;
import com.moe.myfamilybudget.application.usecase.analysis.AnalyseLandingRowModel;
import com.moe.myfamilybudget.application.usecase.analysis.AnalyseMonthlyCompareModel;
import com.moe.myfamilybudget.application.usecase.analysis.AnalyseResultModel;
import com.moe.myfamilybudget.application.usecase.analysis.AnalysisUseCase;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseCalculationService;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseInput;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Implémentation du cas d'usage Analyse (R-84, même gabarit que {@code DefaultTaxUseCase},
 * SILO-315).
 *
 * <p>La logique de composition de l'analyse (anciennement {@code AnalyseServiceImpl#getAnalyse})
 * vit ici, sans {@code ResponseEntity} ni DTO : elle lit ses fragments via les ports des silos
 * ({@link BudgetReader}, {@link PatrimoineReader}, {@link BankReader}, et les cinq ports de
 * paramètres composés par {@link SettingsComposer} pour l'inflation), construit l'entrée du moteur
 * par {@link AnalyseInputFactory} (après résolution des lignes actives par
 * {@link PointageInputFactory}), appelle {@link AnalyseCalculationService} puis traduit les types
 * du silo Analyse vers les modèles de {@code application-api}.
 *
 * <p>La composition du budget réexposé par l'API ({@code data}, {@link
 * com.moe.myfamilybudget.application.mapper.BudgetFacadeView}) est partagée avec Overview (R-85)
 * et l'import/export ({@code GlobalBudgetSnapshotService}) : elle reste hors de ce cas d'usage et
 * portée par {@code AnalyseServiceImpl}, comme avant ce patch.
 */
@Service
public class DefaultAnalysisUseCase implements AnalysisUseCase {

    private final AnalyseInputFactory analyseInputFactory;
    private final AnalyseCalculationService analyseCalculationService;
    private final RetirementSettingsReader retirementSettingsReader;
    private final TaxSettingsReader taxSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final SimulationSettingsReader simulationSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final BankReader bankReader;

    public DefaultAnalysisUseCase(
            AnalyseCalculationService analyseCalculationService,
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            BankReader bankReader) {
        this.analyseInputFactory = new AnalyseInputFactory();
        this.analyseCalculationService = analyseCalculationService;
        this.retirementSettingsReader = retirementSettingsReader;
        this.taxSettingsReader = taxSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.simulationSettingsReader = simulationSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.bankReader = bankReader;
    }

    @Override
    public AnalyseResultModel getAnalyse(Integer monthsBack) {
        SettingsView settings = SettingsComposer.compose(
                retirementSettingsReader.getRetirementSettings(), taxSettingsReader.getTaxSettings(),
                tresorerieSettingsReader.getTresorerieSettings(), simulationSettingsReader.getSimulationSettings(),
                economicAssumptionsReader.getEconomicAssumptions());
        List<IncomeModel> incomes = budgetReader.getIncomes();
        List<ChargeModel> charges = budgetReader.getCharges();
        List<PlacementModel> placements = patrimoineReader.getPlacements();
        BankImportModel bankImport = bankReader.getBankImport();

        PointageInputFactory.Sources sources = new PointageInputFactory.Sources(
                charges, incomes, placements, settings.inflationRate());
        AnalyseInput input = analyseInputFactory.from(bankImport, sources, monthsBack);
        com.moe.myfamilybudget.domain.analysis.model.AnalyseResultModel resultModel =
                analyseCalculationService.computeAnalyse(input);

        return toApplicationModel(resultModel);
    }

    private static AnalyseResultModel toApplicationModel(
            com.moe.myfamilybudget.domain.analysis.model.AnalyseResultModel model) {
        if (model == null) {
            return new AnalyseResultModel(null, null, null, null, null, null, null);
        }
        return new AnalyseResultModel(
                toKpi(model.kpis()),
                toLandingData(model.landingData()),
                toDriftRows(model.driftRows()),
                toMonthlyCompareData(model.monthlyCompareData()),
                toCategorySummaries(model.categorySummaries()),
                model.currentMonthISO(),
                model.currentMonthLabel());
    }

    private static AnalyseKpiModel toKpi(com.moe.myfamilybudget.domain.analysis.model.AnalyseKpiModel kpi) {
        if (kpi == null) {
            return null;
        }
        return new AnalyseKpiModel(
                kpi.totalExpenses(), kpi.totalIncome(), kpi.nbMonths(), kpi.uncategorizedCount(),
                kpi.compressibleTotal());
    }

    private static List<AnalyseLandingRowModel> toLandingData(
            List<com.moe.myfamilybudget.domain.analysis.model.AnalyseLandingRowModel> rows) {
        if (rows == null) {
            return null;
        }
        List<AnalyseLandingRowModel> result = new ArrayList<>(rows.size());
        for (com.moe.myfamilybudget.domain.analysis.model.AnalyseLandingRowModel r : rows) {
            result.add(r == null ? null : new AnalyseLandingRowModel(
                    r.id(), r.label(), r.kind(), r.budgeted(), r.reel(), r.pct(), r.status(),
                    r.pendingContrib(), r.hasPendingContrib()));
        }
        return result;
    }

    private static List<AnalyseDriftRowModel> toDriftRows(
            List<com.moe.myfamilybudget.domain.analysis.model.AnalyseDriftRowModel> rows) {
        if (rows == null) {
            return null;
        }
        List<AnalyseDriftRowModel> result = new ArrayList<>(rows.size());
        for (com.moe.myfamilybudget.domain.analysis.model.AnalyseDriftRowModel r : rows) {
            result.add(r == null ? null : new AnalyseDriftRowModel(
                    r.id(), r.label(), r.kind(), r.budgeted(), r.avg3m(), r.avg12m(), r.ecart(), r.ecartPct(),
                    r.status(), r.months()));
        }
        return result;
    }

    private static List<AnalyseMonthlyCompareModel> toMonthlyCompareData(
            List<com.moe.myfamilybudget.domain.analysis.model.AnalyseMonthlyCompareModel> rows) {
        if (rows == null) {
            return null;
        }
        List<AnalyseMonthlyCompareModel> result = new ArrayList<>(rows.size());
        for (com.moe.myfamilybudget.domain.analysis.model.AnalyseMonthlyCompareModel r : rows) {
            result.add(r == null ? null : new AnalyseMonthlyCompareModel(
                    r.monthISO(), r.label(), r.budgeted(), r.reel(), r.hasPointing()));
        }
        return result;
    }

    private static List<AnalyseCategorySummaryModel> toCategorySummaries(
            List<com.moe.myfamilybudget.domain.analysis.model.AnalyseCategorySummaryModel> rows) {
        if (rows == null) {
            return null;
        }
        List<AnalyseCategorySummaryModel> result = new ArrayList<>(rows.size());
        for (com.moe.myfamilybudget.domain.analysis.model.AnalyseCategorySummaryModel r : rows) {
            result.add(r == null ? null : new AnalyseCategorySummaryModel(r.label(), r.amount(), r.color()));
        }
        return result;
    }
}
