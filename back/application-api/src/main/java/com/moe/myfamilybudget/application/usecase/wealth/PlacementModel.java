package com.moe.myfamilybudget.application.usecase.wealth;

import java.math.BigDecimal;
import java.util.List;

/** Un placement/compte du patrimoine, rappelé dans la réponse Patrimoine. */
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
) {}
