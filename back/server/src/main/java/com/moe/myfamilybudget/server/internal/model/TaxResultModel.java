package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

/**
 * Modèle interne représentant l'ensemble des données et résultats de la section Impôts.
 */
public record TaxResultModel(
    List<TaxChildModel> taxChildren,
    List<TaxBracketModel> taxBrackets,
    List<TaxRateOverrideModel> taxRateOverrides,
    List<TaxActualOverrideModel> taxActualOverrides,
    SettingsModel settings,
    List<TaxYearlyModel> taxPreview
) {}
