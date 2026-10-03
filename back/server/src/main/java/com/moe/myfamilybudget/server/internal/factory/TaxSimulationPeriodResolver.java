package com.moe.myfamilybudget.server.internal.factory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.domain.budget.TransferModel;

/**
 * Déduit la période de simulation fiscale à partir de {@link BudgetDataModel} (RF-202, voir
 * doc/architecture/04-domaine-fiscalite.md).
 *
 * <p>Cette logique (ex-{@code TaxCalculator.findEarliestYear}) est sortie du calcul fiscal : elle
 * est appelée en amont par la couche application, qui passe ensuite la période explicitement à
 * {@link TaxInputFactory} via {@link TaxSimulationPeriod}. Le moteur fiscal ne connaît plus que
 * cette période.
 */
public final class TaxSimulationPeriodResolver {

    private static final Logger LOG = LoggerFactory.getLogger(TaxSimulationPeriodResolver.class);

    private static final int DEFAULT_START_YEAR = 2026;

    private TaxSimulationPeriodResolver() {
    }

    public static TaxSimulationPeriod resolve(BudgetDataModel data) {
        SettingsModel settings = data.getEffectiveSettings();
        int birthYear = settings.getEffectiveBirthYear();
        int retireYear = birthYear + settings.getEffectiveRetireAge();
        int wantedEnd = birthYear + settings.getEffectiveSimulateUntilAge();
        return new TaxSimulationPeriod(findEarliestYear(data), Math.max(retireYear + 3, wantedEnd));
    }

    static int findEarliestYear(BudgetDataModel data) {
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
        if (dateISO == null || dateISO.isBlank()) return null;
        try {
            LocalDate d = dateISO.length() == 7
                    ? YearMonth.parse(dateISO).atDay(1)
                    : LocalDate.parse(dateISO.substring(0, 10));
            return d.getYear();
        } catch (Exception e) {
            LOG.warn("Date ISO illisible dans le calcul de la période fiscale, ignorée : '{}'", dateISO, e);
            return null;
        }
    }
}
