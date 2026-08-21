package com.moe.myfamilybudget.server.internal.model;

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

/**
 * Calculateur métier pour le domaine du pointage mensuel.
 * Isolé de toute API / DTO REST. Opère exclusivement sur les modèles du domaine interne.
 */
public final class PointageCalculator {

    private PointageCalculator() {
        // Utility class
    }

    /**
     * Extrait les lignes de budget actives pour un mois donné (format YYYY-MM).
     */
    public static List<PointageBudgetLineModel> calculateActiveBudgetLines(PointageModel model, String monthISO) {
        if (model == null || monthISO == null || monthISO.isBlank()) {
            return Collections.emptyList();
        }

        int year = parseYearFromMonthISO(monthISO);
        BigDecimal inflationRate = model.settings() != null ? model.settings().getEffectiveInflationRate() : new BigDecimal("0.02");

        List<PointageBudgetLineModel> activeLines = new ArrayList<>();

        // 1. Charges
        if (model.charges() != null) {
            for (ChargeModel c : model.charges()) {
                if (c == null) continue;
                boolean startOK = c.start() == null || c.start().isBlank() || monthISO.compareTo(toMonthISO(c.start())) >= 0;
                boolean endOK = c.end() == null || c.end().isBlank() || monthISO.compareTo(toMonthISO(c.end())) <= 0;
                if (!startOK || !endOK) continue;

                BigDecimal monthly = calculateChargeMonthly(c, year, inflationRate);
                if (monthly.compareTo(BigDecimal.ZERO) > 0) {
                    activeLines.add(new PointageBudgetLineModel(c.id(), c.label(), "charge", monthly, c.categoryId()));
                }
            }
        }

        // 2. Incomes (Revenus)
        if (model.incomes() != null) {
            for (IncomeModel inc : model.incomes()) {
                if (inc == null) continue;
                boolean startOK = inc.start() == null || inc.start().isBlank() || monthISO.compareTo(toMonthISO(inc.start())) >= 0;
                boolean endOK = inc.end() == null || inc.end().isBlank() || monthISO.compareTo(toMonthISO(inc.end())) <= 0;
                if (!startOK || !endOK) continue;

                BigDecimal monthly = calculateIncomeMonthly(inc, year);
                if (monthly.compareTo(BigDecimal.ZERO) > 0) {
                    activeLines.add(new PointageBudgetLineModel(inc.id(), inc.label(), "revenu", monthly, inc.categoryId()));
                }
            }
        }

        // 3. Placements (Épargne)
        if (model.placements() != null) {
            for (PlacementModel p : model.placements()) {
                if (p == null) continue;
                BigDecimal m = p.monthly() != null ? p.monthly() : BigDecimal.ZERO;
                if (m.compareTo(BigDecimal.ZERO) <= 0) continue;

                boolean fromOK = p.monthlyFrom() == null || p.monthlyFrom().isBlank() || monthISO.compareTo(toMonthISO(p.monthlyFrom())) >= 0;
                boolean untilOK = p.monthlyUntil() == null || p.monthlyUntil().isBlank() || monthISO.compareTo(toMonthISO(p.monthlyUntil())) <= 0;
                if (!fromOK || !untilOK) continue;

                activeLines.add(new PointageBudgetLineModel(p.id(), "Épargne : " + p.label(), "placement", m, p.category()));
            }
        }

        return activeLines;
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
    public static Set<String> calculatePointedTxIds(BankImportModel.MatchingModel matching, List<PointageBudgetLineModel> activeLines) {
        if (matching == null || matching.links() == null || activeLines == null) {
            return Collections.emptySet();
        }
        Set<String> activeLineIds = activeLines.stream()
                .map(PointageBudgetLineModel::id)
                .collect(Collectors.toSet());

        Set<String> pointedTxIds = new HashSet<>();
        for (BankImportModel.MatchingLinkModel link : matching.links()) {
            if (link != null && activeLineIds.contains(link.budgetLineId()) && link.txIds() != null) {
                pointedTxIds.addAll(link.txIds());
            }
        }
        return pointedTxIds;
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

            if (!pointedSet.contains(tx.id())) {
                unpointedCount++;
                if (amt.compareTo(BigDecimal.ZERO) < 0) {
                    unpointedExpenses = unpointedExpenses.add(amt.abs());
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
            List<PointageBudgetLineModel> activeLines) {

        if (matching == null || matching.links() == null || activeLines == null || transactions == null) {
            return Collections.emptyMap();
        }

        Map<String, String> lineKindMap = activeLines.stream()
                .collect(Collectors.toMap(PointageBudgetLineModel::id, PointageBudgetLineModel::kind, (k1, k2) -> k1));

        Map<String, BankImportModel.BankTransactionModel> txMap = transactions.stream()
                .filter(t -> t != null && t.id() != null)
                .collect(Collectors.toMap(BankImportModel.BankTransactionModel::id, t -> t, (t1, t2) -> t1));

        Map<String, BigDecimal> realByLine = new HashMap<>();

        for (BankImportModel.MatchingLinkModel link : matching.links()) {
            if (link == null || link.budgetLineId() == null || link.txIds() == null) continue;
            String kind = lineKindMap.getOrDefault(link.budgetLineId(), "charge");

            BigDecimal sum = BigDecimal.ZERO;
            for (String txId : link.txIds()) {
                BankImportModel.BankTransactionModel tx = txMap.get(txId);
                if (tx != null && tx.amount() != null) {
                    if ("revenu".equalsIgnoreCase(kind)) {
                        sum = sum.add(tx.amount());
                    } else {
                        sum = sum.add(tx.amount().negate());
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
            PointageBudgetLineModel line,
            BankImportModel.MatchingModel matching,
            BigDecimal realAmount) {

        if (line == null) {
            throw new IllegalArgumentException("La ligne budgétaire ne peut pas être nulle.");
        }

        BankImportModel.MatchingLinkModel link = null;
        if (matching != null && matching.links() != null) {
            link = matching.links().stream()
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

    // --- Helpers de calcul internes ---

    private static int parseYearFromMonthISO(String monthISO) {
        try {
            return Integer.parseInt(monthISO.substring(0, 4));
        } catch (Exception e) {
            return 2026;
        }
    }

    private static String toMonthISO(String dateStr) {
        if (dateStr == null || dateStr.length() < 7) return "";
        return dateStr.substring(0, 7);
    }

    private static BigDecimal calculateChargeMonthly(ChargeModel c, int year, BigDecimal inflationRate) {
        Integer sY = c.start() != null && !c.start().isBlank() ? parseYearFromMonthISO(c.start()) : null;
        Integer eY = c.end() != null && !c.end().isBlank() ? parseYearFromMonthISO(c.end()) : null;
        if (sY != null && eY != null && (year < sY || year > eY)) {
            return BigDecimal.ZERO;
        }

        BigDecimal growth = (c.growthRate() != null && BigDecimal.ZERO.compareTo(c.growthRate()) != 0)
                ? c.growthRate()
                : (inflationRate != null ? inflationRate : new BigDecimal("0.015"));

        int elapsed = sY != null ? Math.max(0, year - sY) : 0;
        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);

        BigDecimal baseMonthly = c.getEffectiveMonthly();
        return baseMonthly.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateIncomeMonthly(IncomeModel inc, int year) {
        Integer sY = inc.start() != null && !inc.start().isBlank() ? parseYearFromMonthISO(inc.start()) : null;
        Integer eY = inc.end() != null && !inc.end().isBlank() ? parseYearFromMonthISO(inc.end()) : null;
        if (sY != null && eY != null && (year < sY || year > eY)) {
            return BigDecimal.ZERO;
        }

        int elapsed = sY != null ? Math.max(0, year - sY) : 0;
        double factor = Math.pow(1.0 + inc.getEffectiveGrowthRate().doubleValue(), elapsed);

        BigDecimal baseMonthly = inc.getEffectiveMonthly();
        return baseMonthly.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }
}
