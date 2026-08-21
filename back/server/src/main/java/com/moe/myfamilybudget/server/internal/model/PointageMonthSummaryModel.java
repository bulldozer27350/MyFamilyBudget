package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Modèle de domaine pur pour la synthèse des opérations bancaires d'un mois.
 */
public record PointageMonthSummaryModel(
        BigDecimal totalBankExpenses,
        BigDecimal totalBankIncome,
        int unpointedCount,
        BigDecimal unpointedExpenses
) {
    public PointageMonthSummaryModel {
        if (totalBankExpenses == null) totalBankExpenses = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        else totalBankExpenses = totalBankExpenses.setScale(2, RoundingMode.HALF_UP);

        if (totalBankIncome == null) totalBankIncome = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        else totalBankIncome = totalBankIncome.setScale(2, RoundingMode.HALF_UP);

        if (unpointedExpenses == null) unpointedExpenses = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        else unpointedExpenses = unpointedExpenses.setScale(2, RoundingMode.HALF_UP);
    }
}
