package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

public record VariablePreviewCellModel(
    int year,
    BigDecimal amount,
    boolean isReal
) {}
