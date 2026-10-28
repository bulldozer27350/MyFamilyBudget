package com.moe.myfamilybudget.application.usecase.wealth;

import java.math.BigDecimal;

/** Projection patrimoniale (pessimiste/correcte/optimiste) pour une année donnée. */
public record PatrimoineYearModel(
    int year,
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
