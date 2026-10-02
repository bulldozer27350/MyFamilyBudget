package com.moe.myfamilybudget.domain.budget;

import java.math.BigDecimal;

public record VariablePreviewCellModel(
    int year,
    BigDecimal amount,
    boolean isReal
) {}
