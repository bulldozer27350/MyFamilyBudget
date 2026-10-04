package com.moe.myfamilybudget.application.factory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.calculation.AnnualTaxIncome;
import com.moe.myfamilybudget.domain.retirement.calculation.AnnualTaxableRetirementIncome;
import com.moe.myfamilybudget.domain.tax.calculation.AnnualVariableIncome;
import com.moe.myfamilybudget.domain.treasury.calculation.ChargeProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.IncomeProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.OneOffCashflow;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementCashflowInput;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementIncomeProjection;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.domain.tax.calculation.TaxProjection;
import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.domain.treasury.calculation.TransferProjection;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryParameters;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasuryProjectionInput;
import com.moe.myfamilybudget.domain.treasury.calculation.TreasurySimulationPeriod;
import com.moe.myfamilybudget.domain.treasury.calculation.VariableIncomeProjection;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculator;
import com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel;
import com.moe.myfamilybudget.domain.budget.TransferModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;

/**
 * Construit un {@link TreasuryProjectionInput} à partir de {@link BudgetDataModel} (RF-400, voir
 * doc/architecture/06-domaine-tresorerie.md).
 *
 * <p>Comme {@code TaxInputFactory}, {@code RetirementInputFactory} et {@code PatrimoineInputFactory},
 * cette classe porte la dépendance à {@code BudgetDataModel} que le domaine ne doit pas avoir :
 * elle vit dans {@code internal.factory}, hors du package {@code internal.calculation} gardé par
 * ArchUnit.
 *
 * <p><b>Purement additive, non branchée.</b> {@code TresorerieCalculationService} continue de
 * calculer lui-même sa projection jusqu'au branchement (RF-401). L'horizon, l'impôt et les
 * pensions sont composés à partir des contrats déjà stables des autres domaines
 * ({@code TaxInputFactory}/{@code TaxCalculator} pour la Fiscalité RF-203,
 * {@code RetirementInputFactory}/{@code RetirementCalculationService} pour la Retraite RF-101),
 * pour ne pas dupliquer une deuxième fois leurs règles de calcul (voir {@code computeTaxYearly}
 * dans {@code TresorerieCalculationService}, qui deviendra ce chemin lors du branchement).
 */
public final class TreasuryInputFactory {

    private static final Logger LOG = LoggerFactory.getLogger(TreasuryInputFactory.class);
    private static final int DEFAULT_START_YEAR = 2026;

    private final RetirementInputFactory retirementInputFactory = new RetirementInputFactory();
    private final RetirementCalculationService retirementCalculationService = new RetirementCalculationService();

