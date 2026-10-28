package com.moe.myfamilybudget.application.usecase.tax;

import java.math.BigDecimal;

/** Une tranche du barème d'imposition rappelée dans la réponse Fiscalité. */
public record TaxBracketModel(
    String id,
    BigDecimal upTo,
    BigDecimal rate
) {}
