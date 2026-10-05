package com.moe.myfamilybudget.domain.tax.model;

import java.math.BigDecimal;

public record TaxRateOverrideModel(
    Integer year,
    BigDecimal rate
) {}
