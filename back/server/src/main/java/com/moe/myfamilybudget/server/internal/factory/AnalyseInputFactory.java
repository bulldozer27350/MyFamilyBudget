package com.moe.myfamilybudget.server.internal.factory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.calculation.AnalyseInput;
import com.moe.myfamilybudget.server.internal.calculation.AnalysisPeriod;
import com.moe.myfamilybudget.server.internal.calculation.BudgetLineKind;
import com.moe.myfamilybudget.server.internal.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.server.internal.calculation.MonthlyBudgetLines;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * Factory construisant un {@link AnalyseInput} a partir de {@link BudgetDataModel}
 * et {@link BankImportModel} (RF-601, voir doc/architecture/08-domaine-analyse.md).
 *
 * <p>Isole {@link com.moe.myfamilybudget.server.internal.model.AnalyseCalculator} de toute dependance
 * aux modeles de persistance et aux reglages du budget.
 */
@Component
public class AnalyseInputFactory {

    public AnalyseInput from(BudgetDataModel data, BankImportModel bankImport, Integer monthsBack) {
        return from(data, bankImport, monthsBack, LocalDate.now());
    }

    public AnalyseInput from(BudgetDataModel data, BankImportModel bankImport, Integer monthsBack, LocalDate today) {
        if (today == null) {
            today = LocalDate.now();
        }
        int mBack = (monthsBack != null && monthsBack >= 0) ? monthsBack : 12;
        AnalysisPeriod period = new AnalysisPeriod(today, mBack);

        if (bankImport == null && data != null) {
            bankImport = data.bankImport();
        }

        List<BankImportModel.BankTransactionModel> transactions = bankImport != null && bankImport.transactions() != null
                ? bankImport.transactions()
                : Collections.emptyList();

        List<BankImportModel.CategoryModel> categories = bankImport != null && bankImport.categories() != null
                ? bankImport.categories()
                : Collections.emptyList();

        List<BankImportModel.MatchingModel> matchings = bankImport != null && bankImport.matchings() != null
                ? bankImport.matchings()
                : Collections.emptyList();

        List<BankImportModel.PendingOperationModel> pendingOperations = bankImport != null && bankImport.pendingOperations() != null
                ? bankImport.pendingOperations()
                : Collections.emptyList();

        int nMonths = Math.min(mBack > 0 ? mBack : 12, 24);
        YearMonth currentYM = YearMonth.from(today);
        List<MonthlyBudgetLines> monthlyBudgetLines = new ArrayList<>(nMonths);
        for (int i = nMonths - 1; i >= 0; i--) {
            YearMonth ym = currentYM.minusMonths(i);
            String monthISO = ym.toString();
            List<BudgetLineProjection> monthLines = PointageInputFactory.activeBudgetLines(
                    data != null ? data.charges() : null,
                    data != null ? data.incomes() : null,
                    data != null ? data.placements() : null,
                    data != null ? data.settings() : null,
                    monthISO
            );
            monthlyBudgetLines.add(new MonthlyBudgetLines(monthISO, monthLines));
        }

        List<BudgetLineKind> lineKinds = new ArrayList<>();
        if (data != null) {
            if (data.charges() != null) {
                for (ChargeModel c : data.charges()) {
                    if (c != null && c.id() != null) {
                        lineKinds.add(new BudgetLineKind(c.id(), "charge"));
                    }
                }
            }
            if (data.incomes() != null) {
                for (IncomeModel inc : data.incomes()) {
                    if (inc != null && inc.id() != null) {
                        lineKinds.add(new BudgetLineKind(inc.id(), "revenu"));
                    }
                }
            }
            if (data.placements() != null) {
                for (PlacementModel p : data.placements()) {
                    if (p != null && p.id() != null) {
                        lineKinds.add(new BudgetLineKind(p.id(), "placement"));
                    }
                }
            }
        }

        return new AnalyseInput(
                period,
                transactions,
                categories,
                matchings,
                pendingOperations,
                monthlyBudgetLines,
                lineKinds
        );
    }
}