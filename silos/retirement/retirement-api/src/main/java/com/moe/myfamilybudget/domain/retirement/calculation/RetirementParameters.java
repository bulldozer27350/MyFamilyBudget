package com.moe.myfamilybudget.domain.retirement.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RetirementParameters(
    BigDecimal pass2026,
    BigDecimal passGrowthRate,
    BigDecimal agircPointValue,
    LocalDate agircPointDate,
    BigDecimal agircPointGrowthRate
) {
    public BigDecimal effectivePass2026() { return pass2026 != null ? pass2026 : new BigDecimal("47100"); }
    public BigDecimal effectivePassGrowthRate() { return passGrowthRate != null ? passGrowthRate : new BigDecimal("0.015"); }
    public BigDecimal effectiveAgircPointValue() { return agircPointValue != null ? agircPointValue : new BigDecimal("1.4386"); }
    public LocalDate effectiveAgircPointDate() { return agircPointDate != null ? agircPointDate : LocalDate.of(2025, 11, 1); }
    public BigDecimal effectiveAgircPointGrowthRate() { return agircPointGrowthRate != null ? agircPointGrowthRate : new BigDecimal("0.01"); }
}
