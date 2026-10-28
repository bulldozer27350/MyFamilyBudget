package com.moe.myfamilybudget.application.usecase.wealth;

import java.math.BigDecimal;

/** Une valeur réelle constatée pour un placement/compte à une date donnée. */
public record PlacementHistoryEntryModel(
    String id,
    String date,
    BigDecimal value,
    String notes
) {}
