package com.moe.myfamilybudget.application.factory;

import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
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
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel;

/**
 * Construit un {@link OverviewInput} à partir de fragments du budget (RF-900,
 * voir doc/architecture/11-domaine-overview.md).
 *
 * <p>SILO-117 : cette factory ne connaît plus {@code BudgetDataModel} ; l'appelant lui fournit des
 * {@link Sources} (les fragments de {@link TreasuryInputFactory.Sources}, qui couvrent aussi Fiscalité,
 * Retraite et Patrimoine, plus l'immobilier). Les listes absentes sont lues comme vides, le barème fiscal
 * par défaut s'applique via {@link TaxBracketDefaults}.
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

    /**
     * Fragments nécessaires à l'assemblage de l'aperçu (SILO-117) : ceux de Trésorerie (qui contiennent
     * déjà paramètres, revenus, charges, placements, fiscalité, retraite et import bancaire) et l'immobilier.
     */
    public record Sources(TreasuryInputFactory.Sources treasury, List<RealEstateModel> realEstate) {

        public Sources {
            Objects.requireNonNull(treasury, "treasury");
            realEstate = realEstate != null ? realEstate : List.of();
        }
    }

    public OverviewInput from(Sources data, boolean useConstantEuros) {
        return from(data, useConstantEuros, LocalDate.now().getYear());
    }

    public OverviewInput from(Sources data, boolean useConstantEuros, int currentYear) {
        Objects.requireNonNull(data, "data");
        TreasuryInputFactory.Sources t = data.treasury();

        int retireYear = t.retirementSettings().getEffectiveBirthYear() + t.retirementSettings().getEffectiveRetireAge();

        // 1. Trésorerie
        TreasuryProjectionInput treasuryInput = treasuryInputFactory.from(t);
        TreasuryProjection treasuryProjection = tresorerieCalculationService.compute(treasuryInput);

        // 2. Retraite
        RetirementProjection retirementProjection = retirementCalculationService.compute(
                retirementInputFactory.create(
                        t.retirementSettings(), t.retirement(), t.incomes(), t.taxChildren().size()));

        // 3. Fiscalité
        int startYear = treasuryInput.period().startYear();
        int endYear = treasuryInput.period().endYear();
        TaxSimulationPeriod taxPeriod = new TaxSimulationPeriod(startYear, endYear);
        TaxCalculationInput taxInput = TaxInputFactory.from(taxSources(t), taxPeriod, retirementProjection);
        List<TaxYearlyModel> taxYearly = TaxCalculator.computeTaxYearly(taxInput);
        TaxProjection taxProjection = new TaxProjection(taxYearly.stream()
                .map(y -> new TaxProjection.Withholding(y.year(), y.withheld(), y.taxActual()))
                .toList());

        // 4. Patrimoine
        PatrimoineProjectionInput patrimoineInput = PatrimoineInputFactory.from(patrimoineSources(t));
        PatrimoineProjectionsModel patrimoineProjections = patrimoineProjectionService.compute(
                patrimoineInput, useConstantEuros);

        BigDecimal patrimoineActuel = t.placements().stream()
                .map(PlacementModel::getEffectiveBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<String> excludedLabels = new HashSet<>();
        for (PlacementModel p : t.placements()) {
            if (p.isExcludedFromRetirement()) {
                excludedLabels.add(p.label());
            }
        }
        PatrimoineProjection patrimoineProjection = new PatrimoineProjection(
                patrimoineProjections, patrimoineActuel, excludedLabels);

        // 5. Immobilier
        RealEstateProjection realEstateProjection = buildRealEstateProjection(data.realEstate(), retireYear, currentYear);

        // 6. Paramètres Overview
        BigDecimal pivotBalance = computePivotBalance(t);
        OverviewParameters parameters = new OverviewParameters(
                retireYear,
                t.inflationRate(),
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

    private RealEstateProjection buildRealEstateProjection(List<RealEstateModel> realEstate, int retireYear, int currentYear) {
        BigDecimal totalNominal = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        List<RealEstateItemProjection> items = new ArrayList<>();

        for (RealEstateModel r : realEstate) {
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

    private BigDecimal computePivotBalance(TreasuryInputFactory.Sources t) {
        TresorerieSettingsModel settings = t.tresorerieSettings();
        if (settings.pivotDate() == null) {
            return null;
        }
        if ("manual".equalsIgnoreCase(settings.pivotMode())) {
            return settings.getEffectiveStartBalance();
        }
        BigDecimal base = settings.getEffectiveStartBalance();
        String pivotDate = settings.pivotDate();

        BigDecimal sum = BigDecimal.ZERO;
        if (t.bankImport() != null && t.bankImport().transactions() != null) {
            for (BankImportModel.BankTransactionModel tx : t.bankImport().transactions()) {
                if (tx.date() != null && tx.date().compareTo(pivotDate) <= 0) {
                    sum = sum.add(tx.amount() != null ? tx.amount() : BigDecimal.ZERO);
                }
            }
        }
        return base.add(sum);
    }

    private static TaxInputFactory.Sources taxSources(TreasuryInputFactory.Sources t) {
        return new TaxInputFactory.Sources(
                t.retirementSettings(),
                t.taxSettings(),
                t.inflationRate(),
                t.incomes(),
                t.variableIncomes(),
                t.variableOverrides(),
                t.taxChildren(),
                TaxBracketDefaults.orDefault(t.taxBrackets()),
                t.taxRateOverrides(),
                t.taxActualOverrides());
    }

    private static PatrimoineInputFactory.Sources patrimoineSources(TreasuryInputFactory.Sources t) {
        return new PatrimoineInputFactory.Sources(
                t.retirementSettings(),
                t.tresorerieSettings(),
                t.inflationRate(),
                t.incomes(),
                t.charges(),
                t.placements(),
                t.oneoff(),
                t.transfers());
    }
}
