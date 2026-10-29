package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Commande de mise à jour des hypothèses de l'analyse des prêts (R-86, SILO-317).
 *
 * <p>Transmise par le module {@code web} (aujourd'hui {@code AnalysePretsServiceImpl}) après
 * désérialisation du DTO HTTP, avant validation par le cas d'usage.
 */
public record LoanAdviceParametersSaveCommand(
        BigDecimal marketRate,
        BigDecimal repayMarginRate,
        BigDecimal renegotiationMinGapRate,
        BigDecimal renegotiationMinCrd,
        Integer renegotiationMinRemainingMonths,
        BigDecimal renegotiationFixedCosts,
        BigDecimal flatTaxRate) {
}
