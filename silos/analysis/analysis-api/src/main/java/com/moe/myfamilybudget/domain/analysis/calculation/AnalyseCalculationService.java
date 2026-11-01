package com.moe.myfamilybudget.domain.analysis.calculation;

import com.moe.myfamilybudget.domain.analysis.model.AnalyseResultModel;

/**
 * Interface de service du silo Analyse (SILO-155, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur d'analyse Réel vs Prévisionnel pour les consommateurs ({@code application}, tests) : ils
 * ne connaissent ni son implémentation ({@code DefaultAnalyseCalculationService}, dans {@code analysis-core}),
 * ni son câblage (déclaré par le composition root). Reçoit exclusivement {@link AnalyseInput} : aucun
 * {@code BudgetDataModel}, aucun autre modèle persistant.
 */
public interface AnalyseCalculationService {

    /**
     * Calcule l'analyse (KPI, dérives, atterrissage, comparatif mensuel, synthèse par catégorie).
     * Une entrée {@code null} est lue comme une entrée vide.
     */
    AnalyseResultModel computeAnalyse(AnalyseInput input);
}
