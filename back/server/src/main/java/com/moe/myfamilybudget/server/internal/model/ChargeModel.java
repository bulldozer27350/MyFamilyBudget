package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record ChargeModel(
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

    public BigDecimal getEffectiveGrowthRate(BigDecimal defaultInflationRate) {
        if (growthRate != null) {
            return growthRate;
        }
        return defaultInflationRate != null ? defaultInflationRate : BigDecimal.ZERO;
    }
}
