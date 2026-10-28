package com.moe.myfamilybudget.application.usecase.tax;

import java.util.List;

/**
 * Commande de sauvegarde de la configuration fiscale (enfants, barème, surcharges). Une liste
 * {@code null} conserve la valeur déjà enregistrée (contrat historique de l'API) : l'appelant
 * (couche web) ne construit une liste que pour les champs reçus dans le corps JSON.
 */
public record TaxConfigSaveCommand(
    List<TaxChildModel> taxChildren,
    List<TaxBracketModel> taxBrackets,
    List<TaxRateOverrideModel> taxRateOverrides,
    List<TaxActualOverrideModel> taxActualOverrides
) {}
