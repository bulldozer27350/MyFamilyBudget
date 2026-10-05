package com.moe.myfamilybudget.application.usecase.retirement;

import java.math.BigDecimal;

/** Revenu rappelé dans la réponse Retraite. */
public record RetraiteIncomeModel(
    String id,
    String label,
    BigDecimal monthly,
    String start,
    String end,
    BigDecimal growthRate,
    String categoryId,
    String notes
) {}
