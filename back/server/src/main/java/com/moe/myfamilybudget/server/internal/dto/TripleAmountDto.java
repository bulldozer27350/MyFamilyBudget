package com.moe.myfamilybudget.server.internal.dto;

import java.math.BigDecimal;

public record TripleAmountDto(
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
