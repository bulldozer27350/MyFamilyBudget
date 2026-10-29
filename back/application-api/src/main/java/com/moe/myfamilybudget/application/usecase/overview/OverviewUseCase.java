package com.moe.myfamilybudget.application.usecase.overview;

/**
 * Cas d'usage « Overview » exposé par l'application (R-85, SILO-316).
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type d'un silo. Le
 * module {@code web} (aujourd'hui {@code OverviewServiceImpl}) convertit le résultat vers le monde HTTP et
 * reste seul responsable de la composition du budget réexposé par l'API ({@code data}, voir
 * {@code BudgetFacadeView}), partagée avec Analyse (R-84) et l'import/export (
 * {@code GlobalBudgetSnapshotService}), hors périmètre de ce cas d'usage.
 */
public interface OverviewUseCase {

    /**
     * Calcule l'aperçu financier global (Overview) : cashflow, projections patrimoniales, rentes FIRE,
     * etc.
     *
     * @param useConstantEuros {@code true} pour exprimer les projections en euros constants
     */
    OverviewResultModel getOverview(Boolean useConstantEuros);
}
