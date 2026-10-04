package com.moe.myfamilybudget.domain.tax.model;

import java.math.BigDecimal;

/**
 * Paramètres généraux dont Fiscalité est propriétaire (SILO-100) : âge de sortie des enfants et abattement.
 * Les valeurs par défaut sont celles historiquement appliquées par {@code SettingsModel}.
 */
public record TaxSettingsModel(Integer childExitAge, BigDecimal taxAbattement) {

    public int getEffectiveChildExitAge() {
        return childExitAge != null ? childExitAge : 21;
    }

    public BigDecimal getEffectiveTaxAbattement() {
        return taxAbattement != null ? taxAbattement : BigDecimal.ZERO;
    }
}
