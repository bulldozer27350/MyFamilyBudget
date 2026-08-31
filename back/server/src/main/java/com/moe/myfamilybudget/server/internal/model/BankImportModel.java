package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record BankImportModel(
        BankColumnMappingModel columnMapping,
        List<CategoryModel> categories,
        List<BankImportRuleModel> rules,
        List<BankTransactionModel> transactions,
        List<PendingOperationModel> pendingOperations,
        List<MatchingModel> matchings
) {
    public BankImportModel {
        if (columnMapping == null) {
            columnMapping = new BankColumnMappingModel(";", "DD/MM/YYYY", true, null, null, null, null);
        }
        if (categories == null) {
            categories = Collections.emptyList();
        }
        if (rules == null) {
            rules = Collections.emptyList();
        }
        if (transactions == null) {
            transactions = Collections.emptyList();
        }
        if (pendingOperations == null) {
            pendingOperations = Collections.emptyList();
        }
        if (matchings == null) {
            matchings = Collections.emptyList();
        }
    }

    public BankImportModel(
            List<BankTransactionModel> transactions,
            List<CategoryModel> categories,
            List<MatchingModel> matchings
    ) {
        this(
                new BankColumnMappingModel(";", "DD/MM/YYYY", true, null, null, null, null),
                categories,
                Collections.emptyList(),
                transactions,
                Collections.emptyList(),
                matchings
        );
    }

    public record BankColumnMappingModel(
            String delimiter,
            String dateFormat,
            Boolean hasHeader,
            Integer dateCol,
            Integer labelCol,
            Integer typeCol,
            Integer amountCol
    ) {
        public BankColumnMappingModel {
            if (delimiter == null || delimiter.isBlank()) delimiter = ";";
            if (dateFormat == null || dateFormat.isBlank()) dateFormat = "DD/MM/YYYY";
            if (hasHeader == null) hasHeader = true;
        }
    }

    public record CategoryModel(
            String id,
            String label,
            String kind,
            String compressible
    ) {
        public CategoryModel {
            if (id == null || id.isBlank()) id = java.util.UUID.randomUUID().toString().substring(0, 8);
            if (label == null) label = "";
            if (kind == null) kind = "Dépense";
            if (compressible == null) compressible = "Non";
        }

        public CategoryModel(String id, String label) {
            this(id, label, "Dépense", "Non");
        }
    }

    public record BankImportRuleModel(
            String id,
            String matchText,
            String categoryId
    ) {
        public BankImportRuleModel {
            if (id == null || id.isBlank()) id = java.util.UUID.randomUUID().toString().substring(0, 8);
            if (matchText == null) matchText = "";
            if (categoryId == null) categoryId = "";
        }
    }

    public record BankTransactionSplitModel(
            String id,
            String categoryId,
            BigDecimal amount,
            String label
    ) {
        public BankTransactionSplitModel {
            if (id == null || id.isBlank()) id = java.util.UUID.randomUUID().toString().substring(0, 8);
            if (categoryId == null) categoryId = "";
            if (amount == null) amount = BigDecimal.ZERO;
            if (label == null) label = "";
        }
    }

    public record BankTransactionModel(
            String id,
            String date,
            String label,
            String type,
            BigDecimal amount,
            String categoryId,
            List<BankTransactionSplitModel> splits
    ) {
        public BankTransactionModel {
            if (id == null || id.isBlank()) id = java.util.UUID.randomUUID().toString().substring(0, 8);
            if (date == null) date = "";
            if (label == null) label = "";
            if (type == null) type = "";
            if (amount == null) amount = BigDecimal.ZERO;
            if (categoryId == null) categoryId = "";
            if (splits == null) splits = Collections.emptyList();
        }

        public BankTransactionModel(String id, String date, String label, String type, BigDecimal amount, String categoryId) {
            this(id, date, label, type, amount, categoryId, Collections.emptyList());
        }

        public BankTransactionModel(String id, String date, String label, BigDecimal amount) {
            this(id, date, label, "", amount, "", Collections.emptyList());
        }

        public void validateSplits() {
            if (splits == null || splits.isEmpty()) {
                return;
            }
            BigDecimal totalSplits = splits.stream()
                    .map(BankTransactionSplitModel::amount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal diff = amount.subtract(totalSplits).abs();
            if (diff.compareTo(new BigDecimal("0.01")) > 0) {
                throw new IllegalArgumentException(
                        String.format("La somme des ventilations (%s €) ne correspond pas au montant de la transaction (%s €)",
                                totalSplits.toPlainString(), amount.toPlainString())
                );
            }
        }
    }

    public record PendingOperationModel(
            String id,
            String date,
            String expectedDate,
            String type,
            String refNumber,
            String label,
            BigDecimal amount,
            String categoryId,
            String status,
            String linkedTxId,
            String clearedDate,
            String notes,
            List<BankTransactionSplitModel> splits,
            String budgetLineId
    ) {
        public PendingOperationModel {
            if (id == null || id.isBlank()) id = java.util.UUID.randomUUID().toString().substring(0, 8);
            if (date == null) date = "";
            if (expectedDate == null) expectedDate = date;
            if (type == null) type = "cb";
            if (refNumber == null) refNumber = "";
            if (label == null) label = "";
            if (amount == null) amount = amount != null ? amount : BigDecimal.ZERO;
            if (categoryId == null) categoryId = "";
            if (status == null) status = "pending";
            if (notes == null) notes = "";
            if (splits == null) splits = Collections.emptyList();
            if (budgetLineId == null) budgetLineId = "";
        }

        public PendingOperationModel(
                String id,
                String date,
                String expectedDate,
                String type,
                String refNumber,
                String label,
                BigDecimal amount,
                String categoryId,
                String status,
                String linkedTxId,
                String clearedDate,
                String notes,
                List<BankTransactionSplitModel> splits
        ) {
            this(id, date, expectedDate, type, refNumber, label, amount, categoryId, status, linkedTxId, clearedDate, notes, splits, "");
        }

        public PendingOperationModel(
                String id,
                String date,
                String expectedDate,
                String type,
                String refNumber,
                String label,
                BigDecimal amount,
                String categoryId,
                String status,
                String linkedTxId,
                String clearedDate,
                String notes
        ) {
            this(id, date, expectedDate, type, refNumber, label, amount, categoryId, status, linkedTxId, clearedDate, notes, Collections.emptyList(), "");
        }
    }

    public record MatchingModel(
            String month,
            List<MatchingLinkModel> links
    ) {
        public MatchingModel {
            if (month == null) month = "";
            if (links == null) links = Collections.emptyList();
        }
    }

    public record MatchingLinkModel(
            String budgetLineId,
            List<String> txIds
    ) {
        public MatchingLinkModel {
            if (budgetLineId == null) budgetLineId = "";
            if (txIds == null) txIds = Collections.emptyList();
        }
    }
}
