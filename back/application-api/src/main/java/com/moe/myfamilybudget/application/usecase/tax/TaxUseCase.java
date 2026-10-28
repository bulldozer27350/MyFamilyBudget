package com.moe.myfamilybudget.application.usecase.tax;

/**
 * Cas d'usage « Fiscalité » exposé par l'application (R-80, même gabarit que {@code RetirementUseCase},
 * SILO-311).
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type d'un silo. Le
 * module {@code web} (aujourd'hui {@code ImpotsServiceImpl}) convertit le résultat vers le monde HTTP.
 */
public interface TaxUseCase {

    /**
     * Compose la réponse Fiscalité : enfants à charge, barème, surcharges, paramètres généraux et
     * simulation fiscale par année.
     */
    TaxResultModel getImpots();

    /**
     * Met à jour la configuration fiscale (enfants, barème, surcharges). Une liste {@code null} dans
     * {@code command} conserve la valeur déjà enregistrée.
     *
     * @throws IllegalArgumentException si {@code command} est {@code null} (aucune écriture n'est alors faite)
     */
    void saveTaxConfig(TaxConfigSaveCommand command);

    /** Réinitialise le barème d'imposition aux tranches par défaut. */
    void resetDefaultTaxBrackets();
}
