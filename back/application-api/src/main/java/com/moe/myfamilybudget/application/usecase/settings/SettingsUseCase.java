package com.moe.myfamilybudget.application.usecase.settings;

import java.util.Map;

/**
 * Cas d'usage « Paramètres » exposé par l'application (R-88, SILO-319).
 *
 * <p>Contrat applicatif : consultation de la vue composite des paramètres
 * (assemblée depuis les ports des silos propriétaires, les catégories d'actifs et l'import bancaire)
 * et mise à jour atomique des paramètres (routage vers les commandes par domaine sous transaction
 * et verrou de mutation).
 *
 * <p>Il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}.
 */
public interface SettingsUseCase {

    /**
     * Compose et retourne la vue complète des paramètres budgétaires.
     *
     * @return map composite contenant les paramètres, les catégories d'actifs, et les métadonnées
     */
    Map<String, Object> getSettings();

    /**
     * Applique une mise à jour des paramètres budgétaires sous transaction et verrous de mutation appropriés.
     *
     * @param body corps de la requête contenant la modification (paramètre simple, bloc settings ou catégorie d'actif)
     */
    void saveSettings(Object body);
}
