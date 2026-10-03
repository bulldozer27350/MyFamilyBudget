package com.moe.myfamilybudget.domain.goals.calculation;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Entrée de la règle de notification « objectif atteignable » (RF-701, voir
 * doc/architecture/09-domaine-objectifs-notifications.md).
 *
 * <p>Une des trois entrées distinctes des notifications. Les objectifs désignent leurs placements
 * par identifiant ; les soldes courants arrivent sous forme de {@link PlacementBalanceSnapshot}
 * (RF-700), sans dépendance à {@code PlacementModel} ni à {@code ObjectifModel}.
 *
 * @param goals             objectifs avec leurs allocations
 * @param placementBalances soldes courants des placements référencés par les allocations
 */
public record ObjectifReachableInput(
        List<GoalCoverage> goals,
        List<PlacementBalanceSnapshot> placementBalances) {

    public ObjectifReachableInput {
        if (goals == null) goals = Collections.emptyList();
        if (placementBalances == null) placementBalances = Collections.emptyList();
    }

    /**
     * Objectif et ses allocations, réduits à ce qu'exige le calcul de couverture.
     *
     * @param id           identifiant de l'objectif (sert à la déduplication)
     * @param label        libellé affichable
     * @param targetAmount montant visé ({@link BigDecimal#ZERO} si non défini)
     * @param allocations  parts de l'objectif alimentées par un placement
     */
    public record GoalCoverage(String id, String label, BigDecimal targetAmount, List<Allocation> allocations) {

        public GoalCoverage {
            if (label == null) label = "";
            if (targetAmount == null) targetAmount = BigDecimal.ZERO;
            if (allocations == null) allocations = Collections.emptyList();
        }
    }

    /**
     * Part d'un objectif alimentée par un placement.
     *
     * @param placementId identifiant du placement
     * @param amount      montant réservé à l'objectif ({@link BigDecimal#ZERO} si non défini)
     */
    public record Allocation(String placementId, BigDecimal amount) {

        public Allocation {
            if (amount == null) amount = BigDecimal.ZERO;
        }
    }
}
