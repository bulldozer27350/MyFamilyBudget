package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;

/**
 * Modèle applicatif d'une cellule de prévisualisation d'un revenu variable (R-82, mirroir de
 * {@code domain.treasury.model.VariablePreviewCellModel}).
 */
public record VariablePreviewCellModel(
    int year,
    BigDecimal amount,
    boolean isReal
) {}
