package com.moe.myfamilybudget.server.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.AnalyseInput;
import com.moe.myfamilybudget.server.internal.calculation.AnalysisPeriod;
import com.moe.myfamilybudget.server.internal.calculation.BudgetLineKind;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.server.internal.calculation.MonthlyBudgetLines;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.BankTransactionModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.BankTransactionSplitModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.CategoryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.MatchingLinkModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.MatchingModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.PendingOperationModel;

/**
 * RF-602 : tests de composant pour {@link AnalyseCalculator}, construits uniquement avec
 * {@link AnalyseInput} -- aucun {@code BudgetDataModel}, aucune charge/revenu/placement/reglages
 * du budget et aucun contexte Spring.
 */
@DisplayName("AnalyseCalculator - tests de composant sur AnalyseInput")
class AnalyseCalculatorComponentTest {

    private static final LocalDate REF_DATE = LocalDate.of(2026, 8, 15);
    private static final String CURRENT_MONTH_ISO = "2026-08";

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private static BankTransactionModel tx(String id, String date, String label, String amount, String catId) {
        return new BankTransactionModel(id, date, label, "cb", bd(amount), catId);
    }

    private static BankTransactionModel txSplit(String id, String date, String label, String amount, String defaultCatId, List<BankTransactionSplitModel> splits) {
        return new BankTransactionModel(id, date, label, "cb", bd(amount), defaultCatId, splits);
    }

    private static CategoryModel cat(String id, String label, String kind, String compressible) {
        return new CategoryModel(id, label, kind, compressible);
    }

    private static MatchingModel matching(String month, MatchingLinkModel... links) {
        return new MatchingModel(month, List.of(links));
    }

    private static MatchingLinkModel link(String lineId, String... txIds) {
        return new MatchingLinkModel(lineId, List.of(txIds));
    }

    private static PendingOperationModel pending(String id, String date, String amount, String lineId) {
        return new PendingOperationModel(id, date, date, "cb", "", "Op en cours", bd(amount), "", "pending", null, null, "", Collections.emptyList(), lineId);
    }

    private static BudgetLineProjection line(String id, String label, String kind, String monthly, String catId) {
        return new BudgetLineProjection(id, label, kind, bd(monthly), catId);
    }

    private static MonthlyBudgetLines monthlyLines(String monthISO, BudgetLineProjection... lines) {
        return new MonthlyBudgetLines(monthISO, List.of(lines));
    }

    private static BudgetLineKind lineKind(String lineId, String kind) {
        return new BudgetLineKind(lineId, kind);
    }

    @Nested
    @DisplayName("Validation et robustesse des entrees")
    class ValidationTests {

        @Test
        @DisplayName("Entree nulle geree sans NullPointerException")
        void nullInput() {
            AnalyseResultModel result = AnalyseCalculator.computeAnalyse(null);
            assertThat(result).isNotNull();
            assertThat(result.kpis()).isNotNull();
            assertThat(result.landingData()).isEmpty();
            assertThat(result.driftRows()).isEmpty();
            assertThat(result.categorySummaries()).isEmpty();
            assertThat(result.monthlyCompareData()).hasSize(12);
        }

