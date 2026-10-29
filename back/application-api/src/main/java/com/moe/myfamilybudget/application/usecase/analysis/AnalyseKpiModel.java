package com.moe.myfamilybudget.application.usecase.analysis;

import java.math.BigDecimal;

/**
 * KPIs de synthèse pour l'analyse Réel vs Prévisionnel (R-84, mirroir applicatif de
 * {@code domain.analysis.model.AnalyseKpiModel}, SILO-315).
 */
public record AnalyseKpiModel(
    BigDecimal totalExpenses,
    BigDecimal totalIncome,
    int nbMonths,
    int uncategorizedCount,
    BigDecimal compressibleTotal
) {
    public AnalyseKpiModel {
        totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
        totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        compressibleTotal = compressibleTotal != null ? compressibleTotal : BigDecimal.ZERO;
    }
}
