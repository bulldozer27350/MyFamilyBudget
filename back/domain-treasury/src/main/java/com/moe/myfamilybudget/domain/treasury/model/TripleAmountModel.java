package com.moe.myfamilybudget.domain.treasury.model;

import java.math.BigDecimal;

public record TripleAmountModel(
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
