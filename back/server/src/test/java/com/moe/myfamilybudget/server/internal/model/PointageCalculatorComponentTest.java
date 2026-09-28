package com.moe.myfamilybudget.server.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.server.internal.calculation.PointageInput;
import com.moe.myfamilybudget.server.internal.calculation.PointagePeriod;

/**
 * RF-502 : tests de composant du moteur de pointage, construits uniquement avec
 * {@link PointageInput} — aucun {@code BudgetDataModel}, aucune charge, aucun revenu, aucun
 * placement, aucun paramètre et aucun contexte Spring.
 */
@DisplayName("PointageCalculator - tests de composant sur PointageInput")
class PointageCalculatorComponentTest {

    private static final BudgetLineProjection LOYER =
            new BudgetLineProjection("c1", "Loyer", "charge", new BigDecimal("800"), "cat1");
    private static final BudgetLineProjection COURSES =
            new BudgetLineProjection("c2", "Courses", "charge", new BigDecimal("400"), "cat2");
    private static final BudgetLineProjection SALAIRE =
            new BudgetLineProjection("i1", "Salaire", "revenu", new BigDecimal("3000"), "cat3");
    private static final BudgetLineProjection EPARGNE =
            new BudgetLineProjection("p1", "Épargne : Livret A", "placement", new BigDecimal("200"), "cat4");

    private static BankImportModel.BankTransactionModel tx(String id, String date, String label, String amount) {
        return new BankImportModel.BankTransactionModel(id, date, label, new BigDecimal(amount));
    }

    private static BankImportModel.MatchingLinkModel link(String lineId, String... txIds) {
        return new BankImportModel.MatchingLinkModel(lineId, List.of(txIds));
    }

    private static PointageInput input(
            List<BankImportModel.BankTransactionModel> txs,
            List<BankImportModel.MatchingLinkModel> links,
            BudgetLineProjection... lines) {
        return new PointageInput(txs, links, List.of(lines), new PointagePeriod("2026-05"));
    }

