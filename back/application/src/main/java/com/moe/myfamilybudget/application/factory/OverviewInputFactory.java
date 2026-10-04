package com.moe.myfamilybudget.application.factory;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;
import com.moe.myfamilybudget.application.factory.PatrimoineInputFactory;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.factory.TaxInputFactory;
import com.moe.myfamilybudget.application.factory.TreasuryInputFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.application.overview.OverviewInput;
import com.moe.myfamilybudget.application.overview.OverviewParameters;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjection;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionInput;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.calculation.RealEstateProjection;
import com.moe.myfamilybudget.domain.wealth.calculation.RealEstateProjection.RealEstateItemProjection;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculator;
import com.moe.myfamilybudget.domain.tax.calculation.TaxProjection;
import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel;

/**
 * Construit un {@link OverviewInput} à partir de {@link BudgetDataModel} (RF-900,
 * voir doc/architecture/11-domaine-overview.md).
 *
 * <p>Porte la composition des projections produites par les autres domaines
 * (Trésorerie, Retraite, Fiscalité, Patrimoine, Immobilier) :
 * elle vit dans {@code internal.factory}, hors du package {@code internal.calculation}
 * gardé par ArchUnit.
 */
@Component
public final class OverviewInputFactory {

    private final TreasuryInputFactory treasuryInputFactory;
    private final TresorerieCalculationService tresorerieCalculationService;
    private final PatrimoineProjectionService patrimoineProjectionService;
    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;

    public OverviewInputFactory() {
        this(new TreasuryInputFactory(),
             new TresorerieCalculationService(),
             new PatrimoineProjectionService(),
             new RetirementInputFactory(),
             new RetirementCalculationService());
    }

    public OverviewInputFactory(
            TreasuryInputFactory treasuryInputFactory,
            TresorerieCalculationService tresorerieCalculationService,
            PatrimoineProjectionService patrimoineProjectionService,
            RetirementInputFactory retirementInputFactory,
            RetirementCalculationService retirementCalculationService) {
        this.treasuryInputFactory = Objects.requireNonNull(treasuryInputFactory, "treasuryInputFactory");
        this.tresorerieCalculationService = Objects.requireNonNull(tresorerieCalculationService, "tresorerieCalculationService");
        this.patrimoineProjectionService = Objects.requireNonNull(patrimoineProjectionService, "patrimoineProjectionService");
        this.retirementInputFactory = Objects.requireNonNull(retirementInputFactory, "retirementInputFactory");
        this.retirementCalculationService = Objects.requireNonNull(retirementCalculationService, "retirementCalculationService");
    }

    public OverviewInput from(BudgetDataModel data, boolean useConstantEuros) {
        return from(data, useConstantEuros, LocalDate.now().getYear());
    }

