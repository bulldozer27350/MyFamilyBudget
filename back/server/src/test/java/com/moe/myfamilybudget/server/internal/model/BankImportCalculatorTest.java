package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BankImportCalculator Unit Tests")
class BankImportCalculatorTest {

    @Nested
    @DisplayName("CSV Parsing & Formatting Tests")
    class CSVAndParsingTests {

        @Test
        @DisplayName("parseCSVText split rows correctly and strips empty rows")
        void testParseCSVText() {
            String csv = "Date;Libelle;Montant\n15/01/2026;\"Supermarche Carrefour\";-45.50\n\n16/01/2026;Virement;-100.00";
            List<List<String>> rows = BankImportCalculator.parseCSVText(csv, ";");

            assertThat(rows).hasSize(3);
            assertThat(rows.get(0)).containsExactly("Date", "Libelle", "Montant");
            assertThat(rows.get(1)).containsExactly("15/01/2026", "Supermarche Carrefour", "-45.50");
        }

        @Test
        @DisplayName("parseDateWithFormat converts various formats to ISO YYYY-MM-DD")
        void testParseDateWithFormat() {
            assertThat(BankImportCalculator.parseDateWithFormat("15/01/2026", "DD/MM/YYYY")).isEqualTo("2026-01-15");
            assertThat(BankImportCalculator.parseDateWithFormat("2026-05-20", "YYYY-MM-DD")).isEqualTo("2026-05-20");
            assertThat(BankImportCalculator.parseDateWithFormat("01-12-25", "DD-MM-YY")).isEqualTo("2025-12-01");
            assertThat(BankImportCalculator.parseDateWithFormat("invalid", "DD/MM/YYYY")).isNull();
        }

        @Test
        @DisplayName("parseAmountText handles currency symbols, spaces, and French decimals")
        void testParseAmountText() {
            assertThat(BankImportCalculator.parseAmountText("1 234,56 €")).isEqualByComparingTo(new BigDecimal("1234.56"));
            assertThat(BankImportCalculator.parseAmountText("-45,50 €")).isEqualByComparingTo(new BigDecimal("-45.50"));
            assertThat(BankImportCalculator.parseAmountText(null)).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("parsePurchaseDateFromLabel extracts DU DDMMYY pattern from label")
        void testParsePurchaseDateFromLabel() {
            String label = "CARTE 15/01 SUPERMARCHE DU 140126";
            String purchaseDate = BankImportCalculator.parsePurchaseDateFromLabel(label);
            assertThat(purchaseDate).isEqualTo("2026-01-14");
        }
    }

    @Nested
    @DisplayName("Deduplication & Rules Tests")
    class DeduplicationAndRulesTests {

        @Test
        @DisplayName("transactionDedupeKey builds deterministic key based on date, label, and rounded cents")
        void testDedupeKey() {
            String key1 = BankImportCalculator.transactionDedupeKey("2026-01-15", "CARREFOUR ", new BigDecimal("45.50"));
            String key2 = BankImportCalculator.transactionDedupeKey("2026-01-15", "carrefour", new BigDecimal("45.50"));

            assertThat(key1).isEqualTo(key2);
            assertThat(key1).isEqualTo("2026-01-15|carrefour|4550");
        }

        @Test
        @DisplayName("applyRulesToTransactions categorizes matching unlabeled transactions")
        void testApplyRulesToTransactions() {
            List<BankImportModel.BankTransactionModel> txs = List.of(
                    new BankImportModel.BankTransactionModel("1", "2026-01-15", "CARREFOUR HYPER", "", new BigDecimal("-50.00"), ""),
                    new BankImportModel.BankTransactionModel("2", "2026-01-16", "TOTAL STATION", "", new BigDecimal("-30.00"), "")
            );

            List<BankImportModel.BankImportRuleModel> rules = List.of(
                    new BankImportModel.BankImportRuleModel("r1", "CARREFOUR", "cat_courses")
            );

            List<BankImportModel.BankTransactionModel> categorized = BankImportCalculator.applyRulesToTransactions(txs, rules);

            assertThat(categorized.get(0).categoryId()).isEqualTo("cat_courses");
            assertThat(categorized.get(1).categoryId()).isEmpty();
        }

