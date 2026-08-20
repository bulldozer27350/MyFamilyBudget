package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;
import java.util.List;

public record RetirementDto(
    List<RetirementPersonDto> people,
    BigDecimal pass2026,
    BigDecimal passGrowthRate,
    BigDecimal agircPointValue,
    String agircPointDateGlobal,
    BigDecimal agircPointGrowthRate
) {
    public record RetirementPersonDto(
        String id,
        String name,
        Integer birthYear,
        String incomeLabel,
        Integer trimestresValides,
        String trimestresDate,
        List<SalaryHistoryDto> salaryHistory,
        BigDecimal agircPoints,
        BigDecimal ratioPointsParEuro
    ) {}

    public record SalaryHistoryDto(
        Integer year,
        BigDecimal salary
    ) {}
}
