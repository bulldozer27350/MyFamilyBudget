package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record IncomeModel(
    String id,
    String label,
    BigDecimal monthly,
    String start,
    String end,
    BigDecimal growthRate,
    String categoryId,
    String notes
) {
    public BigDecimal getEffectiveMonthly() {
        return monthly != null ? monthly : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveGrowthRate() {
        return growthRate != null ? growthRate : BigDecimal.ZERO;
    }
}
