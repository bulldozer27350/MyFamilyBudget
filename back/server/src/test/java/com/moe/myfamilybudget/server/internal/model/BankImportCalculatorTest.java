package com.moe.myfamilybudget.server.internal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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

    @Nested
    @DisplayName("Pending CB Import and Smart Duplicate Merge Tests")
    class PendingCBMergeTests {

        @Test
        @DisplayName("importPendingCB detects fuzzy duplicate candidates within +-1 day (operation date) and +-10 EUR")
        void testFuzzyDuplicateDetection() {
            // Saisie manuelle : le 15/01/2026, libelle "Resto en ville", montant 42.00 €, categorie "cat_resto"
            BankImportModel.PendingOperationModel manualOp = new BankImportModel.PendingOperationModel(
                    "man_1", "2026-01-15", "2026-01-15", "cb", "", "Resto en ville",
                    new BigDecimal("-42.00"), "cat_resto", "pending", null, null, ""
            );

            List<String> colRoles = List.of("date", "label", "amount");
            // Ligne de relevé CB : date 16/01/2026 (a 1 jour pres), libelle different "CB BRASSERIE DU NORD", montant -48.50 € (a 6.50 € pres, <= 10 €)
            List<List<String>> rawRows = List.of(
                    List.of("16/01/2026", "CB BRASSERIE DU NORD", "-48.50")
            );

            PendingImportSummaryModel summary = BankImportCalculator.importPendingCB(
                    rawRows, colRoles, "DD/MM/YYYY", false, List.of(manualOp), List.of()
            );

            assertThat(summary.imported()).isEqualTo(1);
            assertThat(summary.duplicateCandidates()).hasSize(1);
            DuplicateCandidateModel candidate = summary.duplicateCandidates().get(0);
            assertThat(candidate.incomingOp().label()).isEqualTo("CB BRASSERIE DU NORD");
            assertThat(candidate.matchingManualOps()).hasSize(1);
            assertThat(candidate.matchingManualOps().get(0).id()).isEqualTo("man_1");
            assertThat(candidate.matchingManualOps().get(0).categoryId()).isEqualTo("cat_resto");
        }

        @Test
        @DisplayName("importPendingCB does not propose candidate if delta date > 1 day or delta amount > 10 EUR")
        void testFuzzyDuplicateExceedTolerance() {
            BankImportModel.PendingOperationModel manualOp = new BankImportModel.PendingOperationModel(
                    "man_1", "2026-01-15", "2026-01-15", "cb", "", "Courses",
                    new BigDecimal("-50.00"), "cat_courses", "pending", null, null, ""
            );

            List<String> colRoles = List.of("date", "label", "amount");
            // Row 1: delta date = 2 days (17/01/2026 vs 15/01/2026) -> out of tolerance
            // Row 2: delta amount = 11 EUR (-61.00 vs -50.00) -> out of tolerance
            List<List<String>> rawRows = List.of(
                    List.of("17/01/2026", "CARREFOUR", "-50.00"),
                    List.of("15/01/2026", "AUCHAN", "-61.00")
            );

            PendingImportSummaryModel summary = BankImportCalculator.importPendingCB(
                    rawRows, colRoles, "DD/MM/YYYY", false, List.of(manualOp), List.of()
            );

            assertThat(summary.duplicateCandidates()).isEmpty();
        }

        @Test
        @DisplayName("mergePendingOperation preserves manual category and updates official bank fields with pending status")
        void testMergePendingOperation() {
            BankImportModel.PendingOperationModel manualOp = new BankImportModel.PendingOperationModel(
                    "man_1", "2026-01-15", "2026-01-15", "cb", "", "Saisie manuelle Resto",
                    new BigDecimal("-40.00"), "cat_resto_perso", "pending", null, null, "Note perso"
            );

            BankImportModel.PendingOperationModel bankOp = new BankImportModel.PendingOperationModel(
                    "bank_1", "2026-01-16", "2026-01-31", "cb", "", "FACTURE CARTE 160126 RESTO LE PHARE",
                    new BigDecimal("-45.50"), "", "pending", null, null, "Achat le 16/01/2026"
            );

            List<BankImportModel.PendingOperationModel> existingList = List.of(manualOp, bankOp);

            List<BankImportModel.PendingOperationModel> mergedList = BankImportCalculator.mergePendingOperation(
                    "man_1", bankOp, existingList
            );

            assertThat(mergedList).hasSize(1); // bankOp removed as redundant, manualOp updated
            BankImportModel.PendingOperationModel merged = mergedList.get(0);
            assertThat(merged.id()).isEqualTo("man_1"); // Conserve l'ID
            assertThat(merged.categoryId()).isEqualTo("cat_resto_perso"); // Conserve la catégorie saisie manuellement
            assertThat(merged.label()).isEqualTo("FACTURE CARTE 160126 RESTO LE PHARE"); // Libelle banque
            assertThat(merged.amount()).isEqualByComparingTo(new BigDecimal("-45.50")); // Montant exact banque
            assertThat(merged.date()).isEqualTo("2026-01-16"); // Date banque
            assertThat(merged.status()).isEqualTo("pending"); // Reste en statut pending
            assertThat(merged.linkedTxId()).isNull();
            assertThat(merged.clearedDate()).isNull();
        }
    }

    @Nested
    @DisplayName("BankTransactionSplit Tests")
    class BankTransactionSplitTests {

        @Test
        @DisplayName("BankTransactionSplitModel applies safe defaults for null values")
        void testSplitModelDefaults() {
            BankImportModel.BankTransactionSplitModel split = new BankImportModel.BankTransactionSplitModel(null, null, null, null);

            assertThat(split.id()).isNotBlank();
            assertThat(split.categoryId()).isEmpty();
            assertThat(split.amount()).isEqualTo(BigDecimal.ZERO);
            assertThat(split.label()).isEmpty();
        }

        @Test
        @DisplayName("BankTransactionModel with empty splits passes validation")
        void testTransactionWithoutSplitsIsValid() {
            BankImportModel.BankTransactionModel tx = new BankImportModel.BankTransactionModel(
                    "tx1", "2026-01-15", "LECLERC", "", new BigDecimal("-100.00"), "cat_courses"
            );

            assertThat(tx.splits()).isEmpty();
            tx.validateSplits(); // Should not throw
        }

        @Test
        @DisplayName("BankTransactionModel with valid splits sum passes validation")
        void testTransactionWithValidSplits() {
            List<BankImportModel.BankTransactionSplitModel> splits = List.of(
                    new BankImportModel.BankTransactionSplitModel("s1", "cat_food", new BigDecimal("-60.00"), "Alimentation"),
                    new BankImportModel.BankTransactionSplitModel("s2", "cat_clothes", new BigDecimal("-40.00"), "Vêtements")
            );

            BankImportModel.BankTransactionModel tx = new BankImportModel.BankTransactionModel(
                    "tx1", "2026-01-15", "LECLERC", "", new BigDecimal("-100.00"), "", splits
            );

            assertThat(tx.splits()).hasSize(2);
            tx.validateSplits(); // Should not throw
        }

        @Test
        @DisplayName("BankTransactionModel with split sum matching within 0.01 tolerance passes validation")
        void testTransactionWithRoundingTolerance() {
            List<BankImportModel.BankTransactionSplitModel> splits = List.of(
                    new BankImportModel.BankTransactionSplitModel("s1", "cat_1", new BigDecimal("-33.33"), "Part 1"),
                    new BankImportModel.BankTransactionSplitModel("s2", "cat_2", new BigDecimal("-33.33"), "Part 2"),
                    new BankImportModel.BankTransactionSplitModel("s3", "cat_3", new BigDecimal("-33.33"), "Part 3")
            ); // Sum is -99.99 for tx of -100.00 (diff = 0.01)

            BankImportModel.BankTransactionModel tx = new BankImportModel.BankTransactionModel(
                    "tx1", "2026-01-15", "LECLERC", "", new BigDecimal("-100.00"), "", splits
            );

            tx.validateSplits(); // Should not throw because diff <= 0.01
        }

        @Test
        @DisplayName("BankTransactionModel with invalid splits sum throws IllegalArgumentException")
        void testTransactionWithInvalidSplitsThrows() {
            List<BankImportModel.BankTransactionSplitModel> splits = List.of(
                    new BankImportModel.BankTransactionSplitModel("s1", "cat_food", new BigDecimal("-50.00"), "Alimentation"),
                    new BankImportModel.BankTransactionSplitModel("s2", "cat_clothes", new BigDecimal("-30.00"), "Vêtements")
            ); // Sum is -80.00 for tx of -100.00

            BankImportModel.BankTransactionModel tx = new BankImportModel.BankTransactionModel(
                    "tx1", "2026-01-15", "LECLERC", "", new BigDecimal("-100.00"), "", splits
            );

            assertThatThrownBy(tx::validateSplits)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("La somme des ventilations (-80.00 €) ne correspond pas au montant de la transaction (-100.00 €)");
        }
    }
}
