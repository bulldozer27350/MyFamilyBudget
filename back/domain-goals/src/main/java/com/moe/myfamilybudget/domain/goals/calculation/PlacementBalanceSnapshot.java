package com.moe.myfamilybudget.domain.goals.calculation;

import java.math.BigDecimal;

/**
 * Solde courant d'un placement, sous une forme neutre (RF-700, voir
 * doc/architecture/09-domaine-objectifs-notifications.md).
 *
 * <p>Une allocation d'objectif désigne un placement par son identifiant ; le domaine Objectifs
 * n'a besoin que de ce couple, pas de {@code PlacementModel} complet. Ce contrat évite une
 * dépendance forte Objectifs → Patrimoine.
 *
 * @param placementId identifiant du placement
 * @param balance     solde courant du placement ({@link BigDecimal#ZERO} si inconnu)
 */
public record PlacementBalanceSnapshot(String placementId, BigDecimal balance) {

    public PlacementBalanceSnapshot {
        if (placementId == null) placementId = "";
        if (balance == null) balance = BigDecimal.ZERO;
    }
}
