package com.moe.myfamilybudget.domain.analysis.calculation;

import java.math.BigDecimal;

/**
 * Opération en cours telle que le silo Analyse la lit (SILO-133) : aucun type du silo Banque/Pointage.
 *
 * @param date         date au format ISO {@code YYYY-MM-DD}, chaîne vide si inconnue
 * @param amount       montant signé de l'opération
 * @param status       statut ({@code "pending"} par défaut)
 * @param budgetLineId ligne budgétaire visée, chaîne vide si aucune
 */
public record AnalysisPendingOperation(String date, BigDecimal amount, String status, String budgetLineId) {

    public AnalysisPendingOperation {
        if (date == null) date = "";
        if (amount == null) amount = BigDecimal.ZERO;
        if (status == null) status = "pending";
        if (budgetLineId == null) budgetLineId = "";
    }
}
