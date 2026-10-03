package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifReachableInput;

/**
 * Entrée de la règle de notification « débit important » (RF-701, voir
 * doc/architecture/09-domaine-objectifs-notifications.md).
 *
 * <p>Une des trois entrées distinctes des notifications : elle ne porte que ce dont la règle a
 * besoin (un seuil et des transactions), jamais {@code BudgetDataModel}. Ne pas la fusionner avec
 * {@link BalanceFloorInput} ou {@link ObjectifReachableInput} dans une entrée d'évaluation unique.
 *
 * <p>Le filtrage par ancienneté (fenêtre de transactions « récentes ») reste de la responsabilité
 * de la règle ; ce contrat transporte les transactions telles que l'assembleur les fournit.
 *
 * @param threshold          seuil de débit configuré, {@code null} si la règle n'est pas paramétrée
 * @param recentTransactions transactions bancaires à examiner
 */
public record DebitThresholdInput(BigDecimal threshold, List<Transaction> recentTransactions) {

    public DebitThresholdInput {
        if (recentTransactions == null) recentTransactions = Collections.emptyList();
    }

    /**
     * Transaction bancaire réduite aux champs utiles à la règle.
     *
     * @param id     identifiant de la transaction (sert à la déduplication)
     * @param date   date au format ISO {@code YYYY-MM-DD}, telle que fournie par la source
     * @param label  libellé de la transaction
     * @param amount montant signé (négatif pour un débit)
     */
    public record Transaction(String id, String date, String label, BigDecimal amount) {
    }
}
