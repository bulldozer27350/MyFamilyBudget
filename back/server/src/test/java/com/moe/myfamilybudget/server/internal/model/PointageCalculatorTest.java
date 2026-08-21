package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PointageCalculator Unit Tests (Pure Domain Model)")
class PointageCalculatorTest {

    @Test
    @DisplayName("calculateActiveBudgetLines filters charges, incomes and placements active for monthISO")
    void testCalculateActiveBudgetLines() {
        ChargeModel c1 = new ChargeModel("c1", "Loyer", new BigDecimal("800"), "2026-01", "2026-12", null, "cat1", "");
        ChargeModel c2 = new ChargeModel("c2", "Assurance", new BigDecimal("50"), "2027-01", null, null, "cat1", "");

        IncomeModel i1 = new IncomeModel("i1", "Salaire", new BigDecimal("3500"), "2026-01", null, null, "cat2", "");

        PlacementModel p1 = new PlacementModel("p1", "Livret A", "cat3", new BigDecimal("200"), "2026-01", new BigDecimal("0"), "2026-01", "2026-12", null, null, null, null, "");

        PointageModel model = new PointageModel(
                List.of(), List.of(), List.of(),
                List.of(c1, c2), List.of(i1), List.of(p1), null
        );

        List<PointageBudgetLineModel> lines = PointageCalculator.calculateActiveBudgetLines(model, "2026-05");

        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(PointageBudgetLineModel::id).containsExactly("c1", "i1");
        assertThat(lines.get(0).monthly()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(lines.get(1).kind()).isEqualTo("revenu");
    }

    @Test
    @DisplayName("filterTransactionsForMonth selects only transactions matching monthISO prefix")
    void testFilterTransactionsForMonth() {
        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel("tx1", "2026-05-12", "Carrefour", new BigDecimal("-45.50"));
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel("tx2", "2026-06-01", "Super U", new BigDecimal("-30.00"));

        List<BankImportModel.BankTransactionModel> monthTxs = PointageCalculator.filterTransactionsForMonth(List.of(tx1, tx2), "2026-05");

        assertThat(monthTxs).containsExactly(tx1);
    }

    @Test
    @DisplayName("calculateMonthBankSummary correctly computes expenses, income, unpointed count and expenses")
    void testCalculateMonthBankSummary() {
        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel("tx1", "2026-05-10", "Achat 1", new BigDecimal("-100.00"));
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel("tx2", "2026-05-15", "Virement 1", new BigDecimal("+500.00"));
        BankImportModel.BankTransactionModel tx3 = new BankImportModel.BankTransactionModel("tx3", "2026-05-20", "Achat 2", new BigDecimal("-50.00"));

        Set<String> pointed = Set.of("tx1");

        PointageMonthSummaryModel summary = PointageCalculator.calculateMonthBankSummary(List.of(tx1, tx2, tx3), pointed);

        assertThat(summary.totalBankExpenses()).isEqualByComparingTo("150.00");
        assertThat(summary.totalBankIncome()).isEqualByComparingTo("500.00");
        assertThat(summary.unpointedCount()).isEqualTo(2); // tx2 & tx3
        assertThat(summary.unpointedExpenses()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("calculateRealByLine aggregates transaction amounts per budget line")
    void testCalculateRealByLine() {
        PointageBudgetLineModel line1 = new PointageBudgetLineModel("c1", "Loyer", "charge", new BigDecimal("800"), "cat1");
        PointageBudgetLineModel line2 = new PointageBudgetLineModel("i1", "Salaire", "revenu", new BigDecimal("3000"), "cat2");

        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel("tx1", "2026-05-01", "Loyer mai", new BigDecimal("-800.00"));
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel("tx2", "2026-05-28", "Salaire mai", new BigDecimal("3100.00"));

        BankImportModel.MatchingLinkModel link1 = new BankImportModel.MatchingLinkModel("c1", List.of("tx1"));
        BankImportModel.MatchingLinkModel link2 = new BankImportModel.MatchingLinkModel("i1", List.of("tx2"));
        BankImportModel.MatchingModel matching = new BankImportModel.MatchingModel("2026-05", List.of(link1, link2));

        Map<String, BigDecimal> realMap = PointageCalculator.calculateRealByLine(List.of(tx1, tx2), matching, List.of(line1, line2));

        assertThat(realMap.get("c1")).isEqualByComparingTo("800.00");
        assertThat(realMap.get("i1")).isEqualByComparingTo("3100.00");
    }

    @Test
    @DisplayName("calculateLineStatus evaluates match, economy, over, and pending statuses")
    void testCalculateLineStatus() {
        PointageBudgetLineModel lineCharge = new PointageBudgetLineModel("c1", "Courses", "charge", new BigDecimal("200.00"), "cat1");

        BankImportModel.MatchingLinkModel link = new BankImportModel.MatchingLinkModel("c1", List.of("tx1"));
        BankImportModel.MatchingModel matching = new BankImportModel.MatchingModel("2026-05", List.of(link));

        // Exact match within tolerance
        PointageLineStatusModel statusMatch = PointageCalculator.calculateLineStatus(lineCharge, matching, new BigDecimal("201.00"));
        assertThat(statusMatch.status()).isEqualTo("match");

        // Economy (spent 150 < 200)
        PointageLineStatusModel statusEco = PointageCalculator.calculateLineStatus(lineCharge, matching, new BigDecimal("150.00"));
        assertThat(statusEco.status()).isEqualTo("economy");

        // Over (spent 250 > 200)
        PointageLineStatusModel statusOver = PointageCalculator.calculateLineStatus(lineCharge, matching, new BigDecimal("250.00"));
        assertThat(statusOver.status()).isEqualTo("over");

        // Pending when no link
        PointageLineStatusModel statusPending = PointageCalculator.calculateLineStatus(lineCharge, new BankImportModel.MatchingModel("2026-05", List.of()), BigDecimal.ZERO);
        assertThat(statusPending.status()).isEqualTo("pending");
    }

    @Test
    @DisplayName("updateMatchingForMonth adds or replaces matching links for given month")
    void testUpdateMatchingForMonth() {
        BankImportModel.MatchingModel oldMatching = new BankImportModel.MatchingModel("2026-04", List.of());
        BankImportModel initial = new BankImportModel(null, List.of(), List.of(), List.of(), List.of(), List.of(oldMatching));

        BankImportModel.MatchingLinkModel newLink = new BankImportModel.MatchingLinkModel("c1", List.of("tx1"));
        BankImportModel updated = PointageCalculator.updateMatchingForMonth(initial, "2026-05", List.of(newLink));

        assertThat(updated.matchings()).hasSize(2);
        assertThat(updated.matchings()).extracting(BankImportModel.MatchingModel::month).containsExactlyInAnyOrder("2026-04", "2026-05");
    }

    @Test
    @DisplayName("updateMatchingForMonth throws exception on empty monthISO")
    void testUpdateMatchingForMonthThrowsOnEmptyMonth() {
        assertThatThrownBy(() -> PointageCalculator.updateMatchingForMonth(null, "", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
