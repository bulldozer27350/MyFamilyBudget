package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Ligne de dérive - analyse des écarts entre moyennes réelles et budgeté
 */
public record AnalyseDriftRowModel(
    String id,
    String label,
    String kind,
    BigDecimal budgeted,
    BigDecimal avg3m,
    BigDecimal avg12m,
    BigDecimal ecart,
    BigDecimal ecartPct,
    String status,
    int months
) {
    public AnalyseDriftRowModel {
        budgeted = budgeted != null ? budgeted : BigDecimal.ZERO;
        avg3m = avg3m != null ? avg3m : null;
        avg12m = avg12m != null ? avg12m : null;
        ecart = ecart != null ? ecart : null;
        ecartPct = ecartPct != null ? ecartPct : null;
        status = status != null ? status : "pending";
    }
}