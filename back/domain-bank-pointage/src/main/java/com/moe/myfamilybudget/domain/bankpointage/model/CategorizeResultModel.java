package com.moe.myfamilybudget.domain.bankpointage.model;

import java.util.List;

public record CategorizeResultModel(
        List<BankImportModel.BankTransactionModel> updatedTransactions,
        List<BankImportModel.BankImportRuleModel> updatedRules
) {}
