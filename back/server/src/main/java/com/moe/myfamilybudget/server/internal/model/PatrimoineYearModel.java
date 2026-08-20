package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record PatrimoineYearModel(
    int year,
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
