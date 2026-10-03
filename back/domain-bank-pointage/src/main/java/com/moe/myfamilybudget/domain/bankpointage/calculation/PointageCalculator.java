package com.moe.myfamilybudget.domain.bankpointage.calculation;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.PointageLineStatusModel;
import com.moe.myfamilybudget.domain.bankpointage.model.PointageMonthSummaryModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.bankpointage.calculation.BudgetLineProjection;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageInput;

/**
 * Calculateur métier pour le domaine du pointage mensuel.
 * Isolé de toute API / DTO REST. Depuis RF-501, il ne connaît plus les charges, revenus,
 * placements ni paramètres : il opère sur les transactions, les liens de rapprochement et les
 * {@link BudgetLineProjection} déjà composées en amont (voir {@link PointageInput} et
 * {@code PointageInputFactory}).
 */
public final class PointageCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(PointageCalculator.class);

    private PointageCalculator() {
        // Utility class
    }

    /**
     * Filtre les transactions bancaires pour un mois donné.
     */
    public static List<BankImportModel.BankTransactionModel> filterTransactionsForMonth(List<BankImportModel.BankTransactionModel> transactions, String monthISO) {
        if (transactions == null || monthISO == null || monthISO.isBlank()) {
            return Collections.emptyList();
        }
        return transactions.stream()
                .filter(t -> t != null && t.date() != null && t.date().startsWith(monthISO))
                .collect(Collectors.toList());
    }

    /**
     * Calcule l'ensemble des identifiants de transactions pointées sur les lignes budgétaires actives.
     */
    public static Set<String> calculatePointedTxIds(BankImportModel.MatchingModel matching, List<BudgetLineProjection> activeLines) {
        return calculatePointedTxIds(matching != null ? matching.links() : null, activeLines);
    }

    /**
     * Variante sur les liens de rapprochement d'un mois (forme portée par {@link PointageInput}).
     */
    public static Set<String> calculatePointedTxIds(List<BankImportModel.MatchingLinkModel> links, List<BudgetLineProjection> activeLines) {
        if (links == null || activeLines == null) {
            return Collections.emptySet();
        }
        Set<String> activeLineIds = activeLines.stream()
                .map(BudgetLineProjection::id)
                .collect(Collectors.toSet());

        Set<String> pointedTxIds = new HashSet<>();
        for (BankImportModel.MatchingLinkModel link : links) {
            if (link != null && activeLineIds.contains(link.budgetLineId()) && link.txIds() != null) {
                pointedTxIds.addAll(link.txIds());
            }
        }
        return pointedTxIds;
    }

    /**
     * Résout le montant d'un identifiant de transaction composite ("txId" ou "txId#splitId").
     */
    public static BigDecimal resolveAmount(String refId, Map<String, BankImportModel.BankTransactionModel> txMap) {
        if (refId == null || refId.isBlank() || txMap == null) {
            return BigDecimal.ZERO;
        }
        int hashIdx = refId.indexOf('#');
        if (hashIdx >= 0) {
            String txId = refId.substring(0, hashIdx);
            String splitId = refId.substring(hashIdx + 1);
            BankImportModel.BankTransactionModel tx = txMap.get(txId);
            if (tx != null && tx.splits() != null) {
                for (BankImportModel.BankTransactionSplitModel split : tx.splits()) {
                    if (split != null && splitId.equals(split.id())) {
                        return split.amount() != null ? split.amount() : BigDecimal.ZERO;
                    }
                }
            }
            return BigDecimal.ZERO;
        } else {
            BankImportModel.BankTransactionModel tx = txMap.get(refId);
            return (tx != null && tx.amount() != null) ? tx.amount() : BigDecimal.ZERO;
        }
    }

    /**
     * Calcule le résumé bancaire mensuel (dépenses, revenus, transactions non pointées).
     */
    public static PointageMonthSummaryModel calculateMonthBankSummary(List<BankImportModel.BankTransactionModel> monthTxs, Set<String> pointedTxIds) {
        if (monthTxs == null) {
            return new PointageMonthSummaryModel(BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        }

        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;
        int unpointedCount = 0;
        BigDecimal unpointedExpenses = BigDecimal.ZERO;

        Set<String> pointedSet = pointedTxIds != null ? pointedTxIds : Collections.emptySet();

        for (BankImportModel.BankTransactionModel tx : monthTxs) {
            if (tx == null) continue;
            BigDecimal amt = tx.amount() != null ? tx.amount() : BigDecimal.ZERO;
            if (amt.compareTo(BigDecimal.ZERO) < 0) {
                totalExpenses = totalExpenses.add(amt.abs());
            } else {
                totalIncome = totalIncome.add(amt);
            }

            if (tx.splits() != null && !tx.splits().isEmpty()) {
                BigDecimal pointedSplitsSum = BigDecimal.ZERO;
                int pointedSplitsCount = 0;
                for (BankImportModel.BankTransactionSplitModel split : tx.splits()) {
                    if (split == null) continue;
                    String splitRef = tx.id() + "#" + split.id();
                    if (pointedSet.contains(splitRef) || pointedSet.contains(tx.id())) {
                        pointedSplitsSum = pointedSplitsSum.add(split.amount() != null ? split.amount() : BigDecimal.ZERO);
                        pointedSplitsCount++;
                    }
                }
                if (pointedSplitsCount < tx.splits().size() && !pointedSet.contains(tx.id())) {
                    unpointedCount++;
                    BigDecimal unpointedAmt = amt.subtract(pointedSplitsSum);
                    if (unpointedAmt.compareTo(BigDecimal.ZERO) < 0) {
                        unpointedExpenses = unpointedExpenses.add(unpointedAmt.abs());
                    }
                }
            } else {
                if (!pointedSet.contains(tx.id())) {
                    unpointedCount++;
                    if (amt.compareTo(BigDecimal.ZERO) < 0) {
                        unpointedExpenses = unpointedExpenses.add(amt.abs());
                    }
                }
            }
        }

        return new PointageMonthSummaryModel(
                totalExpenses.setScale(2, RoundingMode.HALF_UP),
                totalIncome.setScale(2, RoundingMode.HALF_UP),
                unpointedCount,
                unpointedExpenses.setScale(2, RoundingMode.HALF_UP)
        );
    }

    /**
     * Calcule le montant réel associé à chaque ligne budgétaire.
     */
    public static Map<String, BigDecimal> calculateRealByLine(
            List<BankImportModel.BankTransactionModel> transactions,
            BankImportModel.MatchingModel matching,
            List<BudgetLineProjection> activeLines) {
        return calculateRealByLine(transactions, matching != null ? matching.links() : null, activeLines);
    }

    /**
     * Variante sur les liens de rapprochement d'un mois (forme portée par {@link PointageInput}).
     */
    public static Map<String, BigDecimal> calculateRealByLine(
            List<BankImportModel.BankTransactionModel> transactions,
            List<BankImportModel.MatchingLinkModel> links,
            List<BudgetLineProjection> activeLines) {

        if (links == null || activeLines == null || transactions == null) {
            return Collections.emptyMap();
        }

        Map<String, String> lineKindMap = activeLines.stream()
                .collect(Collectors.toMap(BudgetLineProjection::id, BudgetLineProjection::kind, (k1, k2) -> k1));

        Map<String, BankImportModel.BankTransactionModel> txMap = transactions.stream()
                .filter(t -> t != null && t.id() != null)
                .collect(Collectors.toMap(BankImportModel.BankTransactionModel::id, t -> t, (t1, t2) -> t1));

        Map<String, BigDecimal> realByLine = new HashMap<>();

        for (BankImportModel.MatchingLinkModel link : links) {
            if (link == null || link.budgetLineId() == null || link.txIds() == null) continue;
            String kind = lineKindMap.getOrDefault(link.budgetLineId(), "charge");

            BigDecimal sum = BigDecimal.ZERO;
            for (String refId : link.txIds()) {
                BigDecimal amt = resolveAmount(refId, txMap);
                if (amt != null) {
                    if ("revenu".equalsIgnoreCase(kind)) {
                        sum = sum.add(amt);
                    } else {
                        sum = sum.add(amt.negate());
                    }
                }
            }
            realByLine.put(link.budgetLineId(), sum.setScale(2, RoundingMode.HALF_UP));
        }

        return realByLine;
    }

    /**
     * Évalue le statut de pointage pour une ligne budgétaire ("pending", "match", "economy", "over").
     */
    public static PointageLineStatusModel calculateLineStatus(
            BudgetLineProjection line,
            BankImportModel.MatchingModel matching,
            BigDecimal realAmount) {
        return calculateLineStatusFromLinks(line, matching != null ? matching.links() : null, realAmount);
    }

    private static PointageLineStatusModel calculateLineStatusFromLinks(
            BudgetLineProjection line,
            List<BankImportModel.MatchingLinkModel> links,
            BigDecimal realAmount) {

        if (line == null) {
            throw new IllegalArgumentException("La ligne budgétaire ne peut pas être nulle.");
        }

        BankImportModel.MatchingLinkModel link = null;
        if (links != null) {
            link = links.stream()
                    .filter(l -> l != null && line.id().equals(l.budgetLineId()))
                    .findFirst()
                    .orElse(null);
        }

        if (link == null || link.txIds() == null || link.txIds().isEmpty()) {
            return new PointageLineStatusModel(line.id(), "pending", line.monthly(), BigDecimal.ZERO, line.monthly());
        }

        BigDecimal reel = realAmount != null ? realAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal prevu = line.monthly() != null ? line.monthly().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal diff = reel.subtract(prevu).abs();

        BigDecimal tolerance = prevu.multiply(new BigDecimal("0.02")).max(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);

        String status;
        if (diff.compareTo(tolerance) <= 0) {
            status = "match";
        } else if ("revenu".equalsIgnoreCase(line.kind()) || "placement".equalsIgnoreCase(line.kind())) {
            status = reel.compareTo(prevu) > 0 ? "economy" : "over";
        } else {
            status = reel.compareTo(prevu) < 0 ? "economy" : "over";
        }

        return new PointageLineStatusModel(line.id(), status, prevu, reel, reel.subtract(prevu));
    }

    // --- Points d'entrée sur PointageInput (RF-501) ---

    /**
     * Transactions de {@code input} appartenant au mois pointé.
     */
    public static List<BankImportModel.BankTransactionModel> filterTransactionsForMonth(PointageInput input) {
        if (input == null) {
            return Collections.emptyList();
        }
        return filterTransactionsForMonth(input.transactions(), input.period().monthISO());
    }

    /**
     * Identifiants de transactions pointées sur les lignes budgétaires actives de {@code input}.
     */
    public static Set<String> calculatePointedTxIds(PointageInput input) {
        if (input == null) {
            return Collections.emptySet();
        }
        return calculatePointedTxIds(input.matchings(), input.activeBudgetLines());
    }

    /**
     * Montant réel associé à chaque ligne budgétaire active de {@code input}.
     */
    public static Map<String, BigDecimal> calculateRealByLine(PointageInput input) {
        if (input == null) {
            return Collections.emptyMap();
        }
        return calculateRealByLine(input.transactions(), input.matchings(), input.activeBudgetLines());
    }

    /**
     * Résumé bancaire du mois pointé de {@code input}.
     */
    public static PointageMonthSummaryModel calculateMonthBankSummary(PointageInput input) {
        if (input == null) {
            return calculateMonthBankSummary(null, null);
        }
        return calculateMonthBankSummary(filterTransactionsForMonth(input), calculatePointedTxIds(input));
    }

    /**
     * Statut de pointage d'une ligne budgétaire de {@code input}.
     */
    public static PointageLineStatusModel calculateLineStatus(BudgetLineProjection line, PointageInput input, BigDecimal realAmount) {
        return calculateLineStatusFromLinks(line, input != null ? input.matchings() : null, realAmount);
    }

    /**
     * Met à jour la liste des rapprochements (matchings) avec les nouveaux liens pour un mois donné.
     */
    public static BankImportModel updateMatchingForMonth(
            BankImportModel currentImport,
            String monthISO,
            List<BankImportModel.MatchingLinkModel> newLinks) {

        if (monthISO == null || monthISO.isBlank()) {
            throw new IllegalArgumentException("Le mois ISO est obligatoire pour enregistrer un pointage.");
        }

        List<BankImportModel.MatchingModel> currentMatchings = currentImport != null && currentImport.matchings() != null
                ? currentImport.matchings()
                : Collections.emptyList();

        List<BankImportModel.MatchingModel> others = currentMatchings.stream()
                .filter(m -> m != null && !monthISO.equals(m.month()))
                .collect(Collectors.toList());

        List<BankImportModel.MatchingModel> updatedMatchings = new ArrayList<>(others);
        updatedMatchings.add(new BankImportModel.MatchingModel(monthISO, newLinks != null ? newLinks : Collections.emptyList()));

        BankImportModel base = currentImport != null ? currentImport : new BankImportModel(null, null, null);
        return new BankImportModel(
                base.columnMapping(),
                base.categories(),
                base.rules(),
                base.transactions(),
                base.pendingOperations(),
                updatedMatchings
        );
    }
}
