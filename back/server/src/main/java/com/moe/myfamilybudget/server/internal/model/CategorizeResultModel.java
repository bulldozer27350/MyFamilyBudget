package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

public record CategorizeResultModel(
        List<BankImportModel.BankTransactionModel> updatedTransactions,
        List<BankImportModel.BankImportRuleModel> updatedRules
) {}
