package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record SettingsModel(
    Integer birthYear,
    Integer retireAge,
    Integer simulateUntilAge,
    BigDecimal inflationRate,
    String pivotDate,
    String pivotMode,
    BigDecimal startBalance,
    Integer childExitAge,
    BigDecimal taxAbattement,
    BigDecimal pass2026,
    BigDecimal passGrowthRate,
    Boolean sweepEnabled,
    BigDecimal cashCeiling,
    BigDecimal cashFloor
) {
    public SettingsModel(
        Integer birthYear,
        Integer retireAge,
        Integer simulateUntilAge,
        BigDecimal inflationRate,
        String pivotDate,
        String pivotMode,
        BigDecimal startBalance,
        Integer childExitAge,
        BigDecimal taxAbattement,
        BigDecimal pass2026,
        BigDecimal passGrowthRate
    ) {
        this(birthYear, retireAge, simulateUntilAge, inflationRate, pivotDate, pivotMode, startBalance, childExitAge, taxAbattement, pass2026, passGrowthRate, false, null, null);
    }

    public int getEffectiveBirthYear() {
        return birthYear != null ? birthYear : 1985;
    }

    public int getEffectiveRetireAge() {
        return retireAge != null ? retireAge : 64;
    }

    public int getEffectiveSimulateUntilAge() {
        return simulateUntilAge != null ? simulateUntilAge : 85;
    }

    public BigDecimal getEffectiveInflationRate() {
        return inflationRate != null ? inflationRate : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveStartBalance() {
        return startBalance != null ? startBalance : BigDecimal.ZERO;
    }

    public int getEffectiveChildExitAge() {
        return childExitAge != null ? childExitAge : 21;
    }

    public BigDecimal getEffectiveTaxAbattement() {
        return taxAbattement != null ? taxAbattement : BigDecimal.ZERO;
    }
}

