package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;

/** Modèle applicatif d'une charge (R-82, mirroir de {@code domain.treasury.model.ChargeModel}). */
public record ChargeModel(
    String id,
    String label,
    BigDecimal monthly,
    String start,
    String end,
    BigDecimal growthRate,
    String categoryId,
    String notes
) {}
