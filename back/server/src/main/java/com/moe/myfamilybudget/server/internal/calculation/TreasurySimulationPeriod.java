package com.moe.myfamilybudget.server.internal.calculation;

/**
 * Horizon de simulation de la projection de trésorerie, bornes incluses (RF-400, voir
 * doc/architecture/06-domaine-tresorerie.md). Distinct de {@link TaxSimulationPeriod} : chaque
 * domaine possède son propre contrat, même de forme identique (voir 00-principes.md).
 */
public record TreasurySimulationPeriod(int startYear, int endYear) {
}