    public TreasuryProjectionInput from(BudgetDataModel data) {
        Objects.requireNonNull(data, "data");
        SettingsModel settings = data.getEffectiveSettings();

        int retireYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();
        int startYear = findEarliestYear(data);
        int wantedEnd = settings.getEffectiveBirthYear() + settings.getEffectiveSimulateUntilAge();
        int endYear = Math.max(retireYear + 3, wantedEnd);
        TreasurySimulationPeriod period = new TreasurySimulationPeriod(startYear, endYear);

        List<IncomeProjectionInput> incomes = data.getEffectiveIncomes().stream()
                .map(TreasuryInputFactory::toIncome)
                .toList();
        List<ChargeProjectionInput> charges = data.getEffectiveCharges().stream()
                .map(TreasuryInputFactory::toCharge)
                .toList();
        List<VariableIncomeProjection> variableIncomes = data.getEffectiveVariableIncomes().stream()
                .map(v -> toVariableIncome(v, data.getEffectiveVariableOverrides()))
                .toList();
        List<OneOffCashflow> oneOffExpenses = data.getEffectiveOneoff().stream()
                .map(o -> new OneOffCashflow(parseDate(o.date()), o.getEffectiveAmount()))
                .toList();
        List<TransferProjection> transfers = data.getEffectiveTransfers().stream()
                .map(t -> new TransferProjection(parseDate(t.date()), t.getEffectiveAmount()))
                .toList();

        List<PlacementCashflowInput> placements = new ArrayList<>();
        for (int year = startYear; year <= endYear; year++) {
            placements.add(new PlacementCashflowInput(year, placementsAnnualForYear(data.getEffectivePlacements(), year)));
        }

        TaxSimulationPeriod taxPeriod = new TaxSimulationPeriod(startYear, endYear);
        RetirementProjection retirement = retirementCalculationService.compute(retirementInputFactory.create(
                new RetirementSettingsModel(data.getEffectiveSettings().birthYear(), data.getEffectiveSettings().retireAge()),
                data.retirement(), data.getEffectiveIncomes(), data.getEffectiveTaxChildren().size()));
        TaxCalculationInput taxInput = TaxInputFactory.from(data, taxPeriod, retirement);
        List<TaxYearlyModel> taxYearly = TaxCalculator.computeTaxYearly(taxInput);
        Map<Integer, BigDecimal> regularIncomes = taxInput.incomes().stream()
                .collect(Collectors.toMap(AnnualTaxIncome::year, AnnualTaxIncome::amount, BigDecimal::add));
        Map<Integer, BigDecimal> varIncomes = taxInput.variableIncomes().stream()
                .collect(Collectors.toMap(AnnualVariableIncome::year, AnnualVariableIncome::taxableAmount, BigDecimal::add));
        Map<Integer, BigDecimal> retIncomes = taxInput.retirementIncome().stream()
                .collect(Collectors.toMap(AnnualTaxableRetirementIncome::year, AnnualTaxableRetirementIncome::amount, BigDecimal::add));

        TaxProjection taxProjection = new TaxProjection(taxYearly.stream()
                .map(t -> {
                    BigDecimal gross = regularIncomes.getOrDefault(t.year(), BigDecimal.ZERO)
                            .add(varIncomes.getOrDefault(t.year(), BigDecimal.ZERO))
                            .add(retIncomes.getOrDefault(t.year(), BigDecimal.ZERO));
                    BigDecimal withheld = t.ratePAS() != null ? t.ratePAS().multiply(gross) : t.withheld();
                    return new TaxProjection.Withholding(t.year(), withheld, t.taxActual());
                })
                .toList());

        RetirementIncomeProjection retirementIncome = new RetirementIncomeProjection(
                retirementPensionByYear(retirement, retireYear, endYear));

        TreasuryParameters parameters = new TreasuryParameters(
                retireYear, resolvePivotBalance(data), settings.getEffectiveInflationRate());

        return new TreasuryProjectionInput(period, incomes, charges, variableIncomes, oneOffExpenses,
                transfers, placements, taxProjection, retirementIncome, parameters);
    }

    public static IncomeProjectionInput toIncome(IncomeModel i) {
        return new IncomeProjectionInput(i.label(), i.getEffectiveMonthly(), parseDate(i.start()), parseDate(i.end()),
                i.getEffectiveGrowthRate());
    }

    public static ChargeProjectionInput toCharge(ChargeModel c) {
        return new ChargeProjectionInput(c.label(), c.getEffectiveMonthly(), parseDate(c.start()), parseDate(c.end()),
                c.growthRate());
    }

    private static VariableIncomeProjection toVariableIncome(VariableIncomeModel v, List<VariableOverrideModel> allOverrides) {
        List<VariableIncomeProjection.Override> overrides = allOverrides.stream()
                .filter(o -> Objects.equals(o.label(), v.label()) && o.year() != null)
                .map(o -> new VariableIncomeProjection.Override(o.year(), o.getEffectiveAmount(), o.taxable()))
                .toList();
        return new VariableIncomeProjection(v.label(), v.refIncomeLabel(), v.startYear(), v.endYear(),
                v.getEffectiveRate(), v.taxable(), overrides);
    }

