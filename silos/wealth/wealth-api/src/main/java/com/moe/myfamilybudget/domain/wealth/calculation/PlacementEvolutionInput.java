package com.moe.myfamilybudget.domain.wealth.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contrat d'entrée de la chronologie d'un placement (RF-301, voir
 * doc/architecture/05-domaine-patrimoine.md) : un segment « réel » (historique saisi) suivi de
 * trois projections qui repartent du dernier point réel connu.
 *
 * <p>L'historique de valorisation n'apparaît que dans cet input, pas dans
 * {@link PatrimoineProjectionInput}. Tous les placements du budget sont fournis, car la pause
 * automatique des versements du placement tracé dépend du solde des autres placements
 * ({@code ContributionPauseRules}).
 *
 * @param placement  placement tracé
 * @param history    valeurs réelles saisies pour ce placement, dans un ordre quelconque
 * @param background tous les placements du budget (y compris le placement tracé), simulés en arrière-plan
 * @param transfers  retraits programmés, tous placements confondus
 * @param parameters horizon et hypothèses
 */
public record PlacementEvolutionInput(
        PlacementProjectionInput placement,
        List<PlacementHistoryPoint> history,
        List<BackgroundPlacement> background,
        List<PlacementTransfer> transfers,
        PlacementEvolutionParameters parameters) {

    public PlacementEvolutionInput {
        history = history != null ? List.copyOf(history) : List.of();
        background = background != null ? List.copyOf(background) : List.of();
        transfers = transfers != null ? List.copyOf(transfers) : List.of();
    }

    /**
     * Placement simulé en arrière-plan pour décider la pause.
     *
     * @param placement          configuration du placement
     * @param latestKnownBalance dernière valeur réelle connue (sinon le solde de référence)
     */
    public record BackgroundPlacement(PlacementProjectionInput placement, BigDecimal latestKnownBalance) {
        public BackgroundPlacement {
            latestKnownBalance = latestKnownBalance != null ? latestKnownBalance : BigDecimal.ZERO;
        }
    }
}
