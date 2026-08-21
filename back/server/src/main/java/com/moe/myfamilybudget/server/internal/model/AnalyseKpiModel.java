package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * KPIs de synthèse pour l'analyse Réel vs Prévisionnel
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