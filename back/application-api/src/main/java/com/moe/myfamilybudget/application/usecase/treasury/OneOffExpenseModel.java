package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;

/**
 * Modèle applicatif d'une dépense ponctuelle (R-82, mirroir de
 * {@code domain.treasury.model.OneOffExpenseModel}).
 */
public record OneOffExpenseModel(
    String id,
    String label,
    String date,
    BigDecimal amount,
    String notes
) {}