        @Test
        @DisplayName("Input minimal vide")
        void minimalEmptyInput() {
            AnalyseInput input = new AnalyseInput(
                    new AnalysisPeriod(REF_DATE, 12),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );

            AnalyseResultModel result = AnalyseCalculator.computeAnalyse(input);
            assertThat(result).isNotNull();
            assertThat(result.currentMonthISO()).isEqualTo("2026-08");
            assertThat(result.currentMonthLabel()).isEqualTo("août 2026");
            assertThat(result.kpis().totalExpenses()).isEqualByComparingTo("0.00");
            assertThat(result.kpis().totalIncome()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("KPIs et ventilation par categorie")
    class CategoryAndKpiTests {

        @Test
        @DisplayName("Transactions simples avec depenses, revenus et categories compressibles")
        void simpleTransactions() {
            CategoryModel catLogement = cat("cat_logement", "Logement", "Dépense", "Non");
            CategoryModel catCourses = cat("cat_courses", "Alimentation", "Dépense", "Oui");
            CategoryModel catSalaire = cat("cat_salaire", "Salaire", "Revenu", "Non");

            List<BankTransactionModel> txs = List.of(
                    tx("tx1", "2026-08-01", "Loyer", "-1000.00", "cat_logement"),
                    tx("tx2", "2026-08-05", "Supermarche", "-150.00", "cat_courses"),
                    tx("tx3", "2026-08-10", "Pain", "-10.00", null), // non catégorisé
                    tx("tx4", "2026-08-25", "Virement Employeur", "3500.00", "cat_salaire")
            );

            AnalyseInput input = new AnalyseInput(
                    new AnalysisPeriod(REF_DATE, 12),
                    txs,
                    List.of(catLogement, catCourses, catSalaire),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );

            AnalyseResultModel result = AnalyseCalculator.computeAnalyse(input);

            assertThat(result.kpis().totalExpenses()).isEqualByComparingTo("1160.00");
            assertThat(result.kpis().totalIncome()).isEqualByComparingTo("3500.00");
            assertThat(result.kpis().uncategorizedCount()).isEqualTo(1);
            assertThat(result.kpis().compressibleTotal()).isEqualByComparingTo("150.00");

            assertThat(result.categorySummaries()).hasSize(3);
            AnalyseCategorySummaryModel sumLogement = result.categorySummaries().stream()
                    .filter(c -> "Logement".equals(c.label())).findFirst().orElseThrow();
            assertThat(sumLogement.amount()).isEqualByComparingTo("1000.00");

            AnalyseCategorySummaryModel sumUncat = result.categorySummaries().stream()
                    .filter(c -> "Non catégorisé".equals(c.label())).findFirst().orElseThrow();
            assertThat(sumUncat.amount()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("Transactions ventilees (splits) par sous-categories")
        void splitTransactions() {
            CategoryModel catFood = cat("cat_food", "Alimentation", "Dépense", "Non");
            CategoryModel catClothes = cat("cat_clothes", "Habillement", "Dépense", "Oui");

            BankTransactionSplitModel s1 = new BankTransactionSplitModel("s1", "cat_food", bd("-70.00"), "Nourriture");
            BankTransactionSplitModel s2 = new BankTransactionSplitModel("s2", "cat_clothes", bd("-30.00"), "Habits");

            BankTransactionModel txS = txSplit("tx_s", "2026-08-10", "Grand Magasin", "-100.00", "cat_default", List.of(s1, s2));

            AnalyseInput input = new AnalyseInput(
                    new AnalysisPeriod(REF_DATE, 12),
                    List.of(txS),
                    List.of(catFood, catClothes),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );

            AnalyseResultModel result = AnalyseCalculator.computeAnalyse(input);

            assertThat(result.kpis().totalExpenses()).isEqualByComparingTo("100.00");
            assertThat(result.kpis().compressibleTotal()).isEqualByComparingTo("30.00");

            AnalyseCategorySummaryModel foodSum = result.categorySummaries().stream()
                    .filter(c -> "Alimentation".equals(c.label())).findFirst().orElseThrow();
            assertThat(foodSum.amount()).isEqualByComparingTo("70.00");
        }
    }

    @Nested
    @DisplayName("Atterrissage du mois courant (landing data)")
    class LandingDataTests {

        @Test
        @DisplayName("Calcul de l'atterrissage avec pointage et operations en cours")
        void landingWithMatchingAndPending() {
            BudgetLineProjection loyerLine = line("c1", "Loyer", "charge", "1000.00", "cat1");
            BudgetLineProjection coursesLine = line("c2", "Courses", "charge", "500.00", "cat2");

            BankTransactionModel txLoyer = tx("t1", "2026-08-02", "Loyer", "-1000.00", "cat1");
            BankTransactionModel txCourses1 = tx("t2", "2026-08-04", "Monoprix", "-200.00", "cat2");

            MatchingLinkModel linkLoyer = link("c1", "t1");
            MatchingLinkModel linkCourses = link("c2", "t2");
            MatchingModel mCurrent = matching(CURRENT_MONTH_ISO, linkLoyer, linkCourses);

            PendingOperationModel pendingCourses = pending("p1", "2026-08-14", "150.00", "c2");

            AnalyseInput input = new AnalyseInput(
                    new AnalysisPeriod(REF_DATE, 12),
                    List.of(txLoyer, txCourses1),
                    Collections.emptyList(),
                    List.of(mCurrent),
                    List.of(pendingCourses),
                    List.of(monthlyLines(CURRENT_MONTH_ISO, loyerLine, coursesLine)),
                    List.of(lineKind("c1", "charge"), lineKind("c2", "charge"))
            );

            AnalyseResultModel result = AnalyseCalculator.computeAnalyse(input);

            assertThat(result.landingData()).hasSize(2);

            AnalyseLandingRowModel rowLoyer = result.landingData().stream()
                    .filter(r -> "c1".equals(r.id())).findFirst().orElseThrow();
            assertThat(rowLoyer.budgeted()).isEqualByComparingTo("1000.00");
            assertThat(rowLoyer.reel()).isEqualByComparingTo("1000.00");
            assertThat(rowLoyer.status()).isEqualTo("match");

            AnalyseLandingRowModel rowCourses = result.landingData().stream()
                    .filter(r -> "c2".equals(r.id())).findFirst().orElseThrow();
            assertThat(rowCourses.budgeted()).isEqualByComparingTo("500.00");
            // 200 tx + 150 pending = 350 reel
            assertThat(rowCourses.reel()).isEqualByComparingTo("350.00");
            assertThat(rowCourses.pendingContrib()).isEqualByComparingTo("150.00");
            assertThat(rowCourses.hasPendingContrib()).isTrue();
            // Depense : 350 < 500 => economy
            assertThat(rowCourses.status()).isEqualTo("economy");
        }
    }

    @Nested
    @DisplayName("Comparatif mensuel (monthlyCompareData)")
    class MonthlyCompareTests {

        @Test
        @DisplayName("Generation des mois du comparatif avec montants budgetes et reels")
        void monthlyCompareFlow() {
            BudgetLineProjection lineJul = line("c1", "Charge", "charge", "400.00", "cat");
            BudgetLineProjection lineAug = line("c1", "Charge", "charge", "450.00", "cat");

            BankTransactionModel txJul = tx("t_jul", "2026-07-10", "Charge Juil", "-390.00", "cat");
            BankTransactionModel txAug = tx("t_aug", "2026-08-10", "Charge Aout", "-450.00", "cat");

            MatchingModel mJul = matching("2026-07", link("c1", "t_jul"));
            MatchingModel mAug = matching("2026-08", link("c1", "t_aug"));

            AnalyseInput input = new AnalyseInput(
                    new AnalysisPeriod(REF_DATE, 3), // fenetre 3 mois
                    List.of(txJul, txAug),
                    Collections.emptyList(),
                    List.of(mJul, mAug),
                    Collections.emptyList(),
                    List.of(
                            monthlyLines("2026-06"),
                            monthlyLines("2026-07", lineJul),
                            monthlyLines("2026-08", lineAug)
                    ),
                    List.of(lineKind("c1", "charge"))
            );

            AnalyseResultModel result = AnalyseCalculator.computeAnalyse(input);

            assertThat(result.monthlyCompareData()).hasSize(3);

            AnalyseMonthlyCompareModel rowJul = result.monthlyCompareData().stream()
                    .filter(m -> "2026-07".equals(m.monthISO())).findFirst().orElseThrow();
            assertThat(rowJul.budgeted()).isEqualByComparingTo("400.00");
            assertThat(rowJul.reel()).isEqualByComparingTo("390.00");
            assertThat(rowJul.hasPointing()).isTrue();

            AnalyseMonthlyCompareModel rowAug = result.monthlyCompareData().stream()
                    .filter(m -> "2026-08".equals(m.monthISO())).findFirst().orElseThrow();
            assertThat(rowAug.budgeted()).isEqualByComparingTo("450.00");
            assertThat(rowAug.reel()).isEqualByComparingTo("450.00");
            assertThat(rowAug.hasPointing()).isTrue();
        }
    }

    @Nested
    @DisplayName("Derives par ligne (driftRows)")
    class DriftRowsTests {

        @Test
        @DisplayName("Calcul des moyennes 3m/12m et statut de derive")
        void driftCalculation() {
            BudgetLineProjection c1 = line("c1", "Electricite", "charge", "100.00", "cat");

            BankTransactionModel t6 = tx("t6", "2026-06-05", "EDF Juin", "-120.00", "cat");
            BankTransactionModel t7 = tx("t7", "2026-07-05", "EDF Juil", "-130.00", "cat");
            BankTransactionModel t8 = tx("t8", "2026-08-05", "EDF Aout", "-140.00", "cat");

            MatchingModel m6 = matching("2026-06", link("c1", "t6"));
            MatchingModel m7 = matching("2026-07", link("c1", "t7"));
            MatchingModel m8 = matching("2026-08", link("c1", "t8"));

            AnalyseInput input = new AnalyseInput(
                    new AnalysisPeriod(REF_DATE, 12),
                    List.of(t6, t7, t8),
                    Collections.emptyList(),
                    List.of(m6, m7, m8),
                    Collections.emptyList(),
                    List.of(monthlyLines(CURRENT_MONTH_ISO, c1)),
                    List.of(lineKind("c1", "charge"))
            );

            AnalyseResultModel result = AnalyseCalculator.computeAnalyse(input);

            assertThat(result.driftRows()).hasSize(1);
            AnalyseDriftRowModel drift = result.driftRows().get(0);
            assertThat(drift.id()).isEqualTo("c1");
            assertThat(drift.budgeted()).isEqualByComparingTo("100.00");
            // avg 3m = (120 + 130 + 140) / 3 = 130.00
            assertThat(drift.avg3m()).isEqualByComparingTo("130.00");
            assertThat(drift.ecart()).isEqualByComparingTo("30.00");
            // Depense : 130 > 100 => over
            assertThat(drift.status()).isEqualTo("over");
            assertThat(drift.months()).isEqualTo(3);
        }
    }
}