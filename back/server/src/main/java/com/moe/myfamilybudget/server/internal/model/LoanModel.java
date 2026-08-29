package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record LoanModel(
    String id,
    String label,
    BigDecimal crd,
    BigDecimal rate,
    BigDecimal monthly,
    BigDecimal insurance,
    String startDate,
    String endDate
) {
    public BigDecimal getEffectiveCrd() {
        return crd != null ? crd : BigDecimal.ZERO;
    }
    public BigDecimal getEffectiveRate() {
        return rate != null ? rate : BigDecimal.ZERO;
    }
    public BigDecimal getEffectiveMonthly() {
        return monthly != null ? monthly : BigDecimal.ZERO;
    }
    public BigDecimal getEffectiveInsurance() {
        return insurance != null ? insurance : BigDecimal.ZERO;
    }
}
