package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

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
