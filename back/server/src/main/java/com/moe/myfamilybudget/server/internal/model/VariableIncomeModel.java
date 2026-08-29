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
    public VariableIncomeModel(
        String id,
        String label,
        String refIncomeLabel,
        BigDecimal rate,
        Integer startYear,
        Integer endYear,
        String taxable
    ) {
        this(id, label, refIncomeLabel, rate, startYear, endYear, taxable, "prime", "");
    }

    public VariableIncomeModel(
        String id,
        String label,
        String refIncomeLabel,
        BigDecimal rate,
        Integer startYear,
        Integer endYear,
        String taxable,
        String type
    ) {
        this(id, label, refIncomeLabel, rate, startYear, endYear, taxable, type, "");
    }

    public BigDecimal getEffectiveRate() {
        return rate != null ? rate : BigDecimal.ZERO;
    }
}

