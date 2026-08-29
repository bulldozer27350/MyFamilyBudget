package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record VariableOverrideModel(
    String id,
    String label,
    Integer year,
    BigDecimal amount,
    String taxable,
    String notes
) {
    public BigDecimal getEffectiveAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}

