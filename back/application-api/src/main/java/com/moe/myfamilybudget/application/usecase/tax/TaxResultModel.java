package com.moe.myfamilybudget.application.usecase.tax;

import java.math.BigDecimal;
import java.util.List;

/**
 * Modèle applicatif de la réponse Fiscalité : enfants à charge, barème, surcharges, paramètres
 * généraux et simulation fiscale par année.
 *
 * <p>{@code pass2026} et {@code passGrowthRate} sont rappelés depuis la retraite (contrat historique de
 * la réponse REST, voir {@code SettingsDto}) et peuvent être {@code null} en l'absence de retraite.
 */
public record TaxResultModel(
    List<TaxChildModel> taxChildren,
    List<TaxBracketModel> taxBrackets,
    List<TaxRateOverrideModel> taxRateOverrides,
    List<TaxActualOverrideModel> taxActualOverrides,
    TaxSettingsViewModel settings,
    List<TaxYearlyModel> taxPreview,
    BigDecimal pass2026,
    BigDecimal passGrowthRate
) {}
