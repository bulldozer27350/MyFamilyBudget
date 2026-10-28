package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;

/** Modèle applicatif d'un revenu (R-82, mirroir de {@code domain.treasury.model.IncomeModel}). */
public record IncomeModel(
    String id,
    String label,
    BigDecimal monthly,
    String start,
    String end,
    BigDecimal growthRate,
    String categoryId,
    String notes
) {}
