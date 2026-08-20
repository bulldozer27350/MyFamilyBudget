package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record ChargeDto(
    String id,
    String label,
    BigDecimal monthly,
    String start,
    String end,
    BigDecimal growthRate,
    String categoryId,
    String notes
) {}
