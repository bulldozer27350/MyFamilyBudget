package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Pension de retraite imposable déjà projetée pour une année (somme des pensions de toutes les
 * personnes du foyer) — voir {@link TaxCalculationInput}.
 *
 * <p>En version 1 (RF-200), cette valeur est encore calculée par {@code TaxInputFactory} avec la
 * logique de pension historique de {@code TaxCalculator}. RF-202 la fera provenir de
 * {@code RetirementCalculationService}, sans changer ce contrat.
 */
public record AnnualTaxableRetirementIncome(int year, BigDecimal amount) {
}
