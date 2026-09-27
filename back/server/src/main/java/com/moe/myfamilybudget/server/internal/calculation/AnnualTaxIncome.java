package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Revenu régulier imposable déjà projeté pour une année (somme de tous les revenus réguliers du
 * foyer pour cette année-là) — voir {@link TaxCalculationInput}.
 */
public record AnnualTaxIncome(int year, BigDecimal amount) {
}
