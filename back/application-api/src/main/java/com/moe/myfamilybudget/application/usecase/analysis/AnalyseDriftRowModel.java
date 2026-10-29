package com.moe.myfamilybudget.application.usecase.analysis;

import java.math.BigDecimal;

/**
 * Ligne de dérive - analyse des écarts entre moyennes réelles et budgeté (R-84, mirroir
 * applicatif de {@code domain.analysis.model.AnalyseDriftRowModel}, SILO-315).
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
        status = status != null ? status : "pending";
    }
}