        @Test
        @DisplayName("importTransactions rejects missing mandatory columns with exception")
        void testImportTransactionsMissingColumns() {
            List<String> invalidRoles = List.of("label", "amount"); // Missing date
            assertThatThrownBy(() -> BankImportCalculator.importTransactions(List.of(), invalidRoles, null, List.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Il faut au minimum assigner les rôles Date, Libellé et Montant");
        }

        @Test
        @DisplayName("importTransactions deduplicates against existing stored transactions")
        void testImportTransactionsDeduplication() {
            List<String> colRoles = List.of("date", "label", "amount");
            BankImportModel.BankColumnMappingModel mapping = new BankImportModel.BankColumnMappingModel(";", "DD/MM/YYYY", false, 0, 1, null, 2);

            List<List<String>> rawRows = List.of(
                    List.of("15/01/2026", "CARREFOUR", "-45.50"),
                    List.of("15/01/2026", "CARREFOUR", "-45.50") // Duplicate row in CSV
            );

            List<BankImportModel.BankTransactionModel> existing = List.of(
                    new BankImportModel.BankTransactionModel("e1", "2026-01-15", "CARREFOUR", "", new BigDecimal("-45.50"), "")
            );

            BankImportSummaryModel summary = BankImportCalculator.importTransactions(rawRows, colRoles, mapping, existing, List.of());

            assertThat(summary.imported()).isEqualTo(1);
            assertThat(summary.duplicates()).isEqualTo(1);
            assertThat(summary.ignoredDuplicates()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Auto Reconcile / Matching Tests")
    class AutoReconcileTests {

        @Test
        @DisplayName("autoMatchPendingOperations matches pending operations by exact refNumber")
        void testAutoMatchByRefNumber() {
            List<BankImportModel.PendingOperationModel> pendingOps = List.of(
                    new BankImportModel.PendingOperationModel("op1", "2026-01-10", "2026-01-10", "cheque", "CHQ-1234", "CHQ BLA BLA", new BigDecimal("-120.00"), "", "pending", null, null, "")
            );

            List<BankImportModel.BankTransactionModel> txs = List.of(
                    new BankImportModel.BankTransactionModel("tx1", "2026-01-15", "DEBIT CHEQUE CHQ-1234", "", new BigDecimal("-120.00"), "")
            );

            AutoMatchResultModel result = BankImportCalculator.autoMatchPendingOperations(pendingOps, txs);

            assertThat(result.matchCount()).isEqualTo(1);
            assertThat(result.updatedOperations().get(0).status()).isEqualTo("cleared");
            assertThat(result.updatedOperations().get(0).linkedTxId()).isEqualTo("tx1");
        }

        @Test
        @DisplayName("autoMatchPendingOperations matches unique amount candidate within 90 days")
        void testAutoMatchByUniqueAmount() {
            List<BankImportModel.PendingOperationModel> pendingOps = List.of(
                    new BankImportModel.PendingOperationModel("op2", "2026-01-01", "2026-01-01", "cb", "", "RESTAURANT", new BigDecimal("-88.90"), "", "pending", null, null, "")
            );

            List<BankImportModel.BankTransactionModel> txs = List.of(
                    new BankImportModel.BankTransactionModel("tx2", "2026-01-05", "CB RESTAURANT PARIS", "", new BigDecimal("-88.90"), "")
            );

            AutoMatchResultModel result = BankImportCalculator.autoMatchPendingOperations(pendingOps, txs);

            assertThat(result.matchCount()).isEqualTo(1);
            assertThat(result.updatedOperations().get(0).linkedTxId()).isEqualTo("tx2");
        }
    }
}
