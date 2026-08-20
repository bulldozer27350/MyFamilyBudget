package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record TaxActualOverrideDto(
    Integer year,
    BigDecimal amount
) {}
