package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record TaxActualOverrideModel(
    Integer year,
    BigDecimal amount
) {}
