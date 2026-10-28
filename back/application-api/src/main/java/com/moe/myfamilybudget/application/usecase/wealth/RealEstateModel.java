package com.moe.myfamilybudget.application.usecase.wealth;

import java.math.BigDecimal;

/** Un bien immobilier, rappelé dans la réponse Patrimoine. */
public record RealEstateModel(
    String id,
    String label,
    String type,
    BigDecimal currentValue,
    Integer valuationYear,
    BigDecimal annualGrowthRate,
    String notes
) {}
