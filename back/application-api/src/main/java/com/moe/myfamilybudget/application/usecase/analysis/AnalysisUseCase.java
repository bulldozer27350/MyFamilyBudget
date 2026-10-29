package com.moe.myfamilybudget.application.usecase.analysis;

/**
 * Cas d'usage « Analyse » exposé par l'application (R-84, même gabarit que {@code TaxUseCase} /
 * {@code TreasuryUseCase}, SILO-315).
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type d'un silo. Le
 * module {@code web} (aujourd'hui {@code AnalyseServiceImpl}) convertit le résultat vers le monde HTTP et
 * reste seul responsable de la composition du budget réexposé par l'API ({@code data}, voir
 * {@code BudgetFacadeView}), partagée avec Overview (R-85) et l'import/export (
 * {@code GlobalBudgetSnapshotService}), hors périmètre de ce cas d'usage.
 */
public interface AnalysisUseCase {

    /**
     * Calcule l'analyse Réel vs Prévisionnel : KPIs, atterrissage, dérives, comparatif mensuel et
     * synthèse par catégorie, sur les {@code monthsBack} derniers mois (12 par défaut si absent ou
     * négatif).
     */
    AnalyseResultModel getAnalyse(Integer monthsBack);
}
