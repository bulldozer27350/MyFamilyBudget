package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;

/**
 * Modèle applicatif d'une surcharge de revenu variable (R-82, mirroir de
 * {@code domain.treasury.model.VariableOverrideModel}).
 */
public record VariableOverrideModel(
    String id,
    String label,
    Integer year,
    BigDecimal amount,
    String taxable,
    String notes
) {}
