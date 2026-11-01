package com.moe.myfamilybudget.domain.treasury.model;

import java.math.BigDecimal;

/**
 * Paramètres généraux dont Trésorerie est propriétaire (SILO-100) : date et mode pivot, solde de départ,
 * balayage (sweep) et seuils de cash. Les valeurs par défaut sont celles historiquement appliquées par
 * {@code SettingsModel}.
 */
public record TresorerieSettingsModel(
    String pivotDate,
    String pivotMode,
    BigDecimal startBalance,
    Boolean sweepEnabled,
    BigDecimal cashCeiling,
    BigDecimal cashFloor,
    BigDecimal cashAlertThreshold
) {

    public BigDecimal getEffectiveStartBalance() {
        return startBalance != null ? startBalance : BigDecimal.ZERO;
    }

    public boolean getEffectiveSweepEnabled() {
        return sweepEnabled != null && sweepEnabled;
    }
}
