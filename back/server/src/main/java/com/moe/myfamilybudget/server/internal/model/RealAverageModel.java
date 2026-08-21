package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record RealAverageModel(
    BigDecimal avg3m,
    BigDecimal avg12m,
    int months
) {}
