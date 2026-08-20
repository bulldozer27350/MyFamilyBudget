package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record TaxRateOverrideDto(
    Integer year,
    BigDecimal rate
) {}
