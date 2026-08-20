package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record SettingsDto(
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
) {}
