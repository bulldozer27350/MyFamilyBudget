package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record OneOffExpenseDto(
    String id,
    String label,
    String date,
    BigDecimal amount,
    String notes
) {}