    public OverviewInput from(BudgetDataModel data, boolean useConstantEuros, int currentYear) {
        Objects.requireNonNull(data, "data");
        SettingsModel settings = data.getEffectiveSettings();

        int retireYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();

        // 1. Trésorerie
        TreasuryProjectionInput treasuryInput = treasuryInputFactory.from(treasurySources(data));
        TreasuryProjection treasuryProjection = tresorerieCalculationService.compute(treasuryInput);

        // 2. Retraite
        RetirementProjection retirementProjection = retirementCalculationService.compute(
                retirementInputFactory.create(
                new RetirementSettingsModel(data.getEffectiveSettings().birthYear(), data.getEffectiveSettings().retireAge()),
                data.retirement(), data.getEffectiveIncomes(), data.getEffectiveTaxChildren().size()));

        // 3. Fiscalité
        int startYear = treasuryInput.period().startYear();
        int endYear = treasuryInput.period().endYear();
        TaxSimulationPeriod taxPeriod = new TaxSimulationPeriod(startYear, endYear);
        TaxCalculationInput taxInput = TaxInputFactory.from(taxSources(data), taxPeriod, retirementProjection);
        List<TaxYearlyModel> taxYearly = TaxCalculator.computeTaxYearly(taxInput);
        TaxProjection taxProjection = new TaxProjection(taxYearly.stream()
                .map(t -> new TaxProjection.Withholding(t.year(), t.withheld(), t.taxActual()))
                .toList());

        // 4. Patrimoine
        PatrimoineProjectionInput patrimoineInput = PatrimoineInputFactory.from(patrimoineSources(data));
        PatrimoineProjectionsModel patrimoineProjections = patrimoineProjectionService.compute(
                patrimoineInput, useConstantEuros);

        BigDecimal patrimoineActuel = data.getEffectivePlacements().stream()
                .map(PlacementModel::getEffectiveBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<String> excludedLabels = new HashSet<>();
        for (PlacementModel p : data.getEffectivePlacements()) {
            if (p.isExcludedFromRetirement()) {
                excludedLabels.add(p.label());
            }
        }
        PatrimoineProjection patrimoineProjection = new PatrimoineProjection(
                patrimoineProjections, patrimoineActuel, excludedLabels);

        // 5. Immobilier
        RealEstateProjection realEstateProjection = buildRealEstateProjection(data, retireYear, currentYear);

        // 6. Paramètres Overview
        BigDecimal pivotBalance = computePivotBalance(data);
        OverviewParameters parameters = new OverviewParameters(
                retireYear,
                settings.getEffectiveInflationRate(),
                pivotBalance,
                currentYear,
                useConstantEuros);

        return new OverviewInput(
                treasuryProjection,
                patrimoineProjection,
                retirementProjection,
                taxProjection,
                realEstateProjection,
                parameters);
    }

    private RealEstateProjection buildRealEstateProjection(BudgetDataModel data, int retireYear, int currentYear) {
        BigDecimal totalNominal = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        List<RealEstateItemProjection> items = new ArrayList<>();

        for (RealEstateModel r : data.getEffectiveRealEstate()) {
            BigDecimal currentVal = r.getEffectiveCurrentValue();
            totalCurrent = totalCurrent.add(currentVal);

            int valYear = r.valuationYear() != null ? r.valuationYear() : currentYear;
            int elapsed = Math.max(0, retireYear - valYear);
            double growthFactor = Math.pow(1.0 + r.getEffectiveAnnualGrowthRate().doubleValue(), elapsed);
            BigDecimal nominalVal = currentVal.multiply(BigDecimal.valueOf(growthFactor));
            totalNominal = totalNominal.add(nominalVal);

            items.add(new RealEstateItemProjection(
                    r.label(),
                    currentVal,
                    valYear,
                    r.getEffectiveAnnualGrowthRate(),
                    nominalVal));
        }

        return new RealEstateProjection(totalNominal, totalCurrent, items);
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

    /**
     * Transition (SILO-113) : extrait de {@link BudgetDataModel} les fragments attendus par
     * {@link TreasuryInputFactory}. Supprimé avec ce service (SILO-117), qui lira alors les ports
     * propriétaires.
     */
    private static TreasuryInputFactory.Sources treasurySources(BudgetDataModel data) {
        SettingsModel settings = data.getEffectiveSettings();
        return new TreasuryInputFactory.Sources(
                new RetirementSettingsModel(settings.birthYear(), settings.retireAge()),
                new TaxSettingsModel(settings.childExitAge(), settings.taxAbattement()),
                new TresorerieSettingsModel(settings.pivotDate(), settings.pivotMode(), settings.startBalance(),
                        settings.sweepEnabled(), settings.cashCeiling(), settings.cashFloor(),
                        settings.cashAlertThreshold()),
                new SimulationSettingsModel(settings.simulateUntilAge()),
                settings.inflationRate(),
                data.retirement(),
                data.getEffectiveIncomes(),
                data.getEffectiveCharges(),
                data.getEffectivePlacements(),
                data.getEffectiveOneoff(),
                data.getEffectiveTransfers(),
                data.getEffectiveVariableIncomes(),
                data.getEffectiveVariableOverrides(),
                data.getEffectiveTaxChildren(),
                data.getEffectiveTaxBrackets(),
                data.getEffectiveTaxRateOverrides(),
                data.getEffectiveTaxActualOverrides(),
                data.bankImport());
    }

    /**
     * Transition (SILO-111, déplacé ici par SILO-113) : extrait de {@link BudgetDataModel} les fragments
     * attendus par {@link TaxInputFactory}. Supprimé avec ce service (SILO-117).
     */
    private static TaxInputFactory.Sources taxSources(BudgetDataModel data) {
        SettingsModel settings = data.getEffectiveSettings();
        return new TaxInputFactory.Sources(
                new RetirementSettingsModel(settings.birthYear(), settings.retireAge()),
                new TaxSettingsModel(settings.childExitAge(), settings.taxAbattement()),
                settings.inflationRate(),
                data.getEffectiveIncomes(),
                data.getEffectiveVariableIncomes(),
                data.getEffectiveVariableOverrides(),
                data.getEffectiveTaxChildren(),
                data.getEffectiveTaxBrackets(),
                data.getEffectiveTaxRateOverrides(),
                data.getEffectiveTaxActualOverrides());
    }

    /**
     * Transition (SILO-112) : extrait de {@link BudgetDataModel} les fragments attendus par
     * {@link PatrimoineInputFactory}. Supprimé avec ce service (SILO-117), qui lira alors les ports
     * propriétaires.
     */
    private static PatrimoineInputFactory.Sources patrimoineSources(BudgetDataModel data) {
        SettingsModel settings = data.getEffectiveSettings();
        return new PatrimoineInputFactory.Sources(
                new RetirementSettingsModel(settings.birthYear(), settings.retireAge()),
                new TresorerieSettingsModel(settings.pivotDate(), settings.pivotMode(), settings.startBalance(),
                        settings.sweepEnabled(), settings.cashCeiling(), settings.cashFloor(),
                        settings.cashAlertThreshold()),
                settings.inflationRate(),
                data.getEffectiveIncomes(),
                data.getEffectiveCharges(),
                data.getEffectivePlacements(),
                data.getEffectiveOneoff(),
                data.getEffectiveTransfers());
    }
}
