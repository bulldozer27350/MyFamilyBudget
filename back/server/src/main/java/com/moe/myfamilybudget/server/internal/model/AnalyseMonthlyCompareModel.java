package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Comparaison mensuelle - historique budgeté vs réel sur plusieurs mois
 */
public record AnalyseMonthlyCompareModel(
    String monthISO,
    String label,
    BigDecimal budgeted,
    BigDecimal reel,
    boolean hasPointing
) {
    public AnalyseMonthlyCompareModel {
        budgeted = budgeted != null ? budgeted : BigDecimal.ZERO;
        reel = reel != null ? reel : BigDecimal.ZERO;
    }
}