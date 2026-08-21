package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

public record PendingImportSummaryModel(
        int imported,
        int duplicates,
        int autoCategorized,
        List<BankImportModel.PendingOperationModel> ignoredDuplicates,
        String firstOpDate,
        List<BankImportModel.PendingOperationModel> newOperations
) {}
