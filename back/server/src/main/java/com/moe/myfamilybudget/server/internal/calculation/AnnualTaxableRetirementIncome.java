package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Pension de retraite imposable déjà projetée pour une année (somme des pensions de toutes les
 * personnes du foyer) — voir {@link TaxCalculationInput}.
 *
 * <p>Depuis RF-202, cette valeur est produite par {@code TaxInputFactory} à partir de la
 * projection de {@code RetirementCalculationService} (RF-102).
 */
public record AnnualTaxableRetirementIncome(int year, BigDecimal amount) {
}
