package com.moe.myfamilybudget.application.usecase.treasury;

/**
 * Modèle applicatif d'une option de catégorie (R-82, mirroir de
 * {@code domain.treasury.model.CategoryOptionModel}).
 */
public record CategoryOptionModel(
    String value,
    String label
) {}
