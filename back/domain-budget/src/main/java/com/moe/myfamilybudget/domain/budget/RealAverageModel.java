package com.moe.myfamilybudget.domain.budget;

import java.math.BigDecimal;

public record RealAverageModel(
    BigDecimal avg3m,
    BigDecimal avg12m,
    int months
) {}
