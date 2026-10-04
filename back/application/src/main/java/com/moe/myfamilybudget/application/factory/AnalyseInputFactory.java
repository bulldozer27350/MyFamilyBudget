package com.moe.myfamilybudget.application.factory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseInput;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisPeriod;
import com.moe.myfamilybudget.domain.analysis.calculation.BudgetLineKind;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.domain.analysis.calculation.MonthlyBudgetLines;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * Factory construisant un {@link AnalyseInput} a partir de l'import bancaire et des fragments du
 * budget (RF-601, voir doc/architecture/08-domaine-analyse.md).
 *
 * <p>SILO-115 : cette factory ne connait plus {@code BudgetDataModel} ; l'appelant lui fournit
 * l'import bancaire et des {@link PointageInputFactory.Sources} (charges, revenus, placements,
 * inflation). Les listes absentes sont lues comme vides.
 *
 * <p>Isole {@link com.moe.myfamilybudget.domain.analysis.calculation.AnalyseCalculator} de toute dependance
 * aux modeles de persistance et aux reglages du budget.
 */
@Component
public class AnalyseInputFactory {

    public AnalyseInput from(BankImportModel bankImport, PointageInputFactory.Sources data, Integer monthsBack) {
        return from(bankImport, data, monthsBack, LocalDate.now());
    }

    public AnalyseInput from(BankImportModel bankImport, PointageInputFactory.Sources data, Integer monthsBack,
            LocalDate today) {
        if (today == null) {
            today = LocalDate.now();
        }
        int mBack = (monthsBack != null && monthsBack >= 0) ? monthsBack : 12;
        AnalysisPeriod period = new AnalysisPeriod(today, mBack);

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
                    data != null ? data.inflationRate() : null,
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