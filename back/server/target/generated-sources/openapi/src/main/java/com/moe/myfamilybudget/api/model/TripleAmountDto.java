package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record TripleAmountDto(
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
