package com.moe.myfamilybudget.domain.tax.model;

import java.math.BigDecimal;

public record TaxActualOverrideModel(
    Integer year,
    BigDecimal amount
) {}