    /** Somme des versements mensuels configurés dans la fenêtre de chaque placement, sans pause (voir {@link PlacementCashflowInput}). */
    private static BigDecimal placementsAnnualForYear(List<PlacementModel> placements, int year) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PlacementModel p : placements) {
            Integer sY = p.monthlyFrom() != null ? yearOf(p.monthlyFrom()) : null;
            Integer eY = p.monthlyUntil() != null ? yearOf(p.monthlyUntil()) : null;
            boolean within = (sY == null || year >= sY) && (eY == null || year <= eY);
            if (within) {
                sum = sum.add(p.getEffectiveMonthly().multiply(BigDecimal.valueOf(12)));
            }
        }
        return sum;
    }

    private static List<RetirementIncomeProjection.AnnualPension> retirementPensionByYear(
            RetirementProjection retirement, int retireYear, int endYear) {
        BigDecimal monthlyPension = BigDecimal.ZERO;
        for (RetirementProjectionModel p : retirement.people()) {
            if (p.pensionTotaleMensuelle() != null) {
                monthlyPension = monthlyPension.add(p.pensionTotaleMensuelle());
            }
        }
        BigDecimal annualPension = monthlyPension.multiply(BigDecimal.valueOf(12));
        List<RetirementIncomeProjection.AnnualPension> years = new ArrayList<>();
        for (int year = retireYear; year <= Math.max(retireYear, endYear); year++) {
            years.add(new RetirementIncomeProjection.AnnualPension(year, annualPension));
        }
        return years;
    }

    private static BigDecimal resolvePivotBalance(BudgetDataModel data) {
        SettingsModel settings = data.getEffectiveSettings();
        if (data.settings() == null || data.settings().pivotDate() == null) {
            return settings.getEffectiveStartBalance();
        }
        if ("manual".equalsIgnoreCase(data.settings().pivotMode())) {
            return settings.getEffectiveStartBalance();
        }
        BigDecimal base = settings.getEffectiveStartBalance();
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

    private static int findEarliestYear(BudgetDataModel data) {
        List<String> dates = new ArrayList<>();
        for (IncomeModel i : data.getEffectiveIncomes()) if (i.start() != null) dates.add(i.start());
        for (ChargeModel c : data.getEffectiveCharges()) if (c.start() != null) dates.add(c.start());
        for (PlacementModel p : data.getEffectivePlacements()) {
            if (p.monthlyFrom() != null) dates.add(p.monthlyFrom());
            if (p.balanceDate() != null) dates.add(p.balanceDate());
        }
        for (OneOffExpenseModel o : data.getEffectiveOneoff()) if (o.date() != null) dates.add(o.date());
        for (TransferModel t : data.getEffectiveTransfers()) if (t.date() != null) dates.add(t.date());
        if (data.settings() != null && data.settings().pivotDate() != null) dates.add(data.settings().pivotDate());
        if (data.bankImport() != null && data.bankImport().transactions() != null) {
            for (BankImportModel.BankTransactionModel t : data.bankImport().transactions()) {
                if (t.date() != null) dates.add(t.date());
            }
        }

        int earliestYear = DEFAULT_START_YEAR;
        boolean found = false;
        for (String d : dates) {
            Integer y = yearOf(d);
            if (y != null && (!found || y < earliestYear)) {
                earliestYear = y;
                found = true;
            }
        }
        return found ? earliestYear : DEFAULT_START_YEAR;
    }

    private static Integer yearOf(String dateISO) {
        LocalDate d = parseDate(dateISO);
        return d != null ? d.getYear() : null;
    }

    private static LocalDate parseDate(String dateISO) {
        if (dateISO == null || dateISO.isBlank()) return null;
        try {
            if (dateISO.length() == 7) {
                return YearMonth.parse(dateISO).atDay(1);
            }
            return LocalDate.parse(dateISO.substring(0, 10));
        } catch (Exception e) {
            LOG.warn("Date ISO illisible dans la construction de l'entrée trésorerie, ignorée : '{}'", dateISO, e);
            return null;
        }
    }
}
