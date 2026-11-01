package com.moe.myfamilybudget.domain.retirement.calculation;

import java.math.BigDecimal;

public record SalaryHistoryEntry(int year, BigDecimal salary) {
    public SalaryHistoryEntry { salary = salary != null ? salary : BigDecimal.ZERO; }
}
