package com.moe.myfamilybudget.domain.budget;

import java.math.BigDecimal;

public record TransferModel(
    String id,
    String placement,
    String date,
    BigDecimal amount,
    String notes
) {
    public BigDecimal getEffectiveAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
