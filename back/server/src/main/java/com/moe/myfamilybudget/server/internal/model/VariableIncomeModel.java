package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record VariableIncomeModel(
    String id,
    String label,
    String refIncomeLabel,
    BigDecimal rate,
    Integer startYear,
    Integer endYear,
    String taxable,
    String type,
    String notes
) {
    public BigDecimal getEffectiveRate() {
        return rate != null ? rate : BigDecimal.ZERO;
    }
}

