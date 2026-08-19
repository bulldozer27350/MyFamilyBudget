package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record TaxRateOverrideModel(
    Integer year,
    BigDecimal rate
) {}
