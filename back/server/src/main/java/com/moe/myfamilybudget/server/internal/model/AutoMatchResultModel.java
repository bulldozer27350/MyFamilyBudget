package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

public record AutoMatchResultModel(
        int matchCount,
        List<BankImportModel.PendingOperationModel> updatedOperations
) {}
