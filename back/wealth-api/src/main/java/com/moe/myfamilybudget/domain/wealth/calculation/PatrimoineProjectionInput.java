package com.moe.myfamilybudget.domain.wealth.calculation;

import java.util.List;

/**
 * Contrat d'entrée de la projection patrimoniale (RF-300, voir
 * doc/architecture/05-domaine-patrimoine.md).
 *
 * <p>Pas de {@code ContributionDecisionPlan} à ce stade : seules les valeurs de configuration
 * statiques de chaque placement sont fournies (voir {@link PlacementProjectionInput}). Ce point
 * ouvert est traité en RF-400.
 *
 * @param placements liste des placements à projeter
 * @param transfers  retraits programmés, tous placements confondus
 * @param cashflow   flux net annuel hors placements, pour décider la pause des versements
 * @param parameters horizon et hypothèses de la projection
 */
public record PatrimoineProjectionInput(
        List<PlacementProjectionInput> placements,
        List<PlacementTransfer> transfers,
        CashflowProjection cashflow,
        PatrimoineProjectionParameters parameters) {

    public PatrimoineProjectionInput {
        placements = placements != null ? List.copyOf(placements) : List.of();
        transfers = transfers != null ? List.copyOf(transfers) : List.of();
        cashflow = cashflow != null ? cashflow : new CashflowProjection(List.of());
    }
}
