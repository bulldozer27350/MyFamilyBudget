package com.moe.myfamilybudget.server.internal.model;

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
