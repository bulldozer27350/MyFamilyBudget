package com.moe.myfamilybudget.application.factory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseInput;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisBudgetLine;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisCategory;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisMatching;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisPendingOperation;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisPeriod;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisTransaction;
import com.moe.myfamilybudget.domain.analysis.calculation.BudgetLineKind;
import com.moe.myfamilybudget.domain.analysis.calculation.MonthlyBudgetLines;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * Factory construisant un {@link AnalyseInput} a partir de l'import bancaire et des fragments du
 * budget (RF-601, voir doc/architecture/08-domaine-analyse.md).
 *
 * <p>SILO-115 : cette factory ne connait plus {@code BudgetDataModel} ; l'appelant lui fournit
 * l'import bancaire et des {@link PointageInputFactory.Sources} (charges, revenus, placements,
 * inflation). Les listes absentes sont lues comme vides.
 *
 * <p>SILO-133 : Analyse definit ses propres types d'entree ; cette factory traduit les types du silo
 * Banque/Pointage (transactions, categories, rapprochements, operations en cours, lignes budgetaires) vers
 * ceux d'Analyse.
 *
 * <p>Isole {@link com.moe.myfamilybudget.domain.analysis.calculation.AnalyseCalculationService} de toute dependance
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

        List<AnalysisTransaction> transactions = bankImport != null && bankImport.transactions() != null
                ? bankImport.transactions().stream().filter(t -> t != null).map(AnalyseInputFactory::toTransaction).toList()
                : Collections.emptyList();

        List<AnalysisCategory> categories = bankImport != null && bankImport.categories() != null
                ? bankImport.categories().stream().filter(c -> c != null)
                        .map(c -> new AnalysisCategory(c.id(), c.label(), c.kind(), c.compressible())).toList()
                : Collections.emptyList();

        List<AnalysisMatching> matchings = bankImport != null && bankImport.matchings() != null
                ? bankImport.matchings().stream().filter(m -> m != null).map(AnalyseInputFactory::toMatching).toList()
                : Collections.emptyList();

        List<AnalysisPendingOperation> pendingOperations = bankImport != null && bankImport.pendingOperations() != null
                ? bankImport.pendingOperations().stream().filter(op -> op != null)
                        .map(op -> new AnalysisPendingOperation(op.date(), op.amount(), op.status(), op.budgetLineId())).toList()
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
            monthlyBudgetLines.add(new MonthlyBudgetLines(monthISO, monthLines.stream()
                    .filter(l -> l != null)
                    .map(l -> new AnalysisBudgetLine(l.id(), l.label(), l.kind(), l.monthly()))
                    .toList()));
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

    private static AnalysisTransaction toTransaction(BankImportModel.BankTransactionModel t) {
        List<AnalysisTransaction.Split> splits = t.splits() == null
                ? Collections.emptyList()
                : t.splits().stream().filter(sp -> sp != null)
                        .map(sp -> new AnalysisTransaction.Split(sp.id(), sp.categoryId(), sp.amount())).toList();
        return new AnalysisTransaction(t.id(), t.date(), t.amount(), t.categoryId(), splits);
    }

    private static AnalysisMatching toMatching(BankImportModel.MatchingModel m) {
        List<AnalysisMatching.Link> links = m.links() == null
                ? Collections.emptyList()
                : m.links().stream().filter(l -> l != null)
                        .map(l -> new AnalysisMatching.Link(l.budgetLineId(), l.txIds())).toList();
        return new AnalysisMatching(m.month(), links);
    }
}
