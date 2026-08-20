package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record VariableIncomeDto(
    String id,
    String label,
    String refIncomeLabel,
    BigDecimal rate,
    Integer startYear,
    Integer endYear,
    String taxable
) {}
