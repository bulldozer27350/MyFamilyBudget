package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record TripleAmountModel(
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
