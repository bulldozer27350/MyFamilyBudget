package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Paramètres pour l'agrégation de l'aperçu financier global (RF-900, voir
 * doc/architecture/11-domaine-overview.md).
 *
 * @param retireYear       année de départ à la retraite
 * @param inflationRate    taux d'inflation annuel (pour le calcul du déflateur en euros constants)
 * @param pivotBalance     solde pivot / solde initial de trésorerie (null si non configuré)
 * @param currentYear      année civile courante de référence (déterminisme pour le calcul du flux net actuel)
 * @param useConstantEuros indicateur d'affichage en euros constants
 */
public record OverviewParameters(
        int retireYear,
        BigDecimal inflationRate,
        BigDecimal pivotBalance,
        int currentYear,
        boolean useConstantEuros) {

    public OverviewParameters {
        inflationRate = inflationRate != null ? inflationRate : BigDecimal.ZERO;
    }
}
