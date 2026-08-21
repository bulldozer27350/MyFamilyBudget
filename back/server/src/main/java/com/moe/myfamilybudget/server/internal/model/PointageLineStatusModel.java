package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Modèle de domaine pur pour le statut de rapprochement d'une ligne budgétaire.
 */
public record PointageLineStatusModel(
        String budgetLineId,
        String status, // "pending", "match", "economy", "over"
        BigDecimal expectedAmount,
        BigDecimal realAmount,
        BigDecimal difference
) {
    public PointageLineStatusModel {
        if (budgetLineId == null) budgetLineId = "";
        if (status == null) status = "pending";
        if (expectedAmount == null) expectedAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        else expectedAmount = expectedAmount.setScale(2, RoundingMode.HALF_UP);

        if (realAmount == null) realAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        else realAmount = realAmount.setScale(2, RoundingMode.HALF_UP);

        if (difference == null) difference = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        else difference = difference.setScale(2, RoundingMode.HALF_UP);
    }
}
