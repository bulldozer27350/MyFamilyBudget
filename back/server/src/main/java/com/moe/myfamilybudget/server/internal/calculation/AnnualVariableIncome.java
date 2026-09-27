package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Part imposable des revenus variables (primes, etc.) déjà projetée pour une année — voir
 * {@link TaxCalculationInput}. Ne reprend que le montant imposable : la part non imposable d'un
 * revenu variable n'entre pas dans le calcul fiscal.
 */
public record AnnualVariableIncome(int year, BigDecimal taxableAmount) {
}
