package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record PatrimoineYearDto(
    int year,
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
