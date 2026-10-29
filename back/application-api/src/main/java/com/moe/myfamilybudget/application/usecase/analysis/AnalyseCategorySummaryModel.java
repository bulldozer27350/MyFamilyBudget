package com.moe.myfamilybudget.application.usecase.analysis;

import java.math.BigDecimal;

/**
 * Résumé par catégorie - total des dépenses par catégorie (R-84, mirroir applicatif de
 * {@code domain.analysis.model.AnalyseCategorySummaryModel}, SILO-315).
 */
public record AnalyseCategorySummaryModel(
    String label,
    BigDecimal amount,
    String color
) {
    public AnalyseCategorySummaryModel {
        amount = amount != null ? amount : BigDecimal.ZERO;
        color = color != null ? color : "#6B7278";
    }
}
