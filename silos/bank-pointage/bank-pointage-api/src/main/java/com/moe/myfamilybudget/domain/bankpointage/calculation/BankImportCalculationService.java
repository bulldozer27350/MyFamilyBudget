package com.moe.myfamilybudget.domain.bankpointage.calculation;

import java.math.BigDecimal;
import java.util.List;

import com.moe.myfamilybudget.domain.bankpointage.model.AutoMatchResultModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportSummaryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.CategorizeResultModel;
import com.moe.myfamilybudget.domain.bankpointage.model.PendingImportSummaryModel;

/**
 * Interface de service du silo Banque (import bancaire, opérations en cours, règles de catégorisation) (SILO-154, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur pour les consommateurs ({@code application}, {@code server}, tests) : ils ne
 * connaissent ni son implémentation ({@code DefaultBankImportCalculationService}, dans {@code bank-pointage-core}), ni son câblage (déclaré
 * par le composition root). Elle ne reçoit que des modèles et contrats du silo : aucun {@code BudgetDataModel}, aucun
 * DTO REST.
 */
public interface BankImportCalculationService {

    /** Découpe un texte CSV en lignes de cellules selon le séparateur. */
    List<List<String>> parseCSVText(String text, String delimiter);

    /** Convertit une date saisie selon le format indiqué en date ISO (ou {@code null} si illisible). */
    String parseDateWithFormat(String str, String format);

    /** Convertit un montant saisi (formats français ou anglais) en {@link BigDecimal}. */
    BigDecimal parseAmountText(String str);

    /** Clé de déduplication d'une transaction (date, libellé, montant). */
    String transactionDedupeKey(String date, String label, BigDecimal amount);

    /** Clé de règle de catégorisation dérivée d'un libellé. */
    String ruleKeyFromLabel(String label);

    /** Applique les règles de catégorisation aux transactions. */
    List<BankImportModel.BankTransactionModel> applyRulesToTransactions(List<BankImportModel.BankTransactionModel> transactions, List<BankImportModel.BankImportRuleModel> rules);

    /** Applique les règles de catégorisation aux opérations en cours. */
    List<BankImportModel.PendingOperationModel> applyRulesToPendingOperations(List<BankImportModel.PendingOperationModel> operations, List<BankImportModel.BankImportRuleModel> rules);

    /** Importe des lignes brutes en transactions, en écartant les doublons. */
    BankImportSummaryModel importTransactions(List<List<String>> rawRows, List<String> colRoles, BankImportModel.BankColumnMappingModel mapping, List<BankImportModel.BankTransactionModel> existingTxs, List<BankImportModel.BankImportRuleModel> rules);

    /** Indique si deux catégorisations (catégorie et ventilations) sont équivalentes. */
    boolean categorizationsMatch(String categoryIdA, List<BankImportModel.BankTransactionSplitModel> splitsA, String categoryIdB, List<BankImportModel.BankTransactionSplitModel> splitsB);

    /** Remet des ventilations à l'échelle d'un montant cible. */
    List<BankImportModel.BankTransactionSplitModel> rescaleSplitsToAmount(List<BankImportModel.BankTransactionSplitModel> splits, BigDecimal targetAmount);

    /** Rapproche automatiquement les opérations en cours des transactions importées. */
    AutoMatchResultModel autoMatchPendingOperations(List<BankImportModel.PendingOperationModel> pendingOps, List<BankImportModel.BankTransactionModel> transactions);

    /** Extrait la date d'achat présente dans un libellé de carte (ou {@code null}). */
    String parsePurchaseDateFromLabel(String label);

    /** Importe les opérations carte en cours à partir de lignes brutes. */
    PendingImportSummaryModel importPendingCB(List<List<String>> rawRows, List<String> colRoles, String dateFormat, boolean usePurchaseDate, List<BankImportModel.PendingOperationModel> existingOps, List<BankImportModel.BankImportRuleModel> rules);

    /** Fusionne une opération manuelle avec l'opération bancaire correspondante. */
    List<BankImportModel.PendingOperationModel> mergePendingOperation(String manualOpId, BankImportModel.PendingOperationModel bankOp, List<BankImportModel.PendingOperationModel> existingOps);

    /** Catégorise une transaction et, si demandé, mémorise la règle associée. */
    CategorizeResultModel categorizeTransaction(String txId, String categoryId, String ruleKeyword, List<BankImportModel.BankTransactionModel> transactions, List<BankImportModel.BankImportRuleModel> rules);
}
