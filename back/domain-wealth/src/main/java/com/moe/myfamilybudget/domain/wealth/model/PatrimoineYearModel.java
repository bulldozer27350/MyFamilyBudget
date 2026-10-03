package com.moe.myfamilybudget.domain.wealth.model;

import java.math.BigDecimal;

public record PatrimoineYearModel(
    int year,
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
