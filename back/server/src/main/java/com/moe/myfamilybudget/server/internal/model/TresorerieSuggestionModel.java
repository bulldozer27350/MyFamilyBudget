package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record TresorerieSuggestionModel(
    String id,
    String label,
    String kind,
    BigDecimal budgeted,
    BigDecimal avg3m,
    BigDecimal avg12m,
    BigDecimal ecart,
    BigDecimal ecartPct,
    Integer months,
    BigDecimal suggested
) {}
