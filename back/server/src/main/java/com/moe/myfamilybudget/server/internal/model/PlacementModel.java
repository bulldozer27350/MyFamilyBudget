package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record PlacementModel(
    String id,
    String label,
    String category,
    BigDecimal balance,
    String balanceDate,
    BigDecimal monthly,
    String monthlyFrom,
    String monthlyUntil,
    BigDecimal ratePess,
    BigDecimal rateCorr,
    BigDecimal rateOpti,
    Boolean excludedFromRetirement,
    String notes,
    Integer sweepPriority,
    BigDecimal sweepCap,
    BigDecimal pauseTriggerBalance,
    Integer pausePriority,
    String categoryId,
    List<PlacementHistoryEntryModel> history
) {
    public PlacementModel(
        String id,
        String label,
        String category,
        BigDecimal balance,
        String balanceDate,
        BigDecimal monthly,
        String monthlyFrom,
        String monthlyUntil,
        BigDecimal ratePess,
        BigDecimal rateCorr,
        BigDecimal rateOpti,
        Boolean excludedFromRetirement,
        String notes
    ) {
        this(id, label, category, balance, balanceDate, monthly, monthlyFrom, monthlyUntil, ratePess, rateCorr, rateOpti, excludedFromRetirement, notes, null, null, null, null, "", List.of());
    }

    // Constructeur de compatibilite (sans historique) conserve pour ne pas casser les
    // nombreux sites d'appel existants qui ne manipulent pas l'historique de valorisation.
    public PlacementModel(
        String id,
        String label,
        String category,
        BigDecimal balance,
        String balanceDate,
        BigDecimal monthly,
        String monthlyFrom,
        String monthlyUntil,
        BigDecimal ratePess,
        BigDecimal rateCorr,
        BigDecimal rateOpti,
        Boolean excludedFromRetirement,
        String notes,
        Integer sweepPriority,
        BigDecimal sweepCap,
        BigDecimal pauseTriggerBalance,
        Integer pausePriority,
        String categoryId
    ) {
        this(id, label, category, balance, balanceDate, monthly, monthlyFrom, monthlyUntil, ratePess, rateCorr, rateOpti, excludedFromRetirement, notes, sweepPriority, sweepCap, pauseTriggerBalance, pausePriority, categoryId, List.of());
    }

    public BigDecimal getEffectiveBalance() {
        return balance != null ? balance : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveMonthly() {
        return monthly != null ? monthly : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveRatePess() {
        return ratePess != null ? ratePess : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveRateCorr() {
        return rateCorr != null ? rateCorr : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveRateOpti() {
        return rateOpti != null ? rateOpti : BigDecimal.ZERO;
    }

    public boolean isExcludedFromRetirement() {
        return Boolean.TRUE.equals(excludedFromRetirement);
    }

    public List<PlacementHistoryEntryModel> getEffectiveHistory() {
        return history != null ? history : List.of();
    }
}
