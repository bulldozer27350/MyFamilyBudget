package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

public record SalaryHistoryEntry(int year, BigDecimal salary) {
    public SalaryHistoryEntry { salary = salary != null ? salary : BigDecimal.ZERO; }
}
