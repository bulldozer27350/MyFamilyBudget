package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;

/**
 * Modèle applicatif d'un revenu variable (R-82, mirroir de
 * {@code domain.treasury.model.VariableIncomeModel}).
 */
public record VariableIncomeModel(
    String id,
    String label,
    String refIncomeLabel,
    BigDecimal rate,
    Integer startYear,
    Integer endYear,
    String taxable,
    String type,
    String notes
) {}
