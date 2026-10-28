package com.moe.myfamilybudget.application.usecase.wealth;

import java.util.Map;

/**
 * Cas d'usage « Patrimoine » exposé par l'application (R-81, même gabarit que {@code TaxUseCase} /
 * {@code RetirementUseCase}, SILO-312).
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type d'un silo —
 * {@code Map<String, Object>} reste un type générique du JDK (pas un type d'un silo), choisi ici car
 * les lignes de patrimoine (placements, immobilier, virements) ont des formes hétérogènes, comme pour
 * {@code PatrimoineWriter} dont ces méthodes ne sont qu'une façade. Le module {@code web} (aujourd'hui
 * {@code PatrimoineServiceImpl}) convertit le résultat vers le monde HTTP.
 *
 * <p>DB-041 (routage de {@code loans}/{@code credits} et {@code objectifs} vers d'autres command
 * services) reste hors de ce cas d'usage, comme pour Fiscalité (R-80) : c'est un routage générique,
 * pas une règle du domaine Patrimoine, laissé dans {@code PatrimoineServiceImpl}.
 */
public interface WealthUseCase {

    /**
     * Compose la réponse Patrimoine : placements, virements, immobilier, catégories d'actifs,
     * projections, prêts et catégories de l'import bancaire (lus chez d'autres silos).
     */
    WealthResultModel getPatrimoine(boolean useConstantEuros);

    /**
     * Façade mince (RF-301) : projection patrimoniale annuelle seule, sans le reste de la réponse
     * (placements/virements/etc.), utilisée notamment par les tests.
     */
    PatrimoineProjectionsModel computePatrimoineProjections(boolean useConstantEuros);

    /**
     * Chronologie (historique réel + trois projections) d'un placement donné.
     *
     * @return {@code null} si {@code placementId} ne désigne aucun placement connu (traduit en 404
     *         par le module {@code web})
     */
    PlacementEvolutionModel getPlacementEvolution(String placementId, boolean useConstantEuros);

    /**
     * Enregistre une ligne de patrimoine (placement, virement ou bien immobilier).
     *
     * @param listKey désigne la liste ciblée ({@code "placements"}, {@code "transfers"} ou
     *                {@code "realEstate"}, insensible à la casse)
     * @param row     la ligne à enregistrer ; {@code null} accepté (création d'une ligne par défaut,
     *                contrat historique de l'API)
     * @return la ligne telle qu'enregistrée
     * @throws IllegalArgumentException si {@code listKey} ne désigne aucune liste connue
     */
    Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> row);

    /**
     * Supprime une ligne de patrimoine.
     *
     * @throws IllegalArgumentException si {@code listKey} ne désigne aucune liste connue
     */
    void deletePatrimoineRow(String listKey, String id);

    /** Ajoute un point d'historique de valorisation à un placement. */
    Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> entry);

    /** Met à jour un point d'historique de valorisation d'un placement. */
    void updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> entry);

    /** Supprime un point d'historique de valorisation d'un placement. */
    void deletePlacementHistoryEntry(String placementId, String entryId);
}
