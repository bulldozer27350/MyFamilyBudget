package com.moe.myfamilybudget.application.usecase.overview;

import java.math.BigDecimal;

/**
 * Montant décliné selon les 3 scénarios (pessimiste, central, optimiste) pour l'Overview (R-85).
 */
public record OverviewTripleAmountModel(
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
