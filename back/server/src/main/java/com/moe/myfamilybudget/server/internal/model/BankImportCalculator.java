package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure domain calculator for Bank Import and Pending Operations logic.
 * Independent of Spring, OpenAPI DTOs, and external frameworks.
 */
public final class BankImportCalculator {

    private BankImportCalculator() {
        // Utility class
    }

    /**
     * Parses raw CSV text into rows of cell strings, handling delimiter, quotes, and CRLF.
     */
    public static List<List<String>> parseCSVText(String text, String delimiter) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String sep = (delimiter != null && !delimiter.isEmpty()) ? delimiter : ";";
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (text.startsWith(sep, i)) {
                row.add(field.toString());
                field.setLength(0);
                i += sep.length() - 1;
            } else if (c == '\n') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
            } else if (c == '\r') {
                // Ignore carriage return
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }

        return rows.stream()
                .filter(r -> r.stream().anyMatch(cell -> cell != null && !cell.trim().isEmpty()))
                .collect(Collectors.toList());
    }

    /**
     * Parses a date string according to format (e.g., DD/MM/YYYY) into ISO YYYY-MM-DD.
     */
    public static String parseDateWithFormat(String str, String format) {
        if (str == null || str.isBlank()) return null;
        String cleaned = str.trim();
        String[] parts = cleaned.split("[/\\-\\.]");
        if (parts.length != 3) return null;

        // If the date string starts with a 4-digit year (ISO format: YYYY-MM-DD or YYYY/MM/DD)
        if (parts[0].trim().length() == 4) {
            try {
                int y = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                int d = Integer.parseInt(parts[2].trim());
                if (d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 1900 && y <= 2100) {
                    return String.format("%04d-%02d-%02d", y, m, d);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        String fmt = (format != null && !format.isBlank()) ? format : "DD/MM/YYYY";
        String[] order = fmt.split("[/\\-\\.]");
        if (order.length != 3) return null;

        Integer d = null, m = null, y = null;
        for (int idx = 0; idx < 3; idx++) {
            try {
                int v = Integer.parseInt(parts[idx].trim());
                char tokenChar = Character.toUpperCase(order[idx].trim().charAt(0));
                if (tokenChar == 'D') d = v;
                else if (tokenChar == 'M') m = v;
                else if (tokenChar == 'Y') y = v < 100 ? 2000 + v : v;
            } catch (Exception e) {
                return null;
            }
        }
        if (d == null || m == null || y == null) return null;
        if (d < 1 || d > 31 || m < 1 || m > 12 || y < 1900 || y > 2100) return null;

        return String.format("%04d-%02d-%02d", y, m, d);
    }

    /**
     * Parses an amount string into BigDecimal with RoundingMode.HALF_UP.
     */
    public static BigDecimal parseAmountText(String str) {
        if (str == null || str.isBlank()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String s = str.trim().replaceAll("[€\\s]", "");
        if (s.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (s.contains(",") && s.contains(".")) {
            if (s.lastIndexOf(",") > s.lastIndexOf(".")) {
                s = s.replace(".", "").replace(",", ".");
            } else {
                s = s.replace(",", "");
            }
        } else if (s.contains(",")) {
            s = s.replace(",", ".");
        }

        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Generates a deduplication key for transactions or pending operations.
     */
    public static String transactionDedupeKey(String date, String label, BigDecimal amount) {
        String d = date != null ? date : "";
        String l = label != null ? label.trim().replaceAll("\\s+", " ").toLowerCase() : "";
        BigDecimal amt = amount != null ? amount : BigDecimal.ZERO;
        long roundedCents = amt.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValue();
        return d + "|" + l + "|" + roundedCents;
    }

    /**
     * Derives a rule keyword from a label.
     */
    public static String ruleKeyFromLabel(String label) {
        if (label == null) return "";
        return label.replaceAll("[0-9]", "").replaceAll("\\s+", " ").trim().toUpperCase();
    }

    /**
     * Applies matching categorization rules to transactions.
     */
    public static List<BankImportModel.BankTransactionModel> applyRulesToTransactions(
            List<BankImportModel.BankTransactionModel> transactions,
            List<BankImportModel.BankImportRuleModel> rules
    ) {
        if (transactions == null) return Collections.emptyList();
        List<BankImportModel.BankImportRuleModel> activeRules = rules != null ? rules.stream()
                .filter(r -> r.matchText() != null && !r.matchText().trim().isEmpty())
                .toList() : Collections.emptyList();

        List<BankImportModel.BankTransactionModel> result = new ArrayList<>();
        for (BankImportModel.BankTransactionModel t : transactions) {
            if (t.categoryId() != null && !t.categoryId().isBlank()) {
                result.add(t);
                continue;
            }
            String labelUpper = (t.label() != null ? t.label() : "").toUpperCase();
            Optional<BankImportModel.BankImportRuleModel> match = activeRules.stream()
                    .filter(r -> labelUpper.contains(r.matchText().trim().toUpperCase()))
                    .findFirst();

            if (match.isPresent()) {
                result.add(new BankImportModel.BankTransactionModel(
                        t.id(), t.date(), t.label(), t.type(), t.amount(), match.get().categoryId()
                ));
            } else {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Applies matching categorization rules to pending operations.
     */
    public static List<BankImportModel.PendingOperationModel> applyRulesToPendingOperations(
            List<BankImportModel.PendingOperationModel> operations,
            List<BankImportModel.BankImportRuleModel> rules
    ) {
        if (operations == null) return Collections.emptyList();
        List<BankImportModel.BankImportRuleModel> activeRules = rules != null ? rules.stream()
                .filter(r -> r.matchText() != null && !r.matchText().trim().isEmpty())
                .toList() : Collections.emptyList();

        List<BankImportModel.PendingOperationModel> result = new ArrayList<>();
        for (BankImportModel.PendingOperationModel op : operations) {
            if (op.categoryId() != null && !op.categoryId().isBlank()) {
                result.add(op);
                continue;
            }
            String labelUpper = (op.label() != null ? op.label() : "").toUpperCase();
            Optional<BankImportModel.BankImportRuleModel> match = activeRules.stream()
                    .filter(r -> labelUpper.contains(r.matchText().trim().toUpperCase()))
                    .findFirst();

            if (match.isPresent()) {
                result.add(new BankImportModel.PendingOperationModel(
                        op.id(), op.date(), op.expectedDate(), op.type(), op.refNumber(),
                        op.label(), op.amount(), match.get().categoryId(), op.status(),
                        op.linkedTxId(), op.clearedDate(), op.notes(), op.splits()
                ));
            } else {
                result.add(op);
            }
        }
        return result;
    }

    /**
     * Imports bank transactions from CSV rows, handles deduplication, and applies categorization rules.
     */
    public static BankImportSummaryModel importTransactions(
            List<List<String>> rawRows,
            List<String> colRoles,
            BankImportModel.BankColumnMappingModel mapping,
            List<BankImportModel.BankTransactionModel> existingTxs,
            List<BankImportModel.BankImportRuleModel> rules
    ) {
        if (colRoles == null) {
            throw new IllegalArgumentException("Les rôles de colonnes ne peuvent pas être null.");
        }
        int dateCol = colRoles.indexOf("date");
        int labelCol = colRoles.indexOf("label");
        int typeCol = colRoles.indexOf("type");
        int amountCol = colRoles.indexOf("amount");

        if (dateCol == -1 || labelCol == -1 || amountCol == -1) {
            throw new IllegalArgumentException("Il faut au minimum assigner les rôles Date, Libellé et Montant à une colonne.");
        }

        BankImportModel.BankColumnMappingModel updatedMapping = new BankImportModel.BankColumnMappingModel(
                mapping != null ? mapping.delimiter() : ";",
                mapping != null ? mapping.dateFormat() : "DD/MM/YYYY",
                mapping != null ? mapping.hasHeader() : true,
                dateCol, labelCol, typeCol >= 0 ? typeCol : null, amountCol
        );

        Map<String, Integer> existingCounts = new HashMap<>();
        if (existingTxs != null) {
            for (BankImportModel.BankTransactionModel t : existingTxs) {
                String k = transactionDedupeKey(t.date(), t.label(), t.amount());
                existingCounts.put(k, existingCounts.getOrDefault(k, 0) + 1);
            }
        }

        Map<String, Integer> fileKeyCounts = new HashMap<>();
        List<BankImportModel.BankTransactionModel> imported = new ArrayList<>();
        List<BankImportModel.BankTransactionModel> ignoredDuplicates = new ArrayList<>();

        if (rawRows != null) {
            String dateFormat = mapping != null ? mapping.dateFormat() : "DD/MM/YYYY";
            for (List<String> row : rawRows) {
                if (dateCol >= row.size() || labelCol >= row.size() || amountCol >= row.size()) continue;

                String dateISO = parseDateWithFormat(row.get(dateCol), dateFormat);
                if (dateISO == null) continue;

                String label = row.get(labelCol) != null ? row.get(labelCol).trim() : "";
                String type = (typeCol >= 0 && typeCol < row.size()) ? row.get(typeCol).trim() : "";
                BigDecimal amount = parseAmountText(row.get(amountCol));

                BankImportModel.BankTransactionModel t = new BankImportModel.BankTransactionModel(
                        java.util.UUID.randomUUID().toString().substring(0, 8),
                        dateISO, label, type, amount, ""
                );

                String key = transactionDedupeKey(t.date(), t.label(), t.amount());
                fileKeyCounts.put(key, fileKeyCounts.getOrDefault(key, 0) + 1);
                int stored = existingCounts.getOrDefault(key, 0);

                if (fileKeyCounts.get(key) <= stored) {
                    ignoredDuplicates.add(t);
                } else {
                    existingCounts.put(key, stored + 1);
                    imported.add(t);
                }
            }
        }

        List<BankImportModel.BankTransactionModel> categorized = applyRulesToTransactions(imported, rules);
        int autoCategorized = (int) categorized.stream()
                .filter(t -> t.categoryId() != null && !t.categoryId().isBlank())
                .count();

        return new BankImportSummaryModel(
                categorized.size(),
                ignoredDuplicates.size(),
                autoCategorized,
                ignoredDuplicates,
                categorized,
                updatedMapping
        );
    }

    /**
     * Compares the categorization (single categoryId, or the set of category ids used across splits)
     * of two entities (a pending operation and a bank transaction, in any order). Two entities with no
     * categorization at all (no categoryId, no splits) are considered equal (nothing to reconcile). An
     * entity with splits is only considered equal to another entity that ALSO has splits covering the
     * exact same set of category ids - split amounts are intentionally ignored since the pending
     * operation's amount is only an estimate of the real bank amount. A splits-based categorization is
     * NEVER considered equal to a single-category (or uncategorized) representation, even if one side
     * is blank: any difference, including "one side has a categorization and the other doesn't", must
     * be arbitrated by the operator rather than guessed by the application.
     */
    public static boolean categorizationsMatch(
            String categoryIdA, List<BankImportModel.BankTransactionSplitModel> splitsA,
            String categoryIdB, List<BankImportModel.BankTransactionSplitModel> splitsB
    ) {
        boolean aHasSplits = splitsA != null && !splitsA.isEmpty();
        boolean bHasSplits = splitsB != null && !splitsB.isEmpty();
        if (aHasSplits != bHasSplits) {
            return false;
        }
        if (aHasSplits) {
            Set<String> idsA = splitsA.stream()
                    .map(BankImportModel.BankTransactionSplitModel::categoryId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<String> idsB = splitsB.stream()
                    .map(BankImportModel.BankTransactionSplitModel::categoryId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toCollection(TreeSet::new));
            return idsA.equals(idsB);
        }
        String a = categoryIdA != null ? categoryIdA : "";
        String b = categoryIdB != null ? categoryIdB : "";
        return a.equals(b);
    }

    /**
     * Rescales a set of splits so that their sum matches exactly {@code targetAmount} (the real bank
     * transaction amount), preserving the relative weight of each split. The rounding remainder is
     * absorbed by the last split. IDs are preserved when present, generated otherwise.
     */
    public static List<BankImportModel.BankTransactionSplitModel> rescaleSplitsToAmount(
            List<BankImportModel.BankTransactionSplitModel> splits,
            BigDecimal targetAmount
    ) {
        if (splits == null || splits.isEmpty()) {
            return Collections.emptyList();
        }
        BigDecimal target = targetAmount != null ? targetAmount : BigDecimal.ZERO;
        BigDecimal sum = splits.stream()
                .map(sp -> sp.amount() != null ? sp.amount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BankImportModel.BankTransactionSplitModel> rescaled = new ArrayList<>();
        if (sum.abs().compareTo(new BigDecimal("0.001")) > 0
                && target.subtract(sum).abs().compareTo(new BigDecimal("0.001")) > 0) {
            BigDecimal ratio = target.divide(sum, 10, RoundingMode.HALF_UP);
            for (BankImportModel.BankTransactionSplitModel sp : splits) {
                BigDecimal amt = (sp.amount() != null ? sp.amount() : BigDecimal.ZERO)
                        .multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                rescaled.add(new BankImportModel.BankTransactionSplitModel(null, sp.categoryId(), amt, sp.label()));
            }
            BigDecimal newSum = rescaled.stream().map(BankImportModel.BankTransactionSplitModel::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal diff = target.subtract(newSum).setScale(2, RoundingMode.HALF_UP);
            if (diff.compareTo(BigDecimal.ZERO) != 0 && !rescaled.isEmpty()) {
                int lastIdx = rescaled.size() - 1;
                BankImportModel.BankTransactionSplitModel last = rescaled.get(lastIdx);
                rescaled.set(lastIdx, new BankImportModel.BankTransactionSplitModel(
                        last.id(), last.categoryId(), last.amount().add(diff), last.label()));
            }
        } else {
            for (BankImportModel.BankTransactionSplitModel sp : splits) {
                rescaled.add(new BankImportModel.BankTransactionSplitModel(
                        null, sp.categoryId(), sp.amount() != null ? sp.amount() : BigDecimal.ZERO, sp.label()));
            }
        }
        return rescaled;
    }

    /**
     * Auto-reconciles pending operations against bank transactions.
     * <p>
     * A candidate pair (pending operation / bank transaction) found by reference number or by unique
     * amount is only reconciled automatically when both sides already carry the SAME categorization
     * (see {@link #categorizationsMatch}). If they differ - including when only one side has been
     * categorized - the application does NOT decide on the operator's behalf: the pending operation is
     * left "pending" (unlinked) so it can be resolved manually, where the operator is asked to arbitrate.
     */
    public static AutoMatchResultModel autoMatchPendingOperations(
            List<BankImportModel.PendingOperationModel> pendingOps,
            List<BankImportModel.BankTransactionModel> transactions
    ) {
        if (pendingOps == null) {
            return new AutoMatchResultModel(0, Collections.emptyList(), Collections.emptyList(), 0);
        }
        List<BankImportModel.BankTransactionModel> txs = transactions != null ? transactions : Collections.emptyList();

        int matchCount = 0;
        int needsReviewCount = 0;
        Set<String> currentlyLinked = new HashSet<>();
        for (BankImportModel.PendingOperationModel op : pendingOps) {
            if (op.linkedTxId() != null && !op.linkedTxId().isBlank()) {
                currentlyLinked.add(op.linkedTxId());
            }
        }

        // Transactions whose categorization gets aligned on a matched pending operation (id -> updated tx).
        Map<String, BankImportModel.BankTransactionModel> txOverrides = new HashMap<>();

        List<BankImportModel.PendingOperationModel> updatedOps = new ArrayList<>();
        for (BankImportModel.PendingOperationModel op : pendingOps) {
            if ("cleared".equalsIgnoreCase(op.status()) && op.linkedTxId() != null && !op.linkedTxId().isBlank()) {
                updatedOps.add(op);
                continue;
            }

            BigDecimal targetAmt = op.amount() != null ? op.amount() : BigDecimal.ZERO;
            boolean handled = false;

            // 1. Search by exact refNumber (>= 3 chars)
            if (op.refNumber() != null && op.refNumber().trim().length() >= 3) {
                String refLower = op.refNumber().trim().toLowerCase();
                Optional<BankImportModel.BankTransactionModel> foundByRef = txs.stream()
                        .filter(t -> !currentlyLinked.contains(t.id()))
                        .filter(t -> (t.label() != null ? t.label().toLowerCase() : "").contains(refLower))
                        .filter(t -> t.amount().abs().subtract(targetAmt.abs()).abs().compareTo(new BigDecimal("0.01")) < 0)
                        .findFirst();

                if (foundByRef.isPresent()) {
                    BankImportModel.BankTransactionModel found = foundByRef.get();
                    handled = true;
                    if (categorizationsMatch(op.categoryId(), op.splits(), found.categoryId(), found.splits())) {
                        currentlyLinked.add(found.id());
                        matchCount++;
                        if (op.splits() != null && !op.splits().isEmpty()) {
                            txOverrides.put(found.id(), withCategorization(found, "", rescaleSplitsToAmount(op.splits(), found.amount())));
                        }
                        updatedOps.add(new BankImportModel.PendingOperationModel(
                                op.id(), op.date(), op.expectedDate(), op.type(), op.refNumber(),
                                op.label(), op.amount(), op.categoryId(), "cleared", found.id(),
                                found.date(), op.notes(), op.splits(), op.budgetLineId()
                        ));
                    } else {
                        needsReviewCount++;
                        updatedOps.add(op);
                    }
                }
            }

            if (handled) continue;

            // 2. Search by unique exact amount within 90 days window
            LocalDate opDate = parseLocalDate(op.date());
            List<BankImportModel.BankTransactionModel> candidates = txs.stream()
                    .filter(t -> !currentlyLinked.contains(t.id()))
                    .filter(t -> t.amount().abs().subtract(targetAmt.abs()).abs().compareTo(new BigDecimal("0.01")) < 0)
                    .filter(t -> {
                        if (opDate == null) return true;
                        LocalDate tDate = parseLocalDate(t.date());
                        if (tDate == null) return true;
                        long diffDays = Math.abs(ChronoUnit.DAYS.between(opDate, tDate));
                        return diffDays <= 90;
                    })
                    .toList();

            if (candidates.size() == 1) {
                BankImportModel.BankTransactionModel found = candidates.get(0);
                if (categorizationsMatch(op.categoryId(), op.splits(), found.categoryId(), found.splits())) {
                    currentlyLinked.add(found.id());
                    matchCount++;
                    if (op.splits() != null && !op.splits().isEmpty()) {
                        txOverrides.put(found.id(), withCategorization(found, "", rescaleSplitsToAmount(op.splits(), found.amount())));
                    }
                    updatedOps.add(new BankImportModel.PendingOperationModel(
                            op.id(), op.date(), op.expectedDate(), op.type(), op.refNumber(),
                            op.label(), op.amount(), op.categoryId(), "cleared", found.id(),
                            found.date(), op.notes(), op.splits(), op.budgetLineId()
                    ));
                } else {
                    needsReviewCount++;
                    updatedOps.add(op);
                }
            } else {
                updatedOps.add(op);
            }
        }

        List<BankImportModel.BankTransactionModel> updatedTransactions = txOverrides.isEmpty()
                ? txs
                : txs.stream().map(t -> txOverrides.getOrDefault(t.id(), t)).collect(Collectors.toList());

        return new AutoMatchResultModel(matchCount, updatedOps, updatedTransactions, needsReviewCount);
    }

    private static BankImportModel.BankTransactionModel withCategorization(
            BankImportModel.BankTransactionModel tx,
            String categoryId,
            List<BankImportModel.BankTransactionSplitModel> splits
    ) {
        return new BankImportModel.BankTransactionModel(
                tx.id(), tx.date(), tx.label(), tx.type(), tx.amount(), categoryId, splits
        );
    }

    /**
     * Parses purchase date from deferred CB transaction label.
     */
    public static String parsePurchaseDateFromLabel(String label) {
        if (label == null || label.isBlank()) return null;
        Pattern p = Pattern.compile("DU\\s+(\\d{2})(\\d{2})(\\d{2,4})", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(label);
        if (m.find()) {
            try {
                int d = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int y = Integer.parseInt(m.group(3));
                if (y < 100) y = 2000 + y;
                if (d >= 1 && d <= 31 && mo >= 1 && mo <= 12) {
                    return String.format("%04d-%02d-%02d", y, mo, d);
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Imports deferred CB pending operations from parsed CSV rows.
     * Identifies exact duplicates and fuzzy duplicate candidates for manual entries (within +-1 day of operation date and +-10 EUR).
     */
    public static PendingImportSummaryModel importPendingCB(
            List<List<String>> rawRows,
            List<String> colRoles,
            String dateFormat,
            boolean usePurchaseDate,
            List<BankImportModel.PendingOperationModel> existingOps,
            List<BankImportModel.BankImportRuleModel> rules
    ) {
        if (colRoles == null) {
            throw new IllegalArgumentException("Les rôles de colonnes ne peuvent pas être null.");
        }
        int dateCol = colRoles.indexOf("date");
        int labelCol = colRoles.indexOf("label");
        int amountCol = colRoles.indexOf("amount");

        if (dateCol == -1 || labelCol == -1 || amountCol == -1) {
            throw new IllegalArgumentException("Il faut au minimum assigner les rôles Date, Libellé et Montant à une colonne.");
        }

        Map<String, Integer> existingCounts = new HashMap<>();
        List<BankImportModel.PendingOperationModel> manualCandidates = new ArrayList<>();
        if (existingOps != null) {
            for (BankImportModel.PendingOperationModel op : existingOps) {
                String k = transactionDedupeKey(op.date(), op.label(), op.amount());
                existingCounts.put(k, existingCounts.getOrDefault(k, 0) + 1);
                if ("pending".equalsIgnoreCase(op.status()) && (op.linkedTxId() == null || op.linkedTxId().isBlank())) {
                    manualCandidates.add(op);
                }
            }
        }

        Map<String, Integer> fileKeyCounts = new HashMap<>();
        List<BankImportModel.PendingOperationModel> imported = new ArrayList<>();
        List<BankImportModel.PendingOperationModel> ignoredDuplicates = new ArrayList<>();
        List<DuplicateCandidateModel> duplicateCandidates = new ArrayList<>();

        String fmt = (dateFormat != null && !dateFormat.isBlank()) ? dateFormat : "DD-MM-YYYY";

        if (rawRows != null) {
            for (List<String> row : rawRows) {
                if (dateCol >= row.size() || labelCol >= row.size() || amountCol >= row.size()) continue;

                String rawDate = row.get(dateCol);
                String dateISO = parseDateWithFormat(rawDate, fmt);
                if (dateISO == null) dateISO = parseDateWithFormat(rawDate, "DD/MM/YYYY");
                if (dateISO == null) dateISO = parseDateWithFormat(rawDate, "YYYY-MM-DD");
                if (dateISO == null) continue;

                String rawLabel = row.get(labelCol) != null ? row.get(labelCol).trim() : "";
                if (rawLabel.isEmpty()) continue;

                BigDecimal amt = parseAmountText(row.get(amountCol));
                String purchaseDate = parsePurchaseDateFromLabel(rawLabel);

                String finalOpDate = dateISO;
                String expectedDebitDate = dateISO;
                if (usePurchaseDate && purchaseDate != null) {
                    finalOpDate = purchaseDate;
                    expectedDebitDate = dateISO;
                }

                String notes = "";
                if (purchaseDate != null && !usePurchaseDate) {
                    String[] pParts = purchaseDate.split("-");
                    if (pParts.length == 3) {
                        notes = "Achat le " + pParts[2] + "/" + pParts[1] + "/" + pParts[0];
                    }
                }

                BankImportModel.PendingOperationModel op = new BankImportModel.PendingOperationModel(
                        java.util.UUID.randomUUID().toString().substring(0, 8),
                        finalOpDate, expectedDebitDate, "cb", "", rawLabel,
                        amt, "", "pending", null, null, notes
                );

                String key = transactionDedupeKey(op.date(), op.label(), op.amount());
                fileKeyCounts.put(key, fileKeyCounts.getOrDefault(key, 0) + 1);
                int stored = existingCounts.getOrDefault(key, 0);

                if (fileKeyCounts.get(key) <= stored) {
                    ignoredDuplicates.add(op);
                    continue;
                }

                existingCounts.put(key, stored + 1);

                // Fuzzy duplicate check against manual pending operations (within +-1 day on operation date and +-10 EUR)
                LocalDate incomingOpDate = parseLocalDate(op.date());
                BigDecimal incomingAbsAmt = op.amount() != null ? op.amount().abs() : BigDecimal.ZERO;
                List<BankImportModel.PendingOperationModel> matchingManual = manualCandidates.stream()
                        .filter(m -> {
                            LocalDate mDate = parseLocalDate(m.date());
                            if (incomingOpDate != null && mDate != null) {
                                long diffDays = Math.abs(ChronoUnit.DAYS.between(incomingOpDate, mDate));
                                if (diffDays > 1) return false;
                            }
                            BigDecimal mAbsAmt = m.amount() != null ? m.amount().abs() : BigDecimal.ZERO;
                            BigDecimal diffAmt = incomingAbsAmt.subtract(mAbsAmt).abs();
                            return diffAmt.compareTo(new BigDecimal("10.00")) <= 0;
                        })
                        .toList();

                if (!matchingManual.isEmpty()) {
                    duplicateCandidates.add(new DuplicateCandidateModel(op, matchingManual));
                }

                imported.add(op);
            }
        }

        List<BankImportModel.PendingOperationModel> categorized = applyRulesToPendingOperations(imported, rules);
        int autoCategorized = (int) categorized.stream()
                .filter(op -> op.categoryId() != null && !op.categoryId().isBlank())
                .count();

        String firstOpDate = !categorized.isEmpty() ? categorized.get(0).date() : null;

        return new PendingImportSummaryModel(
                categorized.size(),
                ignoredDuplicates.size(),
                autoCategorized,
                ignoredDuplicates,
                firstOpDate,
                categorized,
                duplicateCandidates
        );
    }

    /**
     * Merges a manual pending operation with an incoming bank operation.
     * Preserves the manual operation's ID and categoryId (if present).
     * Overwrites label, date, expectedDate, amount with the bank's values and keeps status as 'pending'.
     */
    public static List<BankImportModel.PendingOperationModel> mergePendingOperation(
            String manualOpId,
            BankImportModel.PendingOperationModel bankOp,
            List<BankImportModel.PendingOperationModel> existingOps
    ) {
        if (existingOps == null) return Collections.emptyList();
        List<BankImportModel.PendingOperationModel> updated = new ArrayList<>();
        boolean found = false;

        for (BankImportModel.PendingOperationModel op : existingOps) {
            if (op.id().equals(manualOpId)) {
                found = true;
                String categoryToKeep = (op.categoryId() != null && !op.categoryId().isBlank())
                        ? op.categoryId()
                        : (bankOp != null ? bankOp.categoryId() : "");

                String mergedDate = bankOp != null && bankOp.date() != null && !bankOp.date().isBlank()
                        ? bankOp.date() : op.date();
                String mergedExpectedDate = bankOp != null && bankOp.expectedDate() != null && !bankOp.expectedDate().isBlank()
                        ? bankOp.expectedDate() : op.expectedDate();
                String mergedLabel = bankOp != null && bankOp.label() != null && !bankOp.label().isBlank()
                        ? bankOp.label() : op.label();
                BigDecimal mergedAmount = bankOp != null && bankOp.amount() != null
                        ? bankOp.amount() : op.amount();
                String mergedNotes = bankOp != null && bankOp.notes() != null && !bankOp.notes().isBlank()
                        ? bankOp.notes() : op.notes();

                List<BankImportModel.BankTransactionSplitModel> splitsToKeep = (op.splits() != null && !op.splits().isEmpty())
                        ? op.splits()
                        : (bankOp != null && bankOp.splits() != null ? bankOp.splits() : Collections.emptyList());

                updated.add(new BankImportModel.PendingOperationModel(
                        op.id(),
                        mergedDate,
                        mergedExpectedDate,
                        op.type() != null ? op.type() : "cb",
                        op.refNumber() != null ? op.refNumber() : "",
                        mergedLabel,
                        mergedAmount,
                        categoryToKeep,
                        "pending",
                        null,
                        null,
                        mergedNotes,
                        splitsToKeep
                ));
            } else if (bankOp != null && op.id().equals(bankOp.id())) {
                // If the bank operation was already added to pending operations, remove/skip it to avoid duplicate
                continue;
            } else {
                updated.add(op);
            }
        }

        return updated;
    }

    /**
     * Categorizes a transaction and optionally creates/updates a matching rule.
     */
    public static CategorizeResultModel categorizeTransaction(
            String txId,
            String categoryId,
            String ruleKeyword,
            List<BankImportModel.BankTransactionModel> transactions,
            List<BankImportModel.BankImportRuleModel> rules
    ) {
        if (transactions == null) {
            return new CategorizeResultModel(Collections.emptyList(), rules != null ? rules : Collections.emptyList());
        }

        List<BankImportModel.BankImportRuleModel> currentRules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
        if (ruleKeyword != null && !ruleKeyword.trim().isBlank()) {
            String key = ruleKeyword.trim().toUpperCase();
            boolean found = false;
            for (int i = 0; i < currentRules.size(); i++) {
                BankImportModel.BankImportRuleModel r = currentRules.get(i);
                if (r.matchText() != null && r.matchText().trim().toUpperCase().equals(key)) {
                    currentRules.set(i, new BankImportModel.BankImportRuleModel(r.id(), ruleKeyword.trim(), categoryId));
                    found = true;
                    break;
                }
            }
            if (!found) {
                currentRules.add(new BankImportModel.BankImportRuleModel(
                        java.util.UUID.randomUUID().toString().substring(0, 8),
                        ruleKeyword.trim(),
                        categoryId
                ));
            }
        }

        List<BankImportModel.BankTransactionModel> updated = transactions.stream()
                .map(t -> t.id().equals(txId)
                        ? new BankImportModel.BankTransactionModel(t.id(), t.date(), t.label(), t.type(), t.amount(), categoryId)
                        : t)
                .collect(Collectors.toList());

        List<BankImportModel.BankTransactionModel> finalTxs = applyRulesToTransactions(updated, currentRules);
        return new CategorizeResultModel(finalTxs, currentRules);
    }

    private static LocalDate parseLocalDate(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDate.parse(str.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
