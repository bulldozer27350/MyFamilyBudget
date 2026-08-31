package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calculateur métier pour l'analyse Réel vs Prévisionnel et dérives.
 * Isolé de toute API REST ou DTO. Opère exclusivement sur le domaine interne.
 */
public final class AnalyseCalculator {

    private AnalyseCalculator() {
        // Utility class
    }

    public static AnalyseResultModel computeAnalyse(BudgetDataModel data, BankImportModel bankImport, Integer monthsBack) {
        if (data == null) {
            data = new BudgetDataModel(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
        if (bankImport == null) {
            bankImport = data.bankImport() != null ? data.bankImport() : new BankImportModel(null, null, null, null, null, null);
        } else if (data.bankImport() == null) {
            data = data.withBankImport(bankImport);
        }

        int mBack = (monthsBack != null && monthsBack >= 0) ? monthsBack : 12;
        String cutoffISO = null;
        if (mBack > 0) {
            cutoffISO = LocalDate.now().minusMonths(mBack).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        List<BankImportModel.BankTransactionModel> allTx = bankImport.transactions() != null ? bankImport.transactions() : Collections.emptyList();
        final String finalCutoff = cutoffISO;
        List<BankImportModel.BankTransactionModel> periodTx = allTx.stream()
                .filter(t -> t != null && t.date() != null && (finalCutoff == null || t.date().compareTo(finalCutoff) >= 0))
                .collect(Collectors.toList());

        List<BankImportModel.CategoryModel> categories = bankImport.categories() != null ? bankImport.categories() : Collections.emptyList();
        Map<String, BankImportModel.CategoryModel> catById = new HashMap<>();
        for (BankImportModel.CategoryModel c : categories) {
            if (c.id() != null) {
                catById.put(c.id(), c);
            }
        }

        // 1. Category summaries (byCategory)
        Map<String, BigDecimal> expenseByCat = new HashMap<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        int uncategorizedCount = 0;
        BigDecimal compressibleTotal = BigDecimal.ZERO;

        Set<String> compressibleCatIds = categories.stream()
                .filter(c -> "Oui".equalsIgnoreCase(c.compressible()))
                .map(BankImportModel.CategoryModel::id)
                .collect(Collectors.toSet());

        for (BankImportModel.BankTransactionModel t : periodTx) {
            if (t.splits() != null && !t.splits().isEmpty()) {
                for (BankImportModel.BankTransactionSplitModel split : t.splits()) {
                    BigDecimal amt = split.amount() != null ? split.amount() : BigDecimal.ZERO;
                    String splitCatId = split.categoryId();
                    BankImportModel.CategoryModel cat = (splitCatId != null && !splitCatId.isBlank()) ? catById.get(splitCatId) : null;

                    boolean isIncome = (cat != null && "Revenu".equalsIgnoreCase(cat.kind())) || amt.compareTo(BigDecimal.ZERO) > 0;
                    if (isIncome) {
                        if (amt.compareTo(BigDecimal.ZERO) > 0) {
                            totalIncome = totalIncome.add(amt);
                        }
                    } else {
                        // Expense
                        if (splitCatId == null || splitCatId.isBlank()) {
                            uncategorizedCount++;
                        }
                        String label = (cat != null && cat.label() != null && !cat.label().isBlank()) ? cat.label() : "Non catégorisé";
                        BigDecimal expAmt = amt.negate();
                        expenseByCat.put(label, expenseByCat.getOrDefault(label, BigDecimal.ZERO).add(expAmt));

                        if (splitCatId != null && compressibleCatIds.contains(splitCatId)) {
                            compressibleTotal = compressibleTotal.add(expAmt);
                        }
                    }
                }
            } else {
                BigDecimal amt = t.amount() != null ? t.amount() : BigDecimal.ZERO;
                BankImportModel.CategoryModel cat = t.categoryId() != null ? catById.get(t.categoryId()) : null;

                boolean isIncome = (cat != null && "Revenu".equalsIgnoreCase(cat.kind())) || amt.compareTo(BigDecimal.ZERO) > 0;
                if (isIncome) {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        totalIncome = totalIncome.add(amt);
                    }
                } else {
                    // Expense
                    if (t.categoryId() == null || t.categoryId().isBlank()) {
                        uncategorizedCount++;
                    }
                    String label = (cat != null && cat.label() != null && !cat.label().isBlank()) ? cat.label() : "Non catégorisé";
                    BigDecimal expAmt = amt.negate();
                    expenseByCat.put(label, expenseByCat.getOrDefault(label, BigDecimal.ZERO).add(expAmt));

                    if (t.categoryId() != null && compressibleCatIds.contains(t.categoryId())) {
                        compressibleTotal = compressibleTotal.add(expAmt);
                    }
                }
            }
        }

        List<AnalyseCategorySummaryModel> categorySummaries = new ArrayList<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : expenseByCat.entrySet()) {
            String label = entry.getKey();
            BigDecimal amount = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            if (amount.abs().compareTo(new BigDecimal("0.01")) > 0) {
                totalExpenses = totalExpenses.add(amount);
                String color;
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    color = "#2F5D50";
                } else if ("Non catégorisé".equals(label)) {
                    color = "#6B7278";
                } else {
                    color = "#A8503C";
                }
                categorySummaries.add(new AnalyseCategorySummaryModel(label, amount, color));
            }
        }
        categorySummaries.sort((a, b) -> b.amount().compareTo(a.amount()));

        int nbMonths = Math.max(1, mBack > 0 ? mBack : 1);
        AnalyseKpiModel kpis = new AnalyseKpiModel(
                totalExpenses.setScale(2, RoundingMode.HALF_UP),
                totalIncome.setScale(2, RoundingMode.HALF_UP),
                nbMonths,
                uncategorizedCount,
                compressibleTotal.setScale(2, RoundingMode.HALF_UP)
        );

        // 2. Current Month & Landing Data
        YearMonth currentYM = YearMonth.now();
        String currentMonthISO = currentYM.toString();
        String currentMonthLabel = currentYM.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));

        List<PointageBudgetLineModel> activeLines = PointageCalculator.calculateActiveBudgetLines(
                new PointageModel(allTx, categories, bankImport.matchings(), data.charges(), data.incomes(), data.placements(), data.settings()),
                currentMonthISO
        );

        BankImportModel.MatchingModel currentMatching = null;
        if (bankImport.matchings() != null) {
            for (BankImportModel.MatchingModel m : bankImport.matchings()) {
                if (m != null && currentMonthISO.equals(m.month())) {
                    currentMatching = m;
                    break;
                }
            }
        }
        if (currentMatching == null) {
            currentMatching = new BankImportModel.MatchingModel(currentMonthISO, Collections.emptyList());
        }

        Map<String, BankImportModel.BankTransactionModel> txById = new HashMap<>();
        for (BankImportModel.BankTransactionModel tx : allTx) {
            if (tx.id() != null) {
                txById.put(tx.id(), tx);
            }
        }

        Map<String, List<String>> lineToTxIds = new HashMap<>();
        if (currentMatching.links() != null) {
            for (BankImportModel.MatchingLinkModel link : currentMatching.links()) {
                if (link.budgetLineId() != null && link.txIds() != null) {
                    lineToTxIds.put(link.budgetLineId(), link.txIds());
                }
            }
        }

        List<BankImportModel.PendingOperationModel> allPendingOps = bankImport.pendingOperations() != null
                ? bankImport.pendingOperations()
                : Collections.emptyList();

        List<AnalyseLandingRowModel> landingData = new ArrayList<>();
        for (PointageBudgetLineModel line : activeLines) {
            BigDecimal budgeted = line.monthly() != null ? line.monthly() : BigDecimal.ZERO;
            List<String> txIds = lineToTxIds.getOrDefault(line.id(), Collections.emptyList());

            BigDecimal reel = BigDecimal.ZERO;
            for (String refId : txIds) {
                BigDecimal amt = PointageCalculator.resolveAmount(refId, txById);
                if (amt != null) {
                    if ("revenu".equals(line.kind())) {
                        reel = reel.add(amt);
                    } else {
                        reel = reel.add(amt.negate());
                    }
                }
            }

            BigDecimal pendingContrib = BigDecimal.ZERO;
            for (BankImportModel.PendingOperationModel op : allPendingOps) {
                if ("pending".equalsIgnoreCase(op.status()) && line.id().equals(op.budgetLineId())) {
                    String opDate = op.date() != null ? op.date() : "";
                    if (opDate.isBlank() || opDate.startsWith(currentMonthISO)) {
                        BigDecimal amt = op.amount() != null ? op.amount() : BigDecimal.ZERO;
                        if ("revenu".equals(line.kind())) {
                            pendingContrib = pendingContrib.add(amt.max(BigDecimal.ZERO));
                        } else {
                            pendingContrib = pendingContrib.add(amt.abs());
                        }
                    }
                }
            }
            reel = reel.add(pendingContrib);

            BigDecimal pct = BigDecimal.ZERO;
            if (budgeted.compareTo(BigDecimal.ZERO) > 0) {
                pct = reel.divide(budgeted, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                if (pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                    pct = BigDecimal.valueOf(100);
                }
            }

            BigDecimal diff = reel.subtract(budgeted).abs();
            BigDecimal tolerance = budgeted.multiply(new BigDecimal("0.02")).max(BigDecimal.ONE);

            String status;
            boolean hasData = !txIds.isEmpty() || pendingContrib.compareTo(BigDecimal.ZERO) > 0;
            if (hasData) {
                if (diff.compareTo(tolerance) <= 0) {
                    status = "match";
                } else if ("revenu".equals(line.kind()) || "placement".equals(line.kind())) {
                    status = reel.compareTo(budgeted) > 0 ? "economy" : "over";
                } else {
                    status = reel.compareTo(budgeted) < 0 ? "economy" : "over";
                }
            } else {
                status = "pending";
            }

            landingData.add(new AnalyseLandingRowModel(
                    line.id(),
                    line.label(),
                    line.kind(),
                    budgeted.setScale(2, RoundingMode.HALF_UP),
                    reel.setScale(2, RoundingMode.HALF_UP),
                    pct.setScale(2, RoundingMode.HALF_UP),
                    status,
                    pendingContrib.setScale(2, RoundingMode.HALF_UP),
                    pendingContrib.compareTo(BigDecimal.ZERO) > 0
            ));
        }
        landingData.sort((a, b) -> b.budgeted().compareTo(a.budgeted()));

        // 3. Monthly Compare Data
        int nMonths = Math.min(mBack > 0 ? mBack : 12, 24);
        List<AnalyseMonthlyCompareModel> monthlyCompareData = new ArrayList<>();
        Map<String, BankImportModel.MatchingModel> matchingByMonth = new HashMap<>();
        if (bankImport.matchings() != null) {
            for (BankImportModel.MatchingModel m : bankImport.matchings()) {
                if (m.month() != null) {
                    matchingByMonth.put(m.month(), m);
                }
            }
        }

        Map<String, String> lineKindMap = new HashMap<>();
        if (data.charges() != null) {
            for (ChargeModel c : data.charges()) {
                if (c.id() != null) lineKindMap.put(c.id(), "charge");
            }
        }
        if (data.incomes() != null) {
            for (IncomeModel i : data.incomes()) {
                if (i.id() != null) lineKindMap.put(i.id(), "revenu");
            }
        }
        if (data.placements() != null) {
            for (PlacementModel p : data.placements()) {
                if (p.id() != null) lineKindMap.put(p.id(), "placement");
            }
        }

        for (int i = nMonths - 1; i >= 0; i--) {
            YearMonth ym = currentYM.minusMonths(i);
            String monthISO = ym.toString();
            List<PointageBudgetLineModel> monthLines = PointageCalculator.calculateActiveBudgetLines(
                    new PointageModel(allTx, categories, bankImport.matchings(), data.charges(), data.incomes(), data.placements(), data.settings()),
                    monthISO
            );

            BigDecimal budgeted = monthLines.stream()
                    .map(l -> l.monthly() != null ? l.monthly() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BankImportModel.MatchingModel monthMatching = matchingByMonth.get(monthISO);
            BigDecimal reel = BigDecimal.ZERO;
            boolean hasPointing = false;

            if (monthMatching != null && monthMatching.links() != null) {
                for (BankImportModel.MatchingLinkModel link : monthMatching.links()) {
                    if (link.txIds() != null && !link.txIds().isEmpty()) {
                        hasPointing = true;
                        String kind = lineKindMap.getOrDefault(link.budgetLineId(), "charge");
                        for (String refId : link.txIds()) {
                            BigDecimal amt = PointageCalculator.resolveAmount(refId, txById);
                            if (amt != null) {
                                if ("revenu".equals(kind)) {
                                    reel = reel.add(amt);
                                } else {
                                    reel = reel.add(amt.negate());
                                }
                            }
                        }
                    }
                }
            }

            BigDecimal pendingContrib = BigDecimal.ZERO;
            for (BankImportModel.PendingOperationModel op : allPendingOps) {
                if ("pending".equalsIgnoreCase(op.status()) && op.budgetLineId() != null && !op.budgetLineId().isBlank()) {
                    String opDate = op.date() != null ? op.date() : "";
                    if (opDate.startsWith(monthISO)) {
                        String kind = lineKindMap.getOrDefault(op.budgetLineId(), "charge");
                        BigDecimal amt = op.amount() != null ? op.amount() : BigDecimal.ZERO;
                        if ("revenu".equals(kind)) {
                            pendingContrib = pendingContrib.add(amt.max(BigDecimal.ZERO));
                        } else {
                            pendingContrib = pendingContrib.add(amt.abs());
                        }
                    }
                }
            }
            reel = reel.add(pendingContrib);
            if (pendingContrib.compareTo(BigDecimal.ZERO) > 0) {
                hasPointing = true;
            }

            String label = ym.format(DateTimeFormatter.ofPattern("MMM yy", Locale.FRENCH));
            monthlyCompareData.add(new AnalyseMonthlyCompareModel(
                    monthISO,
                    label,
                    budgeted.setScale(2, RoundingMode.HALF_UP),
                    reel.setScale(2, RoundingMode.HALF_UP),
                    hasPointing
            ));
        }

        // 4. Drift Rows (dérives par ligne)
        Map<String, RealAverageModel> realAverages = computeRealAveragesInternal(data, bankImport);
        List<AnalyseDriftRowModel> driftRows = new ArrayList<>();

        List<PointageBudgetLineModel> allPossibleLines = PointageCalculator.calculateActiveBudgetLines(
                new PointageModel(allTx, categories, bankImport.matchings(), data.charges(), data.incomes(), data.placements(), data.settings()),
                currentMonthISO
        );

        for (PointageBudgetLineModel line : allPossibleLines) {
            RealAverageModel avg = realAverages.get(line.id());
            BigDecimal budgeted = line.monthly() != null ? line.monthly() : BigDecimal.ZERO;
            BigDecimal avg3m = avg != null ? avg.avg3m() : null;
            BigDecimal avg12m = avg != null ? avg.avg12m() : null;
            int monthsCount = avg != null ? avg.months() : 0;

            BigDecimal ecart = null;
            BigDecimal ecartPct = null;
            String status = "pending";

            if (avg3m != null) {
                ecart = avg3m.subtract(budgeted);
                if (budgeted.compareTo(BigDecimal.ZERO) > 0) {
                    ecartPct = ecart.divide(budgeted, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                }
                BigDecimal diff = ecart.abs();
                BigDecimal tolerance = budgeted.multiply(new BigDecimal("0.02")).max(BigDecimal.ONE);
                if (diff.compareTo(tolerance) <= 0) {
                    status = "match";
                } else if ("revenu".equals(line.kind()) || "placement".equals(line.kind())) {
                    status = avg3m.compareTo(budgeted) > 0 ? "economy" : "over";
                } else {
                    status = avg3m.compareTo(budgeted) < 0 ? "economy" : "over";
                }
            }

            driftRows.add(new AnalyseDriftRowModel(
                    line.id(),
                    line.label(),
                    line.kind(),
                    budgeted.setScale(2, RoundingMode.HALF_UP),
                    avg3m != null ? avg3m.setScale(2, RoundingMode.HALF_UP) : null,
                    avg12m != null ? avg12m.setScale(2, RoundingMode.HALF_UP) : null,
                    ecart != null ? ecart.setScale(2, RoundingMode.HALF_UP) : null,
                    ecartPct != null ? ecartPct.setScale(2, RoundingMode.HALF_UP) : null,
                    status,
                    monthsCount
            ));
        }

        return new AnalyseResultModel(
                data,
                kpis,
                landingData,
                driftRows,
                monthlyCompareData,
                categorySummaries,
                currentMonthISO,
                currentMonthLabel
        );
    }

    private static Map<String, RealAverageModel> computeRealAveragesInternal(BudgetDataModel data, BankImportModel bankImport) {
        if (bankImport == null || bankImport.matchings() == null || bankImport.transactions() == null) {
            return Collections.emptyMap();
        }

        Map<String, BankImportModel.BankTransactionModel> txById = new HashMap<>();
        for (BankImportModel.BankTransactionModel tx : bankImport.transactions()) {
            if (tx.id() != null) txById.put(tx.id(), tx);
        }

        Map<String, String> lineKindMap = new HashMap<>();
        if (data.charges() != null) {
            for (ChargeModel c : data.charges()) {
                if (c.id() != null) lineKindMap.put(c.id(), "charge");
            }
        }
        if (data.incomes() != null) {
            for (IncomeModel i : data.incomes()) {
                if (i.id() != null) lineKindMap.put(i.id(), "revenu");
            }
        }
        if (data.placements() != null) {
            for (PlacementModel p : data.placements()) {
                if (p.id() != null) lineKindMap.put(p.id(), "placement");
            }
        }

        record MonthEntry(String month, BigDecimal realAmount) {}
        Map<String, List<MonthEntry>> byLine = new HashMap<>();

        for (BankImportModel.MatchingModel m : bankImport.matchings()) {
            String month = m.month();
            if (m.links() == null) continue;
            for (BankImportModel.MatchingLinkModel l : m.links()) {
                if (l.budgetLineId() == null || l.txIds() == null || l.txIds().isEmpty()) continue;
                String kind = lineKindMap.getOrDefault(l.budgetLineId(), "charge");

                BigDecimal sum = BigDecimal.ZERO;
                for (String refId : l.txIds()) {
                    BigDecimal amt = PointageCalculator.resolveAmount(refId, txById);
                    if (amt != null) {
                        sum = sum.add("revenu".equals(kind) ? amt : amt.negate());
                    }
                }

                byLine.computeIfAbsent(l.budgetLineId(), k -> new ArrayList<>())
                        .add(new MonthEntry(month, sum));
            }
        }

        String todayISO = LocalDate.now().toString().substring(0, 7);
        Map<String, RealAverageModel> result = new HashMap<>();

        for (Map.Entry<String, List<MonthEntry>> entry : byLine.entrySet()) {
            String lineId = entry.getKey();
            List<MonthEntry> sorted = new ArrayList<>(entry.getValue());
            sorted.sort((a, b) -> (b.month() != null ? b.month() : "").compareTo(a.month() != null ? a.month() : ""));

            List<MonthEntry> last3 = sorted.stream()
                    .filter(e -> e.month() != null && e.month().compareTo(todayISO) <= 0)
                    .limit(3)
                    .collect(Collectors.toList());

            List<MonthEntry> last12 = sorted.stream()
                    .filter(e -> e.month() != null && e.month().compareTo(todayISO) <= 0)
                    .limit(12)
                    .collect(Collectors.toList());

            BigDecimal avg3m = null;
            if (!last3.isEmpty()) {
                BigDecimal sum3 = last3.stream().map(MonthEntry::realAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                avg3m = sum3.divide(BigDecimal.valueOf(last3.size()), 10, RoundingMode.HALF_UP);
            }

            BigDecimal avg12m = null;
            if (!last12.isEmpty()) {
                BigDecimal sum12 = last12.stream().map(MonthEntry::realAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                avg12m = sum12.divide(BigDecimal.valueOf(last12.size()), 10, RoundingMode.HALF_UP);
            }

            result.put(lineId, new RealAverageModel(avg3m, avg12m, last12.size()));
        }

        return result;
    }
}
