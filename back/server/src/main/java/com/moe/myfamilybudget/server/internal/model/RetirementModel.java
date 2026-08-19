package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record RetirementModel(
    List<RetirementPersonModel> people,
    BigDecimal pass2026,
    BigDecimal passGrowthRate,
    BigDecimal agircPointValue,
    String agircPointDateGlobal,
    BigDecimal agircPointGrowthRate
) {
    public List<RetirementPersonModel> getEffectivePeople() {
        return people != null ? people : List.of();
    }

    public BigDecimal getEffectivePass2026() {
        return pass2026 != null ? pass2026 : new BigDecimal("47100");
    }

    public BigDecimal getEffectivePassGrowthRate() {
        return passGrowthRate != null ? passGrowthRate : new BigDecimal("0.015");
    }

    public BigDecimal getEffectiveAgircPointValue() {
        return agircPointValue != null ? agircPointValue : new BigDecimal("1.4386");
    }

    public BigDecimal getEffectiveAgircPointGrowthRate() {
        return agircPointGrowthRate != null ? agircPointGrowthRate : new BigDecimal("0.01");
    }

    public record RetirementPersonModel(
        String id,
        String name,
        Integer birthYear,
        String incomeLabel,
        Integer trimestresValides,
        String trimestresDate,
        List<SalaryHistoryModel> salaryHistory,
        BigDecimal agircPoints,
        BigDecimal ratioPointsParEuro
    ) {
        public int getEffectiveTrimestresValides() {
            return trimestresValides != null ? trimestresValides : 0;
        }

        public List<SalaryHistoryModel> getEffectiveSalaryHistory() {
            return salaryHistory != null ? salaryHistory : List.of();
        }

        public BigDecimal getEffectiveAgircPoints() {
            return agircPoints != null ? agircPoints : BigDecimal.ZERO;
        }

        public BigDecimal getEffectiveRatioPointsParEuro() {
            return ratioPointsParEuro != null ? ratioPointsParEuro : new BigDecimal("0.0051");
        }
    }

    public record SalaryHistoryModel(
        Integer year,
        BigDecimal salary
    ) {
        public BigDecimal getEffectiveSalary() {
            return salary != null ? salary : BigDecimal.ZERO;
        }
    }
}
