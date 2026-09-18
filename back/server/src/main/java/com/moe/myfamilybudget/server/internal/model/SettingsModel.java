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
    BigDecimal cashFloor,
    BigDecimal cashAlertThreshold,
    Integer goalSecureHorizonMonths,
    Integer goalLiquidHorizonMonths
) {
    // Constructeur de compatibilite ascendante : conserve la signature historique a 15
    // parametres (avant l'ajout des seuils de bascule des objectifs) pour ne pas avoir a
    // modifier tous les appels existants qui construisent encore ces 15 champs. Les deux
    // nouveaux seuils valent alors null (valeurs par defaut appliquees par les getEffective*).
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
        BigDecimal passGrowthRate,
        Boolean sweepEnabled,
        BigDecimal cashCeiling,
        BigDecimal cashFloor,
        BigDecimal cashAlertThreshold
    ) {
        this(birthYear, retireAge, simulateUntilAge, inflationRate, pivotDate, pivotMode, startBalance, childExitAge, taxAbattement, pass2026, passGrowthRate, sweepEnabled, cashCeiling, cashFloor, cashAlertThreshold, null, null);
    }

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
        this(birthYear, retireAge, simulateUntilAge, inflationRate, pivotDate, pivotMode, startBalance, childExitAge, taxAbattement, pass2026, passGrowthRate, false, null, null, null);
    }

    // Constructeur de compatibilite ascendante : conserve la signature historique a 14
    // parametres (avant l'ajout de cashAlertThreshold) pour ne pas avoir a modifier tous les
    // appels existants (tests, valeurs par defaut) qui construisent encore ces 14 champs.
    // cashAlertThreshold vaut alors null (aucun seuil d'alerte configure).
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
        BigDecimal passGrowthRate,
        Boolean sweepEnabled,
        BigDecimal cashCeiling,
        BigDecimal cashFloor
    ) {
        this(birthYear, retireAge, simulateUntilAge, inflationRate, pivotDate, pivotMode, startBalance, childExitAge, taxAbattement, pass2026, passGrowthRate, sweepEnabled, cashCeiling, cashFloor, null);
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

    public int getEffectiveGoalSecureHorizonMonths() {
        return goalSecureHorizonMonths != null ? goalSecureHorizonMonths : 12;
    }

    public int getEffectiveGoalLiquidHorizonMonths() {
        return goalLiquidHorizonMonths != null ? goalLiquidHorizonMonths : 3;
    }
}

