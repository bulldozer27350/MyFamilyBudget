package com.moe.myfamilybudget.application.usecase.wealth;

import java.math.BigDecimal;

/** Un virement vers un placement, rappelé dans la réponse Patrimoine. */
public record PatrimoineTransferModel(
    String id,
    String placement,
    String date,
    BigDecimal amount,
    String notes
) {}
