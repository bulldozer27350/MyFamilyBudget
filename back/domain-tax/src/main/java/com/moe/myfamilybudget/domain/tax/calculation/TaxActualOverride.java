package com.moe.myfamilybudget.domain.tax.calculation;

import java.math.BigDecimal;

/**
 * Montant d'impôt réel saisi manuellement pour une année donnée (avis d'imposition reçu),
 * remplaçant l'impôt prévisionnel calculé — voir {@link TaxCalculationInput}.
 */
public record TaxActualOverride(int year, BigDecimal amount) {
}
