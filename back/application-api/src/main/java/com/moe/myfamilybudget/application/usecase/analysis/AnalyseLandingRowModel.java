package com.moe.myfamilybudget.application.usecase.analysis;

import java.math.BigDecimal;

/**
 * Ligne d'atterrissage - comparaison budgeté vs réel pour une ligne budgétaire du mois courant
 * (R-84, mirroir applicatif de {@code domain.analysis.model.AnalyseLandingRowModel}, SILO-315).
 */
public record AnalyseLandingRowModel(
    String id,
    String label,
    String kind,
    BigDecimal budgeted,
    BigDecimal reel,
    BigDecimal pct,
    String status,
    BigDecimal pendingContrib,
    boolean hasPendingContrib
) {
    public AnalyseLandingRowModel {
        budgeted = budgeted != null ? budgeted : BigDecimal.ZERO;
        reel = reel != null ? reel : BigDecimal.ZERO;
        pct = pct != null ? pct : BigDecimal.ZERO;
        status = status != null ? status : "pending";
        pendingContrib = pendingContrib != null ? pendingContrib : BigDecimal.ZERO;
    }
}
