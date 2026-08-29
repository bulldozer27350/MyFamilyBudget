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
    public VariableOverrideModel(
        String id,
        String label,
        Integer year,
        BigDecimal amount,
        String taxable
    ) {
        this(id, label, year, amount, taxable, "");
    }

    public BigDecimal getEffectiveAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}

