package com.moe.myfamilybudget.application.usecase.system;

import java.util.Map;

/**
 * Cas d'usage « Système » exposé par l'application (R-88, SILO-319).
 *
 * <p>Contrat applicatif : opérations globales sur l'instantané du budget complet
 * (export, import et réinitialisation).
 *
 * <p>Il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}.
 */
public interface SystemUseCase {

    /**
     * Exporte l'intégralité du modèle de données du budget sous forme d'arbre de données.
     *
     * @return map représentant l'instantané complet du budget
     */
    Map<String, Object> exportBudget();

    /**
     * Importe un instantané complet du budget sous transaction et verrous de mutation.
     *
     * @param snapshot données complètes du budget à importer
     * @return l'instantané du budget après import
     */
    Map<String, Object> importBudget(Map<String, Object> snapshot);

    /**
     * Réinitialise l'ensemble des données budgétaires sous transaction et verrous de mutation.
     *
     * @return l'instantané du budget après réinitialisation
     */
    Map<String, Object> resetBudget();
}
