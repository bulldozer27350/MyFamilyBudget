package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Valeurs d'un jeu d'hypothèses de l'analyse des prêts (R-86, SILO-317).
 *
 * <p>Miroir applicatif de {@code domain.credit.calculation.LoanAdviceParameters}.
 *
 * @param marketRate                    taux de marché de référence (null = non renseigné)
 * @param repayMarginRate               marge de décision remboursement
 * @param renegotiationMinGapRate       écart minimal pour déclencher une suggestion de renégociation
 * @param renegotiationMinCrd           capital restant dû minimal (€) pour envisager une renégociation
 * @param renegotiationMinRemainingMonths durée restante minimale (mois) pour envisager une renégociation
 * @param renegotiationFixedCosts       frais fixes estimés d'une renégociation (€)
 * @param flatTaxRate                   taux de la fiscalité forfaitaire (PFU)
 */
public record LoanAdviceParametersValuesModel(
        BigDecimal marketRate,
        BigDecimal repayMarginRate,
        BigDecimal renegotiationMinGapRate,
        BigDecimal renegotiationMinCrd,
        Integer renegotiationMinRemainingMonths,
        BigDecimal renegotiationFixedCosts,
        BigDecimal flatTaxRate) {
}
