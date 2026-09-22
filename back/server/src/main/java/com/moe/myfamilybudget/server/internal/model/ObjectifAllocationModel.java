package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Une part d'un objectif d'épargne alimentée par un compte donné : {@code placementId} référence
 * un {@link PlacementModel} (vue Patrimoine), {@code amount} est le montant de ce compte réservé à
 * l'objectif.
 *
 * Un objectif peut regrouper plusieurs allocations (un même objectif alimenté par plusieurs
 * comptes), et un même compte peut être référencé par plusieurs objectifs différents ; c'est la
 * validation appelante ({@code BudgetMutationService}) qui garantit que la somme des allocations
 * d'un compte, tous objectifs confondus, ne dépasse jamais son solde.
 *
 * Remplace, pour les nouveaux objectifs, le couple historique {@code sourcePlacementId}/
 * {@code allocatedAmount} porté par un seul compte (voir {@code LegacyObjectifAllocationMigrator}
 * pour la bascule en lecture des objectifs enregistrés avant cette évolution).
 */
public record ObjectifAllocationModel(
    String id,
    String placementId,
    BigDecimal amount
) {
    public BigDecimal getEffectiveAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
