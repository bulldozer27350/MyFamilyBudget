package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record ObjectifModel(
    String id,
    String label,
    BigDecimal targetAmount,
    BigDecimal allocatedAmount,
    String targetDate,
    String sourcePlacementId,
    String notes
) {
    // Compatibilité ascendante : anciens appelants ne connaissant pas allocatedAmount.
    // null => comportement historique (100% du solde du compte support compte pour l'objectif).
    public ObjectifModel(String id, String label, BigDecimal targetAmount, String targetDate,
                          String sourcePlacementId, String notes) {
        this(id, label, targetAmount, null, targetDate, sourcePlacementId, notes);
    }

    public BigDecimal getEffectiveTargetAmount() {
        return targetAmount != null ? targetAmount : BigDecimal.ZERO;
    }
}
