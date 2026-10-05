package com.moe.myfamilybudget.domain.bankpointage.calculation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.PointageLineStatusModel;
import com.moe.myfamilybudget.domain.bankpointage.model.PointageMonthSummaryModel;

/**
 * Interface de service du silo Banque/Pointage (pointage mensuel) (SILO-154, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur pour les consommateurs ({@code application}, {@code server}, tests) : ils ne
 * connaissent ni son implémentation ({@code DefaultPointageCalculationService}, dans {@code bank-pointage-core}), ni son câblage (déclaré
 * par le composition root). Elle ne reçoit que des modèles et contrats du silo : aucun {@code BudgetDataModel}, aucun
 * DTO REST.
 */
public interface PointageCalculationService {

    /** Filtre les transactions d'un mois donné. */
    List<BankImportModel.BankTransactionModel> filterTransactionsForMonth(List<BankImportModel.BankTransactionModel> transactions, String monthISO);

    /** Identifiants des transactions pointées par les liens de rapprochement. */
    Set<String> calculatePointedTxIds(BankImportModel.MatchingModel matching, List<BudgetLineProjection> activeLines);

    /** Identifiants des transactions pointées par les liens de rapprochement. */
    Set<String> calculatePointedTxIds(List<BankImportModel.MatchingLinkModel> links, List<BudgetLineProjection> activeLines);

    /** Montant d'une référence {@code txId} ou {@code txId#splitId} (ou {@code null} si inconnue). */
    BigDecimal resolveAmount(String refId, Map<String, BankImportModel.BankTransactionModel> txMap);

    /** Synthèse bancaire d'un mois (pointé, non pointé). */
    PointageMonthSummaryModel calculateMonthBankSummary(List<BankImportModel.BankTransactionModel> monthTxs, Set<String> pointedTxIds);

    /** Montant réel rapproché par ligne budgétaire. */
    Map<String, BigDecimal> calculateRealByLine(List<BankImportModel.BankTransactionModel> transactions, BankImportModel.MatchingModel matching, List<BudgetLineProjection> activeLines);

    /** Montant réel rapproché par ligne budgétaire. */
    Map<String, BigDecimal> calculateRealByLine(List<BankImportModel.BankTransactionModel> transactions, List<BankImportModel.MatchingLinkModel> links, List<BudgetLineProjection> activeLines);

    /** Statut de pointage d'une ligne budgétaire. */
    PointageLineStatusModel calculateLineStatus(BudgetLineProjection line, BankImportModel.MatchingModel matching, BigDecimal realAmount);

    /** Filtre les transactions d'un mois donné. */
    List<BankImportModel.BankTransactionModel> filterTransactionsForMonth(PointageInput input);

    /** Identifiants des transactions pointées par les liens de rapprochement. */
    Set<String> calculatePointedTxIds(PointageInput input);

    /** Montant réel rapproché par ligne budgétaire. */
    Map<String, BigDecimal> calculateRealByLine(PointageInput input);

    /** Synthèse bancaire d'un mois (pointé, non pointé). */
    PointageMonthSummaryModel calculateMonthBankSummary(PointageInput input);

    /** Statut de pointage d'une ligne budgétaire. */
    PointageLineStatusModel calculateLineStatus(BudgetLineProjection line, PointageInput input, BigDecimal realAmount);

    /** Remplace les liens de rapprochement d'un mois dans l'import courant. */
    BankImportModel updateMatchingForMonth(BankImportModel currentImport, String monthISO, List<BankImportModel.MatchingLinkModel> newLinks);
}
