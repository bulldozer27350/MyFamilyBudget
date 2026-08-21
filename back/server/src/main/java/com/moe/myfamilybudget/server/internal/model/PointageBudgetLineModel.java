package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Modèle de domaine pur représentant une ligne de budget active pour un mois de pointage.
 */
public record PointageBudgetLineModel(
        String id,
        String label,
        String kind, // "charge", "revenu", "placement"
        BigDecimal monthly,
        String categoryId
) {
    public PointageBudgetLineModel {
        if (id == null) id = "";
        if (label == null) label = "";
        if (kind == null) kind = "charge";
        if (monthly == null) {
            monthly = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            monthly = monthly.setScale(2, RoundingMode.HALF_UP);
        }
        if (categoryId == null) categoryId = "";
    }
}
