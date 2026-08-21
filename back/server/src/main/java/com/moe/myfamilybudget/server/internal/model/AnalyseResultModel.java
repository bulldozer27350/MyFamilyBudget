package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résultat complet de l'analyse Réel vs Prévisionnel
 */
public record AnalyseResultModel(
    AnalyseKpiModel kpis,
    List<AnalyseLandingRowModel> landingData,
    List<AnalyseDriftRowModel> driftRows,
    List<AnalyseMonthlyCompareModel> monthlyCompareData,
    List<AnalyseCategorySummaryModel> categorySummaries,
    String currentMonthISO,
    String currentMonthLabel
) {
    public AnalyseResultModel {
        kpis = kpis != null ? kpis : new AnalyseKpiModel(
            BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO
        );
        landingData = landingData != null ? landingData : List.of();
        driftRows = driftRows != null ? driftRows : List.of();
        monthlyCompareData = monthlyCompareData != null ? monthlyCompareData : List.of();
        categorySummaries = categorySummaries != null ? categorySummaries : List.of();
        currentMonthISO = currentMonthISO != null ? currentMonthISO : "";
        currentMonthLabel = currentMonthLabel != null ? currentMonthLabel : "";
    }
}