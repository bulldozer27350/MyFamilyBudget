package com.moe.myfamilybudget.domain.settings.model;

import java.math.BigDecimal;

/**
 * Paramètre de la notion Hypothèses économiques (SILO-100) : taux d'inflation. Propriété de l'application, sans
 * propriétaire métier : elle appartient au silo Paramètres (SILO-220).
 */
public record EconomicAssumptionsModel(BigDecimal inflationRate) {

    public BigDecimal getEffectiveInflationRate() {
        return inflationRate != null ? inflationRate : BigDecimal.ZERO;
    }
}
