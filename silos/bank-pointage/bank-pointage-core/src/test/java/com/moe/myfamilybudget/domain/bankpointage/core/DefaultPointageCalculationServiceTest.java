package com.moe.myfamilybudget.domain.bankpointage.core;

import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.PointageLineStatusModel;
import com.moe.myfamilybudget.domain.bankpointage.model.PointageMonthSummaryModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.bankpointage.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageInput;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointagePeriod;

@DisplayName("DefaultPointageCalculationService Unit Tests (Pure Domain Model)")
class DefaultPointageCalculationServiceTest {

    private final PointageCalculationService service = new DefaultPointageCalculationService();

    @Test
    @DisplayName("filterTransactionsForMonth selects only transactions matching monthISO prefix")
    void testFilterTransactionsForMonth() {
        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel("tx1", "2026-05-12", "Carrefour", new BigDecimal("-45.50"));
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel("tx2", "2026-06-01", "Super U", new BigDecimal("-30.00"));

        List<BankImportModel.BankTransactionModel> monthTxs = service.filterTransactionsForMonth(List.of(tx1, tx2), "2026-05");

        assertThat(monthTxs).containsExactly(tx1);
    }

    @Test
    @DisplayName("calculateMonthBankSummary correctly computes expenses, income, unpointed count and expenses")
    void testCalculateMonthBankSummary() {
        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel("tx1", "2026-05-10", "Achat 1", new BigDecimal("-100.00"));
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel("tx2", "2026-05-15", "Virement 1", new BigDecimal("+500.00"));
        BankImportModel.BankTransactionModel tx3 = new BankImportModel.BankTransactionModel("tx3", "2026-05-20", "Achat 2", new BigDecimal("-50.00"));

        Set<String> pointed = Set.of("tx1");

        PointageMonthSummaryModel summary = service.calculateMonthBankSummary(List.of(tx1, tx2, tx3), pointed);

        assertThat(summary.totalBankExpenses()).isEqualByComparingTo("150.00");
        assertThat(summary.totalBankIncome()).isEqualByComparingTo("500.00");
        assertThat(summary.unpointedCount()).isEqualTo(2); // tx2 & tx3
        assertThat(summary.unpointedExpenses()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("calculateRealByLine aggregates transaction amounts per budget line with whole transactions and splits")
    void testCalculateRealByLine() {
        BudgetLineProjection line1 = new BudgetLineProjection("c1", "Loyer", "charge", new BigDecimal("800"), "cat1");
        BudgetLineProjection line2 = new BudgetLineProjection("i1", "Salaire", "revenu", new BigDecimal("3000"), "cat2");

        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel("tx1", "2026-05-01", "Loyer mai", new BigDecimal("-800.00"));
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel("tx2", "2026-05-28", "Salaire mai", new BigDecimal("3100.00"));

        BankImportModel.MatchingLinkModel link1 = new BankImportModel.MatchingLinkModel("c1", List.of("tx1"));
        BankImportModel.MatchingLinkModel link2 = new BankImportModel.MatchingLinkModel("i1", List.of("tx2"));
        BankImportModel.MatchingModel matching = new BankImportModel.MatchingModel("2026-05", List.of(link1, link2));

        Map<String, BigDecimal> realMap = service.calculateRealByLine(List.of(tx1, tx2), matching, List.of(line1, line2));

        assertThat(realMap.get("c1")).isEqualByComparingTo("800.00");
        assertThat(realMap.get("i1")).isEqualByComparingTo("3100.00");
    }

    @Test
    @DisplayName("calculateRealByLine handles composite split IDs (txId#splitId)")
    void testCalculateRealByLineWithSplits() {
        BudgetLineProjection lineFood = new BudgetLineProjection("c_food", "Courses", "charge", new BigDecimal("300"), "cat_food");
        BudgetLineProjection lineClothes = new BudgetLineProjection("c_clothes", "Vêtements", "charge", new BigDecimal("100"), "cat_clothes");

        BankImportModel.BankTransactionSplitModel split1 = new BankImportModel.BankTransactionSplitModel("s1", "cat_food", new BigDecimal("-65.00"), "Nourriture");
        BankImportModel.BankTransactionSplitModel split2 = new BankImportModel.BankTransactionSplitModel("s2", "cat_clothes", new BigDecimal("-35.00"), "Pull");

        BankImportModel.BankTransactionModel txSplit = new BankImportModel.BankTransactionModel(
                "tx_leclerc", "2026-05-15", "LECLERC TICKET", "cb", new BigDecimal("-100.00"), "cat_default", List.of(split1, split2)
        );

        BankImportModel.MatchingLinkModel linkFood = new BankImportModel.MatchingLinkModel("c_food", List.of("tx_leclerc#s1"));
        BankImportModel.MatchingLinkModel linkClothes = new BankImportModel.MatchingLinkModel("c_clothes", List.of("tx_leclerc#s2"));
        BankImportModel.MatchingModel matching = new BankImportModel.MatchingModel("2026-05", List.of(linkFood, linkClothes));

        Map<String, BigDecimal> realMap = service.calculateRealByLine(List.of(txSplit), matching, List.of(lineFood, lineClothes));

        assertThat(realMap.get("c_food")).isEqualByComparingTo("65.00");
        assertThat(realMap.get("c_clothes")).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("calculateMonthBankSummary handles split transactions when partially or fully pointed")
    void testCalculateMonthBankSummaryWithSplits() {
        BankImportModel.BankTransactionSplitModel s1 = new BankImportModel.BankTransactionSplitModel("s1", "cat_food", new BigDecimal("-70.00"), "Alim");
        BankImportModel.BankTransactionSplitModel s2 = new BankImportModel.BankTransactionSplitModel("s2", "cat_other", new BigDecimal("-30.00"), "Autre");
        BankImportModel.BankTransactionModel txSplit = new BankImportModel.BankTransactionModel(
                "tx_split", "2026-05-10", "HYPERMARCHE", "cb", new BigDecimal("-100.00"), "cat_food", List.of(s1, s2)
        );

        // 1. Partial pointing (only s1 is pointed)
        Set<String> partialPointed = Set.of("tx_split#s1");
        PointageMonthSummaryModel summaryPartial = service.calculateMonthBankSummary(List.of(txSplit), partialPointed);
        assertThat(summaryPartial.totalBankExpenses()).isEqualByComparingTo("100.00");
        assertThat(summaryPartial.unpointedCount()).isEqualTo(1);
        assertThat(summaryPartial.unpointedExpenses()).isEqualByComparingTo("30.00"); // 100 - 70 = 30 remaining

        // 2. Full pointing via split IDs (both s1 and s2)
        Set<String> fullPointedSplits = Set.of("tx_split#s1", "tx_split#s2");
        PointageMonthSummaryModel summaryFull = service.calculateMonthBankSummary(List.of(txSplit), fullPointedSplits);
        assertThat(summaryFull.unpointedCount()).isEqualTo(0);
        assertThat(summaryFull.unpointedExpenses()).isEqualByComparingTo("0.00");

        // 3. Full pointing via parent txId
        Set<String> fullPointedTx = Set.of("tx_split");
        PointageMonthSummaryModel summaryFullTx = service.calculateMonthBankSummary(List.of(txSplit), fullPointedTx);
        assertThat(summaryFullTx.unpointedCount()).isEqualTo(0);
        assertThat(summaryFullTx.unpointedExpenses()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("resolveAmount correctly retrieves amount for full transaction and composite split reference")
    void testResolveAmount() {
        BankImportModel.BankTransactionSplitModel s1 = new BankImportModel.BankTransactionSplitModel("s1", "cat1", new BigDecimal("-40.00"), "Split 1");
        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel(
                "tx1", "2026-05-10", "TX 1", "cb", new BigDecimal("-100.00"), "cat1", List.of(s1)
        );
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel(
                "tx2", "2026-05-10", "TX 2", "cb", new BigDecimal("-50.00"), "cat1"
        );

        Map<String, BankImportModel.BankTransactionModel> txMap = Map.of("tx1", tx1, "tx2", tx2);

        // Whole transaction
        assertThat(service.resolveAmount("tx2", txMap)).isEqualByComparingTo("-50.00");
        // Split reference
        assertThat(service.resolveAmount("tx1#s1", txMap)).isEqualByComparingTo("-40.00");
        // Unknown split
        assertThat(service.resolveAmount("tx1#unknown", txMap)).isEqualByComparingTo("0.00");
        // Unknown tx
        assertThat(service.resolveAmount("tx_unknown", txMap)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("calculateLineStatus evaluates match, economy, over, and pending statuses")
    void testCalculateLineStatus() {
        BudgetLineProjection lineCharge = new BudgetLineProjection("c1", "Courses", "charge", new BigDecimal("200.00"), "cat1");

        BankImportModel.MatchingLinkModel link = new BankImportModel.MatchingLinkModel("c1", List.of("tx1"));
        BankImportModel.MatchingModel matching = new BankImportModel.MatchingModel("2026-05", List.of(link));

        // Exact match within tolerance
        PointageLineStatusModel statusMatch = service.calculateLineStatus(lineCharge, matching, new BigDecimal("201.00"));
        assertThat(statusMatch.status()).isEqualTo("match");

        // Economy (spent 150 < 200)
        PointageLineStatusModel statusEco = service.calculateLineStatus(lineCharge, matching, new BigDecimal("150.00"));
        assertThat(statusEco.status()).isEqualTo("economy");

        // Over (spent 250 > 200)
        PointageLineStatusModel statusOver = service.calculateLineStatus(lineCharge, matching, new BigDecimal("250.00"));
        assertThat(statusOver.status()).isEqualTo("over");

        // Pending when no link
        PointageLineStatusModel statusPending = service.calculateLineStatus(lineCharge, new BankImportModel.MatchingModel("2026-05", List.of()), BigDecimal.ZERO);
        assertThat(statusPending.status()).isEqualTo("pending");
    }

    @Test
    @DisplayName("updateMatchingForMonth adds or replaces matching links for given month")
    void testUpdateMatchingForMonth() {
        BankImportModel.MatchingModel oldMatching = new BankImportModel.MatchingModel("2026-04", List.of());
        BankImportModel initial = new BankImportModel(null, List.of(), List.of(), List.of(), List.of(), List.of(oldMatching));

        BankImportModel.MatchingLinkModel newLink = new BankImportModel.MatchingLinkModel("c1", List.of("tx1"));
        BankImportModel updated = service.updateMatchingForMonth(initial, "2026-05", List.of(newLink));

        assertThat(updated.matchings()).hasSize(2);
        assertThat(updated.matchings()).extracting(BankImportModel.MatchingModel::month).containsExactlyInAnyOrder("2026-04", "2026-05");
    }

    @Test
    @DisplayName("updateMatchingForMonth throws exception on empty monthISO")
    void testUpdateMatchingForMonthThrowsOnEmptyMonth() {
        assertThatThrownBy(() -> service.updateMatchingForMonth(null, "", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Points d'entrée sur PointageInput (RF-501) : aucun modèle du budget n'est nécessaire ---

    private static PointageInput sampleInput() {
        BudgetLineProjection loyer = new BudgetLineProjection("c1", "Loyer", "charge", new BigDecimal("800"), "cat1");
        BudgetLineProjection salaire = new BudgetLineProjection("i1", "Salaire", "revenu", new BigDecimal("3000"), "cat2");
        BudgetLineProjection epargne = new BudgetLineProjection("p1", "Épargne : Livret A", "placement", new BigDecimal("200"), "cat3");

        BankImportModel.BankTransactionModel txLoyer = new BankImportModel.BankTransactionModel("tx1", "2026-05-01", "Loyer mai", new BigDecimal("-800.00"));
        BankImportModel.BankTransactionModel txSalaire = new BankImportModel.BankTransactionModel("tx2", "2026-05-28", "Salaire mai", new BigDecimal("3100.00"));
        BankImportModel.BankTransactionModel txAutre = new BankImportModel.BankTransactionModel("tx3", "2026-05-15", "Achat", new BigDecimal("-25.00"));
        BankImportModel.BankTransactionModel txJuin = new BankImportModel.BankTransactionModel("tx4", "2026-06-02", "Achat juin", new BigDecimal("-10.00"));

        return new PointageInput(
                List.of(txLoyer, txSalaire, txAutre, txJuin),
                List.of(new BankImportModel.MatchingLinkModel("c1", List.of("tx1")),
                        new BankImportModel.MatchingLinkModel("i1", List.of("tx2")),
                        new BankImportModel.MatchingLinkModel("inconnue", List.of("tx3"))),
                List.of(loyer, salaire, epargne),
                new PointagePeriod("2026-05"));
    }

    @Test
    @DisplayName("PointageInput : filterTransactionsForMonth ne garde que le mois de la période")
    void testFilterTransactionsForMonthFromInput() {
        assertThat(service.filterTransactionsForMonth(sampleInput()))
                .extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx1", "tx2", "tx3");
    }

    @Test
    @DisplayName("PointageInput : calculatePointedTxIds ignore les liens vers des lignes non actives")
    void testCalculatePointedTxIdsFromInput() {
        assertThat(service.calculatePointedTxIds(sampleInput())).containsExactlyInAnyOrder("tx1", "tx2");
    }

    @Test
    @DisplayName("PointageInput : calculateRealByLine agrège le réel par ligne (signe selon la nature)")
    void testCalculateRealByLineFromInput() {
        Map<String, BigDecimal> real = service.calculateRealByLine(sampleInput());

        assertThat(real.get("c1")).isEqualByComparingTo("800.00");
        assertThat(real.get("i1")).isEqualByComparingTo("3100.00");
    }

    @Test
    @DisplayName("PointageInput : calculateMonthBankSummary calcule le résumé du mois pointé")
    void testCalculateMonthBankSummaryFromInput() {
        PointageMonthSummaryModel summary = service.calculateMonthBankSummary(sampleInput());

        assertThat(summary.totalBankExpenses()).isEqualByComparingTo("825.00");
        assertThat(summary.totalBankIncome()).isEqualByComparingTo("3100.00");
        assertThat(summary.unpointedCount()).isEqualTo(1); // tx3, lié à une ligne inactive
        assertThat(summary.unpointedExpenses()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("PointageInput : calculateLineStatus distingue pending et match")
    void testCalculateLineStatusFromInput() {
        PointageInput input = sampleInput();
        BudgetLineProjection loyer = input.activeBudgetLines().get(0);
        BudgetLineProjection epargne = input.activeBudgetLines().get(2);

        assertThat(service.calculateLineStatus(loyer, input, new BigDecimal("800.00")).status()).isEqualTo("match");
        assertThat(service.calculateLineStatus(epargne, input, BigDecimal.ZERO).status()).isEqualTo("pending");
    }

    @Test
    @DisplayName("PointageInput nul : les points d'entrée renvoient des résultats vides")
    void testNullInput() {
        assertThat(service.filterTransactionsForMonth((PointageInput) null)).isEmpty();
        assertThat(service.calculatePointedTxIds((PointageInput) null)).isEmpty();
        assertThat(service.calculateRealByLine((PointageInput) null)).isEmpty();
        assertThat(service.calculateMonthBankSummary((PointageInput) null).unpointedCount()).isZero();
    }
}
