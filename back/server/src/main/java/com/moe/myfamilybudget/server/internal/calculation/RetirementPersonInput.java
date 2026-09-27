package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RetirementPersonInput(
    int birthYear,
    int trimestresValides,
    LocalDate trimestresDate,
    List<SalaryHistoryEntry> salaryHistory,
    BigDecimal agircPoints,
    BigDecimal ratioPointsParEuro,
    List<AnnualSalaryProjection> projectedSalaries
) {
    public RetirementPersonInput {
        salaryHistory = salaryHistory != null ? List.copyOf(salaryHistory) : List.of();
        projectedSalaries = projectedSalaries != null ? List.copyOf(projectedSalaries) : List.of();
        agircPoints = agircPoints != null ? agircPoints : BigDecimal.ZERO;
        ratioPointsParEuro = ratioPointsParEuro != null ? ratioPointsParEuro : new BigDecimal("0.0051");
    }
}
