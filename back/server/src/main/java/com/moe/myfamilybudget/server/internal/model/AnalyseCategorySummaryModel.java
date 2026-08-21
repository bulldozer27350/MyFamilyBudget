package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Résumé par catégorie - total des dépenses par catégorie
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