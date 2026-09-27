package com.moe.myfamilybudget.server.internal.calculation;

/**
 * Horizon de simulation du calcul fiscal, bornes incluses (voir {@link TaxCalculationInput}).
 */
public record TaxSimulationPeriod(int startYear, int endYear) {
}
