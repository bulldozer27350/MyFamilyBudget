package com.moe.myfamilybudget.domain.wealth.model;

import java.math.BigDecimal;

public record RealEstateModel(
    String id,
    String label,
    String type,
    BigDecimal currentValue,
    Integer valuationYear,
    BigDecimal annualGrowthRate,
    String notes
) {
    public BigDecimal getEffectiveCurrentValue() {
        return currentValue != null ? currentValue : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveAnnualGrowthRate() {
        return annualGrowthRate != null ? annualGrowthRate : BigDecimal.ZERO;
    }
}
