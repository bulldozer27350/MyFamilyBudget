package com.moe.myfamilybudget.domain.analysis.core;

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

import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseCalculationService;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseInput;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisBudgetLine;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisCategory;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisMatching;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisPendingOperation;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisPeriod;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisRealAverage;
import com.moe.myfamilybudget.domain.analysis.calculation.AnalysisTransaction;
import com.moe.myfamilybudget.domain.analysis.calculation.BudgetLineKind;
import com.moe.myfamilybudget.domain.analysis.calculation.MonthlyBudgetLines;
import com.moe.myfamilybudget.domain.analysis.model.AnalyseResultModel;
import com.moe.myfamilybudget.domain.analysis.model.AnalyseKpiModel;
import com.moe.myfamilybudget.domain.analysis.model.AnalyseDriftRowModel;
import com.moe.myfamilybudget.domain.analysis.model.AnalyseLandingRowModel;
import com.moe.myfamilybudget.domain.analysis.model.AnalyseMonthlyCompareModel;
import com.moe.myfamilybudget.domain.analysis.model.AnalyseCategorySummaryModel;

/**
 * Calculateur metier pour l'analyse Reel vs Previsionnel et derives (RF-601).
 * Isole de toute API REST ou DTO et de tout modele persistant (BudgetDataModel).
 * Opere exclusivement sur {@link AnalyseInput}.
 *
 * <p>SILO-155 : implementation de {@link AnalyseCalculationService} ({@code analysis-api}), hebergee dans
 * {@code analysis-core} ; l'ancienne methode statique {@code AnalyseCalculator.computeAnalyse} devient une
 * methode d'instance de l'interface (calcul inchange, helpers prives restes statiques).
 */
public final class DefaultAnalyseCalculationService implements AnalyseCalculationService {

