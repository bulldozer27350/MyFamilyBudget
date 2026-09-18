package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record ObjectifModel(
    String id,
    String label,
    BigDecimal targetAmount,
    String targetDate,
    String sourcePlacementId,
    String notes
) {
    public BigDecimal getEffectiveTargetAmount() {
        return targetAmount != null ? targetAmount : BigDecimal.ZERO;
    }
}
