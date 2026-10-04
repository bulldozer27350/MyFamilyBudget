package com.moe.myfamilybudget.domain.treasury.model;

import java.math.BigDecimal;

public record OneOffExpenseModel(
    String id,
    String label,
    String date,
    BigDecimal amount,
    String notes
) {
    public BigDecimal getEffectiveAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
