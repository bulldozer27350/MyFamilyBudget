package com.moe.myfamilybudget.server.internal.dto;

import java.math.BigDecimal;

public record PatrimoineYearDto(
    int year,
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
