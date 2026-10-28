package com.moe.myfamilybudget.application.usecase.treasury;

import java.util.List;

/**
 * Modèle applicatif de la prévisualisation d'un revenu variable (R-82, mirroir de
 * {@code domain.treasury.model.VariablePreviewModel}).
 */
public record VariablePreviewModel(
    String label,
    List<VariablePreviewCellModel> cells
) {}
