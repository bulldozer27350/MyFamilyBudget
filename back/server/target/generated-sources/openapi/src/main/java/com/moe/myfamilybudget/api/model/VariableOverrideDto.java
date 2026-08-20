package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record VariableOverrideDto(
    String id,
    String label,
    Integer year,
    BigDecimal amount,
    String taxable
) {}
