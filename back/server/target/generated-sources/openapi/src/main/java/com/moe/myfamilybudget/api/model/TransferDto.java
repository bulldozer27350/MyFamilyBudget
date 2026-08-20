package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;

public record TransferDto(
    String id,
    String placement,
    String date,
    BigDecimal amount,
    String notes
) {}
