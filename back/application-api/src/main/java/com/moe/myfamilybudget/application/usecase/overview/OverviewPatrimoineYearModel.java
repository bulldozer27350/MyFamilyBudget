package com.moe.myfamilybudget.application.usecase.overview;

import java.math.BigDecimal;

/**
 * Montants projetés d'un placement ou du patrimoine total pour une année donnée (R-85).
 */
public record OverviewPatrimoineYearModel(
    int year,
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
