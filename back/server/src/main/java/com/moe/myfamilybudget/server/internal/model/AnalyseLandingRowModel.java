package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Ligne d'atterrissage - comparaison budgeté vs réel pour une ligne budgétaire du mois courant
 */
public record AnalyseLandingRowModel(
    String id,
    String label,
    String kind,
    BigDecimal budgeted,
    BigDecimal reel,
    BigDecimal pct,
    String status
) {
    public AnalyseLandingRowModel {
        budgeted = budgeted != null ? budgeted : BigDecimal.ZERO;
        reel = reel != null ? reel : BigDecimal.ZERO;
        pct = pct != null ? pct : BigDecimal.ZERO;
        status = status != null ? status : "pending";
    }
}