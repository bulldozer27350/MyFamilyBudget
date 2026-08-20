package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;
import java.util.List;

public record BankImportDto(
    List<BankTransactionDto> transactions
) {
    public record BankTransactionDto(
        String id,
        String date,
        String label,
        BigDecimal amount
    ) {}
}
