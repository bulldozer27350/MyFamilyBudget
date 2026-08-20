package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record PlacementDto(
    String id,
    String label,
    String category,
    BigDecimal balance,
    String balanceDate,
    BigDecimal monthly,
    String monthlyFrom,
    String monthlyUntil,
    BigDecimal ratePess,
    BigDecimal rateCorr,
    BigDecimal rateOpti,
    Boolean excludedFromRetirement,
    String notes
) {}
