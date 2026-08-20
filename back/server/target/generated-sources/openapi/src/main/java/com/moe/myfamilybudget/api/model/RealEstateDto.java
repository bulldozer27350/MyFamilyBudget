package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record RealEstateDto(
    String id,
    String label,
    String type,
    BigDecimal currentValue,
    Integer valuationYear,
    BigDecimal annualGrowthRate,
    String notes
) {}
