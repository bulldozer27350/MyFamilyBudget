package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record BankImportModel(
    List<BankTransactionModel> transactions
) {
    public record BankTransactionModel(
        String id,
        String date,
        String label,
        BigDecimal amount
    ) {}
}
