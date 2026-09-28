package com.moe.myfamilybudget.server.internal.factory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.server.internal.calculation.AnnualCashflow;
import com.moe.myfamilybudget.server.internal.calculation.CashflowProjection;
import com.moe.myfamilybudget.server.internal.calculation.PatrimoineProjectionInput;
import com.moe.myfamilybudget.server.internal.calculation.PatrimoineProjectionParameters;
import com.moe.myfamilybudget.server.internal.calculation.PlacementProjectionInput;
import com.moe.myfamilybudget.server.internal.calculation.PlacementTransfer;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;

/**
 * Construit un {@link PatrimoineProjectionInput} à partir de {@link BudgetDataModel} (RF-300,
 * voir doc/architecture/05-domaine-patrimoine.md).
 *
 * <p>Comme {@code TaxInputFactory} et {@code RetirementInputFactory}, cette classe porte la
 * dépendance à {@code BudgetDataModel} que le domaine ne doit pas avoir : elle vit dans
 * {@code internal.factory}, hors du package {@code internal.calculation} gardé par ArchUnit.
 *
 * <p><b>Purement additif.</b> Aucun appelant existant n'est modifié : {@code PatrimoineServiceImpl}
 * continue de calculer lui-même sa projection jusqu'à RF-301. L'horizon, la reconstitution du flux
 * net annuel et la lecture des dates reprennent volontairement à l'identique les règles de
 * {@code PatrimoineServiceImpl} ({@code findEarliestYear}, {@code incomeAnnualForYear},
 * {@code chargeAnnualForYear}, {@code parseDate}), pour que RF-301 puisse brancher le moteur sans
 * changement de résultat. Ces helpers privés disparaîtront de {@code PatrimoineServiceImpl} à ce
 * moment-là.
 */
public final class PatrimoineInputFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PatrimoineInputFactory.class);

    private PatrimoineInputFactory() {
    }

    public static PatrimoineProjectionInput from(BudgetDataModel data) {
        Objects.requireNonNull(data, "data");
        SettingsModel settings = data.getEffectiveSettings();

        int startYear = findEarliestYear(data);
        int endYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();
        if (endYear < startYear) {
            endYear = startYear + 40;
        }

        PatrimoineProjectionParameters parameters = new PatrimoineProjectionParameters(
                startYear,
                endYear,
                settings.getEffectiveInflationRate(),
                settings.getEffectiveStartBalance(),
                settings.cashCeiling(),
                settings.cashAlertThreshold());

        List<PlacementProjectionInput> placements = data.getEffectivePlacements().stream()
                .map(PatrimoineInputFactory::toPlacement)
                .toList();

        List<PlacementTransfer> transfers = new ArrayList<>();
        for (TransferModel transfer : data.getEffectiveTransfers()) {
            LocalDate date = parseDate(transfer.date());
            if (transfer.placement() == null || date == null) {
                continue;
            }
            transfers.add(new PlacementTransfer(transfer.placement(), date, transfer.getEffectiveAmount()));
        }

        return new PatrimoineProjectionInput(
                placements, transfers, cashflow(data, settings, startYear, endYear), parameters);
    }

    private static PlacementProjectionInput toPlacement(PlacementModel placement) {
        return new PlacementProjectionInput(
                placement.id(),
                placement.label(),
                placement.getEffectiveBalance(),
                parseDate(placement.balanceDate()),
                placement.getEffectiveMonthly(),
                toYearMonth(placement.monthlyFrom()),
                toYearMonth(placement.monthlyUntil()),
                placement.getEffectiveRatePess(),
                placement.getEffectiveRateCorr(),
                placement.getEffectiveRateOpti(),
                placement.isExcludedFromRetirement(),
                placement.sweepPriority(),
                placement.sweepCap(),
                placement.pauseTriggerBalance(),
                placement.pausePriority());
    }

    private static CashflowProjection cashflow(BudgetDataModel data, SettingsModel settings, int startYear, int endYear) {
        BigDecimal inflationRate = settings.getEffectiveInflationRate();
        List<AnnualCashflow> years = new ArrayList<>();
        for (int year = startYear; year <= endYear; year++) {
            BigDecimal income = BigDecimal.ZERO;
            for (IncomeModel row : data.getEffectiveIncomes()) {
                income = income.add(incomeAnnualForYear(row, year));
            }
            BigDecimal charges = BigDecimal.ZERO;
            for (ChargeModel row : data.getEffectiveCharges()) {
                charges = charges.add(chargeAnnualForYear(row, year, inflationRate));
            }
            BigDecimal oneOff = BigDecimal.ZERO;
            for (OneOffExpenseModel expense : data.getEffectiveOneoff()) {
                Integer expenseYear = yearOf(expense.date());
                if (expenseYear != null && expenseYear == year) {
                    oneOff = oneOff.add(expense.getEffectiveAmount());
                }
            }
            years.add(new AnnualCashflow(year, income.subtract(charges).subtract(oneOff)));
        }
        return new CashflowProjection(years);
    }

    private static BigDecimal incomeAnnualForYear(IncomeModel row, int year) {
        Integer startYear = yearOf(row.start());
        if (startYear == null) startYear = year;

        int yearsElapsed = Math.max(0, year - startYear);
        double factor = Math.pow(1.0 + row.getEffectiveGrowthRate().doubleValue(), yearsElapsed);
        BigDecimal effectiveMonthly = row.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));

        int monthsActive = monthsActiveInYear(row.start(), row.end(), year);
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActive));
    }

    private static BigDecimal chargeAnnualForYear(ChargeModel row, int year, BigDecimal defaultInflation) {
        Integer startYear = yearOf(row.start());
        if (startYear == null) startYear = year;

        BigDecimal growth = row.getEffectiveGrowthRate(defaultInflation);
        int yearsElapsed = Math.max(0, year - startYear);
        double factor = Math.pow(1.0 + growth.doubleValue(), yearsElapsed);

        BigDecimal effectiveMonthly = row.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));
        int monthsActive = monthsActiveInYear(row.start(), row.end(), year);
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActive));
    }

    private static int monthsActiveInYear(String startISO, String endISO, int year) {
        LocalDate start = parseDate(startISO);
        LocalDate end = parseDate(endISO);
        if (start == null || end == null) return 0;

        LocalDate yStart = LocalDate.of(year, 1, 1);
        LocalDate yEnd = LocalDate.of(year, 12, 31);

        LocalDate s = start.isAfter(yStart) ? start : yStart;
        LocalDate e = end.isBefore(yEnd) ? end : yEnd;

        if (e.isBefore(s)) return 0;
        return (e.getYear() - s.getYear()) * 12 + (e.getMonthValue() - s.getMonthValue()) + 1;
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

        int earliestYear = 2026;
        boolean found = false;

        for (String d : dates) {
            Integer y = yearOf(d);
            if (y != null) {
                if (!found || y < earliestYear) {
                    earliestYear = y;
                    found = true;
                }
            }
        }

        return found ? earliestYear : 2026;
    }

    private static YearMonth toYearMonth(String dateISO) {
        LocalDate date = parseDate(dateISO);
        return date != null ? YearMonth.from(date) : null;
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
            LOG.warn("Date ISO illisible dans la construction de l'entrée patrimoine, ignorée : '{}'", dateISO, e);
            return null;
        }
    }
}
