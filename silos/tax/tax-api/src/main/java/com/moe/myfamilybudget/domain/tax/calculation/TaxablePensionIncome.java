package com.moe.myfamilybudget.domain.tax.calculation;

import java.math.BigDecimal;

/**
 * Pension imposable déjà projetée pour une année (somme des pensions de toutes les personnes du foyer),
 * telle que le domaine Fiscalité la consomme dans {@link TaxCalculationInput}.
 *
 * <p>SILO-130 (décision D1) : contrat propre au consommateur. Le domaine Fiscalité ne connaît plus aucun type
 * du domaine Retraite ; c'est {@code TaxInputFactory}, côté application, qui traduit la projection de
 * {@code RetirementCalculationService} (RF-102) en ce type.
 *
 * @param year   année de la pension
 * @param amount montant annuel imposable
 */
public record TaxablePensionIncome(int year, BigDecimal amount) {
}
