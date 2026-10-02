package com.moe.myfamilybudget.domain.budget;

import java.math.BigDecimal;

public record TripleAmountModel(
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
