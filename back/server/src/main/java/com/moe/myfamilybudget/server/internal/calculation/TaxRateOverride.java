package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Taux de prélèvement à la source (PAS) saisi manuellement pour une année donnée, remplaçant le
 * taux prévisionnel calculé — voir {@link TaxCalculationInput}.
 */
public record TaxRateOverride(int year, BigDecimal rate) {
}
