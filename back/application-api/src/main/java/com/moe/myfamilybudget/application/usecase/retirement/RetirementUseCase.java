package com.moe.myfamilybudget.application.usecase.retirement;

/**
 * Cas d'usage « Retraite » exposé par l'application (SILO-300, lot B, pilote Retraite).
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type d'un silo. Le
 * module {@code web} (aujourd'hui {@code RetraiteServiceImpl}) convertit le résultat vers le monde HTTP.
 */
public interface RetirementUseCase {

    /**
     * Compose la réponse Retraite : retraite et projections par personne, année de départ, revenus et
     * paramètres généraux.
     */
    RetraiteResultModel getRetraite();

    /**
     * Remplace les données de retraite (SILO-310).
     *
     * @throws IllegalArgumentException si {@code command} est {@code null} (aucune écriture n'est alors faite)
     */
    void saveRetraite(RetraiteSaveCommand command);
}
