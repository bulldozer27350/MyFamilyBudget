package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record CashflowYearDto(
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
