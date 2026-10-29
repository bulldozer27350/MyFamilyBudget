package com.moe.myfamilybudget.application.usecase.analysis;

import java.math.BigDecimal;

/**
 * Comparaison mensuelle - historique budgeté vs réel sur plusieurs mois (R-84, mirroir
 * applicatif de {@code domain.analysis.model.AnalyseMonthlyCompareModel}, SILO-315).
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
