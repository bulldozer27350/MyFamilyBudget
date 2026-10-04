package com.moe.myfamilybudget.domain.treasury.model;

import java.math.BigDecimal;

public record VariablePreviewCellModel(
    int year,
    BigDecimal amount,
    boolean isReal
) {}
