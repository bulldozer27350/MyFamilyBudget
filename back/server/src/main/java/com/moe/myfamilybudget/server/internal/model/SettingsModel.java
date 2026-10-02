package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Paramètres généraux du budget.
 *
 * <p>SET-040 : {@code pass2026} et {@code passGrowthRate} n'appartiennent plus à ce modèle. Ils sont la
 * propriété exclusive du domaine Retraite ({@link RetirementModel}) ; les façades REST ({@code /settings},
 * {@code /budget}, {@code /impots}, {@code /overview}) continuent de les exposer dans {@code settings} en les
 * relisant depuis la retraite, sans changement de contrat.
 */
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
    Boolean sweepEnabled,
    BigDecimal cashCeiling,
    BigDecimal cashFloor,
    BigDecimal cashAlertThreshold
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
        BigDecimal taxAbattement
    ) {
        this(birthYear, retireAge, simulateUntilAge, inflationRate, pivotDate, pivotMode, startBalance, childExitAge, taxAbattement, false, null, null, null);
    }

    // Constructeur de compatibilite ascendante : conserve la signature a 12 parametres (avant l'ajout de
    // cashAlertThreshold) pour ne pas avoir a modifier tous les appels existants (tests, valeurs par defaut).
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
        Boolean sweepEnabled,
        BigDecimal cashCeiling,
        BigDecimal cashFloor
    ) {
        this(birthYear, retireAge, simulateUntilAge, inflationRate, pivotDate, pivotMode, startBalance, childExitAge, taxAbattement, sweepEnabled, cashCeiling, cashFloor, null);
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
