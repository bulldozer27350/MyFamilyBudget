package com.moe.myfamilybudget.server.internal.notification.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.BankTransactionModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.PendingOperationModel;
import com.moe.myfamilybudget.server.internal.notification.NotificationContext;
import com.moe.myfamilybudget.server.internal.notification.NotificationMessage;
import com.moe.myfamilybudget.server.internal.notification.NotificationRule;

/**
 * Signale un solde du compte courant passé sous le seuil configuré.
 *
 * Le compte courant n'est pas un {@code PlacementModel} (ce sont les livrets/épargne) : son solde
 * est reconstitué à partir du solde de départ des paramètres généraux
 * ({@code SettingsModel.getEffectiveStartBalance()}) auquel s'appliquent les transactions
 * bancaires importées ({@code BankImportModel.transactions}) et les opérations en cours
 * ("pointage") encore réellement en attente ({@code PendingOperationModel} de statut
 * {@code "pending"}).
 *
 * Une opération en cours de statut {@code "cleared"} (liée à une transaction déjà importée via
 * {@code linkedTxId}) est volontairement exclue de la somme : elle est déjà comptée via la
 * transaction importée correspondante, l'inclure en plus provoquerait un double comptage.
 *
 * Règle globale sans sous-entité : un seul message possible par contrôle, clé de déduplication
 * = "balance-floor" (voir {@link NotificationMessage#dedupKey()}).
 */
@Component
public class BalanceFloorRule implements NotificationRule {

    public static final String KEY = "balance-floor";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<NotificationMessage> check(NotificationContext context) {
        BigDecimal floor = context.settings().balanceFloorAmount();
        if (floor == null) {
            return List.of();
        }
        BigDecimal balance = computeCompteCourantBalance(context);
        if (balance.compareTo(floor) >= 0) {
            return List.of();
        }
        return List.of(new NotificationMessage(KEY, null, "Solde du compte courant sous le seuil",
                String.format("Solde du compte courant : %s € (seuil configuré : %s €)",
                        balance.toPlainString(), floor.toPlainString())));
    }

    private static BigDecimal computeCompteCourantBalance(NotificationContext context) {
        BigDecimal balance = context.data().getEffectiveSettings().getEffectiveStartBalance();

        BankImportModel bankImport = context.data().bankImport();
        if (bankImport == null) {
            return balance;
        }

        if (bankImport.transactions() != null) {
            balance = balance.add(bankImport.transactions().stream()
                    .map(BankTransactionModel::amount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        if (bankImport.pendingOperations() != null) {
            balance = balance.add(bankImport.pendingOperations().stream()
                    .filter(op -> "pending".equalsIgnoreCase(op.status()))
                    .map(PendingOperationModel::amount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        return balance;
    }
}
