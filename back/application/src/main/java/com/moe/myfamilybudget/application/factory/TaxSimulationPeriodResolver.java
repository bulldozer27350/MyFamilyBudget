package com.moe.myfamilybudget.application.factory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.budget.TransferModel;
import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;

/**
 * Déduit la période de simulation fiscale à partir de fragments lus chez leurs propriétaires
 * (RF-202, voir doc/architecture/04-domaine-fiscalite.md ; SILO-111 : plus de {@code BudgetDataModel}).
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

    /**
     * Fragments nécessaires à la déduction de la période (SILO-111). Les listes absentes sont lues comme
     * vides ; {@code retirementSettings} et {@code simulationSettings} sont obligatoires.
     */
    public record Sources(
            RetirementSettingsModel retirementSettings,
            SimulationSettingsModel simulationSettings,
            String pivotDate,
            List<IncomeModel> incomes,
            List<ChargeModel> charges,
            List<PlacementModel> placements,
            List<OneOffExpenseModel> oneoff,
            List<TransferModel> transfers,
            BankImportModel bankImport) {
    }

    public static TaxSimulationPeriod resolve(Sources sources) {
        Objects.requireNonNull(sources, "sources");
        int birthYear = sources.retirementSettings().getEffectiveBirthYear();
        int retireYear = birthYear + sources.retirementSettings().getEffectiveRetireAge();
        int wantedEnd = birthYear + sources.simulationSettings().getEffectiveSimulateUntilAge();
        return new TaxSimulationPeriod(findEarliestYear(sources), Math.max(retireYear + 3, wantedEnd));
    }

    static int findEarliestYear(Sources sources) {
        List<String> dates = new ArrayList<>();

        for (IncomeModel i : orEmpty(sources.incomes())) if (i.start() != null) dates.add(i.start());
        for (ChargeModel c : orEmpty(sources.charges())) if (c.start() != null) dates.add(c.start());
        for (PlacementModel p : orEmpty(sources.placements())) {
            if (p.monthlyFrom() != null) dates.add(p.monthlyFrom());
            if (p.balanceDate() != null) dates.add(p.balanceDate());
        }
        for (OneOffExpenseModel o : orEmpty(sources.oneoff())) if (o.date() != null) dates.add(o.date());
        for (TransferModel t : orEmpty(sources.transfers())) if (t.date() != null) dates.add(t.date());
        if (sources.pivotDate() != null) dates.add(sources.pivotDate());

        if (sources.bankImport() != null && sources.bankImport().transactions() != null) {
            for (BankImportModel.BankTransactionModel t : sources.bankImport().transactions()) {
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

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
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
