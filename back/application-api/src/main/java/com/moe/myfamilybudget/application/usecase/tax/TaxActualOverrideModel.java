package com.moe.myfamilybudget.application.usecase.tax;

import java.math.BigDecimal;

/** Un montant d'impôt réel pour une année donnée, rappelé dans la réponse Fiscalité. */
public record TaxActualOverrideModel(
    Integer year,
    BigDecimal amount
) {}
