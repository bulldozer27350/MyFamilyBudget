package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

public record BankImportSummaryModel(
        int imported,
        int duplicates,
        int autoCategorized,
        List<BankImportModel.BankTransactionModel> ignoredDuplicates,
        List<BankImportModel.BankTransactionModel> newTransactions,
        BankImportModel.BankColumnMappingModel updatedMapping
) {}