    @Test
    @DisplayName("Scénario mensuel complet : filtre du mois, pointage, réel par ligne et résumé bancaire")
    void testFullMonthScenario() {
        PointageInput in = input(
                List.of(
                        tx("t1", "2026-05-01", "Loyer", "-800.00"),
                        tx("t2", "2026-05-03", "Carrefour", "-420.00"),
                        tx("t3", "2026-05-28", "Salaire", "3000.00"),
                        tx("t4", "2026-05-20", "Café", "-4.50"),
                        tx("t5", "2026-04-30", "Hors mois", "-99.00")),
                List.of(link("c1", "t1"), link("c2", "t2"), link("i1", "t3")),
                LOYER, COURSES, SALAIRE);

        assertThat(PointageCalculator.filterTransactionsForMonth(in))
                .extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("t1", "t2", "t3", "t4");
        assertThat(PointageCalculator.calculatePointedTxIds(in)).containsExactlyInAnyOrder("t1", "t2", "t3");

        Map<String, BigDecimal> real = PointageCalculator.calculateRealByLine(in);
        assertThat(real.get("c1")).isEqualByComparingTo("800.00");
        assertThat(real.get("c2")).isEqualByComparingTo("420.00");
        assertThat(real.get("i1")).isEqualByComparingTo("3000.00");

        PointageMonthSummaryModel summary = PointageCalculator.calculateMonthBankSummary(in);
        assertThat(summary.totalBankExpenses()).isEqualByComparingTo("1224.50");
        assertThat(summary.totalBankIncome()).isEqualByComparingTo("3000.00");
        assertThat(summary.unpointedCount()).isEqualTo(1);
        assertThat(summary.unpointedExpenses()).isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("Statuts d'une charge : match dans la tolérance, economy sous le prévu, over au-dessus")
    void testChargeStatuses() {
        PointageInput in = input(List.of(), List.of(link("c2", "t2")), COURSES);

        assertThat(PointageCalculator.calculateLineStatus(COURSES, in, new BigDecimal("405.00")).status()).isEqualTo("match");
        assertThat(PointageCalculator.calculateLineStatus(COURSES, in, new BigDecimal("300.00")).status()).isEqualTo("economy");
        assertThat(PointageCalculator.calculateLineStatus(COURSES, in, new BigDecimal("500.00")).status()).isEqualTo("over");
    }

    @Test
    @DisplayName("Statuts d'un revenu et d'un placement : le sens économie/dépassement est inversé")
    void testIncomeAndPlacementStatuses() {
        PointageInput in = input(List.of(), List.of(link("i1", "t3"), link("p1", "t6")), SALAIRE, EPARGNE);

        assertThat(PointageCalculator.calculateLineStatus(SALAIRE, in, new BigDecimal("3300.00")).status()).isEqualTo("economy");
        assertThat(PointageCalculator.calculateLineStatus(SALAIRE, in, new BigDecimal("2500.00")).status()).isEqualTo("over");
        assertThat(PointageCalculator.calculateLineStatus(EPARGNE, in, new BigDecimal("250.00")).status()).isEqualTo("economy");
        assertThat(PointageCalculator.calculateLineStatus(EPARGNE, in, new BigDecimal("100.00")).status()).isEqualTo("over");
    }

    @Test
    @DisplayName("Une ligne sans lien est pending avec le prévu comme écart")
    void testPendingLine() {
        PointageInput in = input(List.of(), List.of(link("c1", "t1")), LOYER, COURSES);

        PointageLineStatusModel status = PointageCalculator.calculateLineStatus(COURSES, in, BigDecimal.ZERO);

        assertThat(status.status()).isEqualTo("pending");
        assertThat(status.expectedAmount()).isEqualByComparingTo("400.00");
        assertThat(status.realAmount()).isEqualByComparingTo("0.00");
        assertThat(status.difference()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("Transaction ventilée : pointage partiel par split puis pointage complet")
    void testSplitTransaction() {
        BankImportModel.BankTransactionSplitModel s1 =
                new BankImportModel.BankTransactionSplitModel("s1", "cat2", new BigDecimal("-70.00"), "Alimentaire");
        BankImportModel.BankTransactionSplitModel s2 =
                new BankImportModel.BankTransactionSplitModel("s2", "cat1", new BigDecimal("-30.00"), "Maison");
        BankImportModel.BankTransactionModel split = new BankImportModel.BankTransactionModel(
                "t9", "2026-05-12", "Hypermarché", "cb", new BigDecimal("-100.00"), "cat2", List.of(s1, s2));

        PointageInput partial = input(List.of(split), List.of(link("c2", "t9#s1")), COURSES, LOYER);
        assertThat(PointageCalculator.calculateRealByLine(partial).get("c2")).isEqualByComparingTo("70.00");
        PointageMonthSummaryModel partialSummary = PointageCalculator.calculateMonthBankSummary(partial);
        assertThat(partialSummary.unpointedCount()).isEqualTo(1);
        assertThat(partialSummary.unpointedExpenses()).isEqualByComparingTo("30.00");

        PointageInput full = input(List.of(split), List.of(link("c2", "t9#s1"), link("c1", "t9#s2")), COURSES, LOYER);
        assertThat(PointageCalculator.calculateRealByLine(full).get("c1")).isEqualByComparingTo("30.00");
        assertThat(PointageCalculator.calculateMonthBankSummary(full).unpointedCount()).isZero();
    }

    @Test
    @DisplayName("Un lien vers une ligne absente des lignes actives n'est pas compté comme pointé")
    void testLinkToInactiveLine() {
        PointageInput in = input(
                List.of(tx("t1", "2026-05-02", "Ancienne charge", "-60.00")),
                List.of(link("ligne-terminee", "t1")),
                LOYER);

        assertThat(PointageCalculator.calculatePointedTxIds(in)).isEmpty();
        assertThat(PointageCalculator.calculateMonthBankSummary(in).unpointedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Entrée vide (valeurs nulles normalisées) : aucun résultat, aucune exception")
    void testEmptyInput() {
        PointageInput in = new PointageInput(null, null, null, null);

        assertThat(PointageCalculator.filterTransactionsForMonth(in)).isEmpty();
        assertThat(PointageCalculator.calculatePointedTxIds(in)).isEmpty();
        assertThat(PointageCalculator.calculateRealByLine(in)).isEmpty();
        PointageMonthSummaryModel summary = PointageCalculator.calculateMonthBankSummary(in);
        assertThat(summary.totalBankExpenses()).isEqualByComparingTo("0.00");
        assertThat(summary.unpointedCount()).isZero();
    }

    @Test
    @DisplayName("updateMatchingForMonth remplace uniquement le mois visé et conserve les autres")
    void testUpdateMatchingForMonth() {
        BankImportModel current = new BankImportModel(null, null, null, null, null, List.of(
                new BankImportModel.MatchingModel("2026-04", List.of(link("c1", "a1"))),
                new BankImportModel.MatchingModel("2026-05", List.of(link("c1", "old")))));

        BankImportModel updated = PointageCalculator.updateMatchingForMonth(current, "2026-05", List.of(link("c2", "t2")));

        assertThat(updated.matchings()).hasSize(2);
        assertThat(updated.matchings()).filteredOn(m -> "2026-04".equals(m.month()))
                .flatExtracting(BankImportModel.MatchingModel::links).containsExactly(link("c1", "a1"));
        assertThat(updated.matchings()).filteredOn(m -> "2026-05".equals(m.month()))
                .flatExtracting(BankImportModel.MatchingModel::links).containsExactly(link("c2", "t2"));
    }
}
