package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record BankImportModel(
    List<BankTransactionModel> transactions,
    List<CategoryModel> categories,
    List<MatchingModel> matchings
) {
    public record BankTransactionModel(
        String id,
        String date,
        String label,
        BigDecimal amount
    ) {}

    public record CategoryModel(
        String id,
        String label
    ) {}

    public record MatchingModel(
        String month,
        List<MatchingLinkModel> links
    ) {}

    public record MatchingLinkModel(
        String budgetLineId,
        List<String> txIds
    ) {}
}

