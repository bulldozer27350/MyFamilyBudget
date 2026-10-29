package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Hypothèses de l'analyse des prêts (paramétrables), exposées par l'application (R-86, SILO-317).
 */
public record LoanAssumptionsModel(
        String loanType,
        BigDecimal repayMarginRate,
        BigDecimal renegotiationMinGapRate,
        BigDecimal renegotiationMinCrd,
        Integer renegotiationMinRemainingMonths,
        BigDecimal renegotiationFixedCosts,
        BigDecimal flatTaxRate) {
}
