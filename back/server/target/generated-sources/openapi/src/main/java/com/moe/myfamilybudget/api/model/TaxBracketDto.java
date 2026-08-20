package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record TaxBracketDto(
    String id,
    BigDecimal upTo,
    BigDecimal rate
) {}
