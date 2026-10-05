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
}
