package com.moe.myfamilybudget.domain.wealth.calculation;

/**
 * Interface de service du silo Patrimoine, chronologie d'un placement (SILO-152, voir
 * doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur d'évolution pour les consommateurs ({@code application}, tests) : ils ne
 * connaissent ni son implémentation ({@code DefaultPlacementEvolutionService}, dans {@code wealth-core}),
 * ni son câblage (déclaré par le composition root). Reçoit exclusivement {@link PlacementEvolutionInput} :
 * aucun {@code BudgetDataModel}, aucun DTO.
 */
public interface PlacementEvolutionService {

    /**
     * Construit le segment réel (historique saisi) suivi des trois projections mensuelles qui repartent
     * du dernier point réel connu.
     */
    PlacementEvolution compute(PlacementEvolutionInput input, boolean useConstantEuros);
}
