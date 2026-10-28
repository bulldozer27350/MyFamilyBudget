package com.moe.myfamilybudget.application.usecase.treasury;

import java.math.BigDecimal;

/**
 * Modèle applicatif d'une année de projection de trésorerie (R-82, mirroir de
 * {@code domain.treasury.model.CashflowYearModel}).
 */
public record CashflowYearModel(
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
