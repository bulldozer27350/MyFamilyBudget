package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Entrée de la règle de notification « solde sous le seuil » (RF-701, voir
 * doc/architecture/09-domaine-objectifs-notifications.md).
 *
 * <p>Une des trois entrées distinctes des notifications. Le solde du compte courant se reconstitue
 * à partir du solde de départ, des transactions importées et des opérations en cours ; la règle
 * ne reçoit que ces montants.
 *
 * <p>L'assembleur fournit la liste brute des opérations en cours : c'est la règle qui ne retient
 * que celles dont le statut est {@code "pending"} (une opération {@code "cleared"} est déjà
 * comptée via la transaction importée correspondante).
 *
 * @param floor                seuil configuré, {@code null} si la règle n'est pas paramétrée
 * @param openingBalance       solde de départ du compte courant ({@link BigDecimal#ZERO} si inconnu)
 * @param importedTransactions montants des transactions bancaires importées
 * @param pendingOperations    opérations en cours (statut et montant)
 */
public record BalanceFloorInput(
        BigDecimal floor,
        BigDecimal openingBalance,
        List<AccountTransactionAmount> importedTransactions,
        List<PendingAmount> pendingOperations) {

    public BalanceFloorInput {
        if (openingBalance == null) openingBalance = BigDecimal.ZERO;
        if (importedTransactions == null) importedTransactions = Collections.emptyList();
        if (pendingOperations == null) pendingOperations = Collections.emptyList();
    }

    /**
     * Montant d'une transaction importée.
     *
     * @param amount montant signé, {@code null} si absent
     */
    public record AccountTransactionAmount(BigDecimal amount) {
    }

    /**
     * Opération en cours (pointage) réduite à son statut et son montant.
     *
     * @param status statut de l'opération ({@code "pending"} ou {@code "cleared"})
     * @param amount montant signé, {@code null} si absent
     */
    public record PendingAmount(String status, BigDecimal amount) {
    }
}
