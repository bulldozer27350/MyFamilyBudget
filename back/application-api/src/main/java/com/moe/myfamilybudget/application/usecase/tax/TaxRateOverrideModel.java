package com.moe.myfamilybudget.application.usecase.tax;

import java.math.BigDecimal;

/** Une surcharge de taux pour une année donnée, rappelée dans la réponse Fiscalité. */
public record TaxRateOverrideModel(
    Integer year,
    BigDecimal rate
) {}
