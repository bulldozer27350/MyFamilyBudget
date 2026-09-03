package com.moe.myfamilybudget.server.internal.model;

import java.util.Collections;
import java.util.List;

/**
 * Résultat du rapprochement automatique.
 *
 * @param matchCount          Nombre d'opérations en attente effectivement rapprochées.
 * @param updatedOperations   Liste complète (mise à jour) des opérations en attente.
 * @param updatedTransactions Liste complète (mise à jour) des transactions bancaires. Seules les
 *                            transactions dont la catégorisation (categoryId/splits) a été alignée
 *                            sur celle de l'opération en attente correspondante sont modifiées.
 * @param needsReviewCount    Nombre de paires candidates détectées par le rapprochement automatique
 *                            mais volontairement NON rapprochées car les catégorisations de
 *                            l'opération en attente et de la transaction bancaire diffèrent. Ces
 *                            opérations restent "en attente" et doivent être traitées manuellement
 *                            via le rapprochement manuel (qui proposera un choix à l'opérateur).
 */
public record AutoMatchResultModel(
        int matchCount,
        List<BankImportModel.PendingOperationModel> updatedOperations,
        List<BankImportModel.BankTransactionModel> updatedTransactions,
        int needsReviewCount
) {
    public AutoMatchResultModel {
        if (updatedOperations == null) updatedOperations = Collections.emptyList();
        if (updatedTransactions == null) updatedTransactions = Collections.emptyList();
    }

    /**
     * Constructeur de compatibilité (ancienne signature à 2 champs, sans transactions mises à jour
     * ni décompte des opérations nécessitant une revue manuelle).
     */
    public AutoMatchResultModel(int matchCount, List<BankImportModel.PendingOperationModel> updatedOperations) {
        this(matchCount, updatedOperations, Collections.emptyList(), 0);
    }
}
