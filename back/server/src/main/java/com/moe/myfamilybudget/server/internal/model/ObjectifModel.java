package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record ObjectifModel(
    String id,
    String label,
    BigDecimal targetAmount,
    // Champs historiques : conservés uniquement pour LegacyObjectifAllocationMigrator (bascule en
    // lecture des objectifs "ancien format", mono-compte). Un objectif au nouveau format porte son
    // financement dans `allocations` et laisse ces deux champs vides.
    BigDecimal allocatedAmount,
    String targetDate,
    String sourcePlacementId,
    String notes,
    List<ObjectifAllocationModel> allocations
) {
    // Compatibilité ascendante : appelants antérieurs à allocatedAmount ET aux allocations
    // multi-comptes. null/vide => comportement historique (100% du solde du compte support compte
    // pour l'objectif).
    public ObjectifModel(String id, String label, BigDecimal targetAmount, String targetDate,
                          String sourcePlacementId, String notes) {
        this(id, label, targetAmount, null, targetDate, sourcePlacementId, notes, List.of());
    }

    // Compatibilité ascendante : appelants antérieurs à l'introduction des allocations
    // multi-comptes (connaissent déjà allocatedAmount/sourcePlacementId).
    public ObjectifModel(String id, String label, BigDecimal targetAmount, BigDecimal allocatedAmount,
                          String targetDate, String sourcePlacementId, String notes) {
        this(id, label, targetAmount, allocatedAmount, targetDate, sourcePlacementId, notes, List.of());
    }

    public BigDecimal getEffectiveTargetAmount() {
        return targetAmount != null ? targetAmount : BigDecimal.ZERO;
    }

    public List<ObjectifAllocationModel> getEffectiveAllocations() {
        return allocations != null ? allocations : List.of();
    }
}
