package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

public record AnnualSalaryProjection(int year, BigDecimal annualSalary) {
    public AnnualSalaryProjection { annualSalary = annualSalary != null ? annualSalary : BigDecimal.ZERO; }
}
