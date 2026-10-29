package com.moe.myfamilybudget.application.usecase.overview;

import java.math.BigDecimal;

/**
 * Modèle applicatif d'une année de projection de trésorerie pour l'Overview (R-85).
 */
public record OverviewCashflowYearModel(
    int year,
    BigDecimal income,
    BigDecimal variableIncome,
    BigDecimal savings,
    BigDecimal charges,
    BigDecimal oneoff,
    BigDecimal transfersY,
    BigDecimal impots,
    BigDecimal regularisation,
    BigDecimal net,
    BigDecimal balance
) {}
