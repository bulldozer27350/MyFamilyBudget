package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record TaxBracketModel(
    String id,
    BigDecimal upTo,
    BigDecimal rate
) {
    public BigDecimal getEffectiveRate() {
        return rate != null ? rate : BigDecimal.ZERO;
    }
}
