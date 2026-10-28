package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;

/**
 * Modèle applicatif d'une suggestion budgétaire (R-82, mirroir de
 * {@code domain.treasury.model.TresorerieSuggestionModel}).
 */
public record TresorerieSuggestionModel(
    String id,
    String label,
    String kind,
    BigDecimal budgeted,
    BigDecimal avg3m,
    BigDecimal avg12m,
    BigDecimal ecart,
    BigDecimal ecartPct,
    Integer months,
    BigDecimal suggested
) {}
