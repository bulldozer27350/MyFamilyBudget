package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record PlacementModel(
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
    String notes,
    Integer sweepPriority,
    BigDecimal sweepCap,
    BigDecimal pauseTriggerBalance,
    Integer pausePriority,
    String categoryId
) {
    public BigDecimal getEffectiveBalance() {
        return balance != null ? balance : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveMonthly() {
        return monthly != null ? monthly : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveRatePess() {
        return ratePess != null ? ratePess : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveRateCorr() {
        return rateCorr != null ? rateCorr : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveRateOpti() {
        return rateOpti != null ? rateOpti : BigDecimal.ZERO;
    }

    public boolean isExcludedFromRetirement() {
        return Boolean.TRUE.equals(excludedFromRetirement);
    }
}