    @Override
    public AnalyseResultModel computeAnalyse(AnalyseInput input) {
        if (input == null) {
            input = new AnalyseInput(null, null, null, null, null, null, null);
        }

        AnalysisPeriod period = input.period();
        LocalDate today = period != null && period.today() != null ? period.today() : LocalDate.now();
        int mBack = period != null ? period.monthsBack() : 12;

        String cutoffISO = null;
        if (mBack > 0) {
            cutoffISO = today.minusMonths(mBack).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        List<AnalysisTransaction> allTx = input.transactions() != null ? input.transactions() : Collections.emptyList();
        final String finalCutoff = cutoffISO;
        List<AnalysisTransaction> periodTx = allTx.stream()
                .filter(t -> t != null && t.date() != null && (finalCutoff == null || t.date().compareTo(finalCutoff) >= 0))
                .collect(Collectors.toList());

        List<AnalysisCategory> categories = input.categories() != null ? input.categories() : Collections.emptyList();
        Map<String, AnalysisCategory> catById = new HashMap<>();
        for (AnalysisCategory c : categories) {
            if (c != null && c.id() != null) {
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
                .map(AnalysisCategory::id)
                .collect(Collectors.toSet());

        for (AnalysisTransaction t : periodTx) {
            if (t.splits() != null && !t.splits().isEmpty()) {
                for (AnalysisTransaction.Split split : t.splits()) {
                    BigDecimal amt = split.amount() != null ? split.amount() : BigDecimal.ZERO;
                    String splitCatId = split.categoryId();
                    AnalysisCategory cat = (splitCatId != null && !splitCatId.isBlank()) ? catById.get(splitCatId) : null;

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
                AnalysisCategory cat = t.categoryId() != null ? catById.get(t.categoryId()) : null;

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
        YearMonth currentYM = YearMonth.from(today);
        String currentMonthISO = currentYM.toString();
        String currentMonthLabel = currentYM.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));

        List<AnalysisBudgetLine> activeLines = Collections.emptyList();
        if (input.monthlyBudgetLines() != null) {
            for (MonthlyBudgetLines mbl : input.monthlyBudgetLines()) {
                if (mbl != null && currentMonthISO.equals(mbl.monthISO())) {
                    activeLines = mbl.lines() != null ? mbl.lines() : Collections.emptyList();
                    break;
                }
            }
        }

        AnalysisMatching currentMatching = null;
        if (input.matchings() != null) {
            for (AnalysisMatching m : input.matchings()) {
                if (m != null && currentMonthISO.equals(m.month())) {
                    currentMatching = m;
                    break;
                }
            }
        }
        if (currentMatching == null) {
            currentMatching = new AnalysisMatching(currentMonthISO, Collections.emptyList());
        }

        Map<String, AnalysisTransaction> txById = new HashMap<>();
        for (AnalysisTransaction tx : allTx) {
            if (tx != null && tx.id() != null) {
                txById.put(tx.id(), tx);
            }
        }

        Map<String, List<String>> lineToTxIds = new HashMap<>();
        if (currentMatching.links() != null) {
            for (AnalysisMatching.Link link : currentMatching.links()) {
                if (link != null && link.budgetLineId() != null && link.txIds() != null) {
                    lineToTxIds.put(link.budgetLineId(), link.txIds());
                }
            }
        }

        List<AnalysisPendingOperation> allPendingOps = input.pendingOperations() != null
                ? input.pendingOperations()
                : Collections.emptyList();

        List<AnalyseLandingRowModel> landingData = new ArrayList<>();
        for (AnalysisBudgetLine line : activeLines) {
            BigDecimal budgeted = line.monthly() != null ? line.monthly() : BigDecimal.ZERO;
            List<String> txIds = lineToTxIds.getOrDefault(line.id(), Collections.emptyList());

            BigDecimal reel = BigDecimal.ZERO;
            for (String refId : txIds) {
                BigDecimal amt = resolveAmount(refId, txById);
                if (amt != null) {
                    if ("revenu".equals(line.kind())) {
                        reel = reel.add(amt);
                    } else {
                        reel = reel.add(amt.negate());
                    }
                }
            }

            BigDecimal pendingContrib = BigDecimal.ZERO;
            for (AnalysisPendingOperation op : allPendingOps) {
                if (op != null && "pending".equalsIgnoreCase(op.status()) && line.id().equals(op.budgetLineId())) {
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
        Map<String, AnalysisMatching> matchingByMonth = new HashMap<>();
        if (input.matchings() != null) {
            for (AnalysisMatching m : input.matchings()) {
                if (m != null && m.month() != null) {
                    matchingByMonth.put(m.month(), m);
                }
            }
        }

        Map<String, List<AnalysisBudgetLine>> linesByMonth = new HashMap<>();
        if (input.monthlyBudgetLines() != null) {
            for (MonthlyBudgetLines mbl : input.monthlyBudgetLines()) {
                if (mbl != null && mbl.monthISO() != null) {
                    linesByMonth.put(mbl.monthISO(), mbl.lines() != null ? mbl.lines() : Collections.emptyList());
                }
            }
        }

        Map<String, String> lineKindMap = new HashMap<>();
        if (input.lineKinds() != null) {
            for (BudgetLineKind bk : input.lineKinds()) {
                if (bk != null && bk.lineId() != null) {
                    lineKindMap.put(bk.lineId(), bk.kind());
                }
            }
        }

        for (int i = nMonths - 1; i >= 0; i--) {
            YearMonth ym = currentYM.minusMonths(i);
            String monthISO = ym.toString();
            List<AnalysisBudgetLine> monthLines = linesByMonth.getOrDefault(monthISO, Collections.emptyList());

            BigDecimal budgeted = monthLines.stream()
                    .map(l -> l.monthly() != null ? l.monthly() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            AnalysisMatching monthMatching = matchingByMonth.get(monthISO);
            BigDecimal reel = BigDecimal.ZERO;
            boolean hasPointing = false;

            if (monthMatching != null && monthMatching.links() != null) {
                for (AnalysisMatching.Link link : monthMatching.links()) {
                    if (link != null && link.txIds() != null && !link.txIds().isEmpty()) {
                        hasPointing = true;
                        String kind = lineKindMap.getOrDefault(link.budgetLineId(), "charge");
                        for (String refId : link.txIds()) {
                            BigDecimal amt = resolveAmount(refId, txById);
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
            for (AnalysisPendingOperation op : allPendingOps) {
                if (op != null && "pending".equalsIgnoreCase(op.status()) && op.budgetLineId() != null && !op.budgetLineId().isBlank()) {
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
        Map<String, AnalysisRealAverage> realAverages = computeRealAveragesInternal(input, txById, lineKindMap, currentMonthISO);
        List<AnalyseDriftRowModel> driftRows = new ArrayList<>();

        for (AnalysisBudgetLine line : activeLines) {
            AnalysisRealAverage avg = realAverages.get(line.id());
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
                kpis,
                landingData,
                driftRows,
                monthlyCompareData,
                categorySummaries,
                currentMonthISO,
                currentMonthLabel
        );
    }

    /**
     * Résout le montant d'un identifiant de transaction composite ("txId" ou "txId#splitId").
     */
    private static BigDecimal resolveAmount(String refId, Map<String, AnalysisTransaction> txMap) {
        if (refId == null || refId.isBlank() || txMap == null) {
            return BigDecimal.ZERO;
        }
        int hashIdx = refId.indexOf('#');
        if (hashIdx >= 0) {
            String txId = refId.substring(0, hashIdx);
            String splitId = refId.substring(hashIdx + 1);
            AnalysisTransaction tx = txMap.get(txId);
            if (tx != null && tx.splits() != null) {
                for (AnalysisTransaction.Split split : tx.splits()) {
                    if (split != null && splitId.equals(split.id())) {
                        return split.amount() != null ? split.amount() : BigDecimal.ZERO;
                    }
                }
            }
            return BigDecimal.ZERO;
        }
        AnalysisTransaction tx = txMap.get(refId);
        return (tx != null && tx.amount() != null) ? tx.amount() : BigDecimal.ZERO;
    }

    private static Map<String, AnalysisRealAverage> computeRealAveragesInternal(
            AnalyseInput input,
            Map<String, AnalysisTransaction> txById,
            Map<String, String> lineKindMap,
            String todayMonthISO) {

        if (input == null || input.matchings() == null || input.transactions() == null) {
            return Collections.emptyMap();
        }

        record MonthEntry(String month, BigDecimal realAmount) {}
        Map<String, List<MonthEntry>> byLine = new HashMap<>();

        for (AnalysisMatching m : input.matchings()) {
            String month = m.month();
            if (m.links() == null) continue;
            for (AnalysisMatching.Link l : m.links()) {
                if (l.budgetLineId() == null || l.txIds() == null || l.txIds().isEmpty()) continue;
                String kind = lineKindMap.getOrDefault(l.budgetLineId(), "charge");

                BigDecimal sum = BigDecimal.ZERO;
                for (String refId : l.txIds()) {
                    BigDecimal amt = resolveAmount(refId, txById);
                    if (amt != null) {
                        sum = sum.add("revenu".equals(kind) ? amt : amt.negate());
                    }
                }

                byLine.computeIfAbsent(l.budgetLineId(), k -> new ArrayList<>())
                        .add(new MonthEntry(month, sum));
            }
        }

        Map<String, AnalysisRealAverage> result = new HashMap<>();

        for (Map.Entry<String, List<MonthEntry>> entry : byLine.entrySet()) {
            String lineId = entry.getKey();
            List<MonthEntry> sorted = new ArrayList<>(entry.getValue());
            sorted.sort((a, b) -> (b.month() != null ? b.month() : "").compareTo(a.month() != null ? a.month() : ""));

            List<MonthEntry> last3 = sorted.stream()
                    .filter(e -> e.month() != null && e.month().compareTo(todayMonthISO) <= 0)
                    .limit(3)
                    .collect(Collectors.toList());

            List<MonthEntry> last12 = sorted.stream()
                    .filter(e -> e.month() != null && e.month().compareTo(todayMonthISO) <= 0)
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

            result.put(lineId, new AnalysisRealAverage(avg3m, avg12m, last12.size()));
        }

        return result;
    }
}