package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cas d'usage « Trésorerie » exposé par l'application (R-82, même gabarit que {@code TaxUseCase} /
 * {@code WealthUseCase}, SILO-313).
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type d'un silo. Le
 * module {@code web} (aujourd'hui {@code TresorerieServiceImpl}) convertit le résultat vers le monde
 * HTTP.
 */
public interface TreasuryUseCase {

    /**
     * Compose la réponse Trésorerie : revenus, charges, dépenses ponctuelles, revenus variables et
     * leurs surcharges, options de catégorie, suggestions budgétaires et projection de flux.
     */
    TresorerieResultModel getTresorerie(boolean useConstantEuros);

    /**
     * Ajoute une ligne de trésorerie.
     *
     * @param listKey désigne la liste ciblée (revenus, charges, dépenses ponctuelles, revenus
     *                variables, virements), insensible à la casse
     * @param row     la ligne à enregistrer ; {@code null} accepté (création d'une ligne par défaut,
     *                contrat historique de l'API)
     * @return la ligne telle qu'enregistrée
     * @throws IllegalArgumentException si {@code listKey} ne désigne aucune liste connue
     */
    Map<String, Object> addTresorerieLigne(String listKey, Map<String, Object> row);

    /**
     * Met à jour un champ d'une ligne de trésorerie.
     *
     * @throws IllegalArgumentException si {@code listKey} ne désigne aucune liste connue, ou si
     *                                   {@code field} ne désigne aucun champ connu de cette liste
     */
    void updateTresorerieLigne(String listKey, String id, String field, Object value);

    /**
     * Supprime une ligne de trésorerie.
     *
     * @throws IllegalArgumentException si {@code listKey} ne désigne aucune liste connue
     */
    void removeTresorerieLigne(String listKey, String id);

    /**
     * Applique un ajustement budgétaire (bascule du montant mensuel budgété vers la moyenne réelle
     * constatée) sur une ligne de revenu, charge ou placement.
     *
     * @throws IllegalArgumentException si {@code kind} ne désigne aucune nature d'ajustement connue
     */
    void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly);
}
