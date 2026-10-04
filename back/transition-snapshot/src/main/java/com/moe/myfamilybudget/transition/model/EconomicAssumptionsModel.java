package com.moe.myfamilybudget.transition.model;

import java.math.BigDecimal;

/**
 * Paramètre de la notion Hypothèses économiques (SILO-100) : taux d'inflation. Aucun silo n'existe pour cette
 * notion ; le modèle vit à côté de {@code EconomicAssumptionsWriter}, dans le module de transition.
 */
public record EconomicAssumptionsModel(BigDecimal inflationRate) {

    public BigDecimal getEffectiveInflationRate() {
        return inflationRate != null ? inflationRate : BigDecimal.ZERO;
    }
}
