package com.moe.myfamilybudget.application.usecase.retirement;

import java.math.BigDecimal;

/**
 * Paramètres généraux rappelés dans la réponse Retraite (bloc {@code settings}, contrat des façades
 * composites). {@code pass2026} et {@code passGrowthRate} ne sont pas portés ici : ils viennent de la retraite.
 */
public record RetraiteSettingsModel(
    Integer birthYear,
    Integer retireAge,
    Integer simulateUntilAge,
    BigDecimal inflationRate,
    String pivotDate,
    String pivotMode,
    BigDecimal startBalance,
    Integer childExitAge,
    BigDecimal taxAbattement,
    Boolean sweepEnabled,
    BigDecimal cashCeiling,
    BigDecimal cashFloor,
    BigDecimal cashAlertThreshold
) {}
