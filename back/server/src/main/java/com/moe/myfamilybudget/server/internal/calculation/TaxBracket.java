package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Une tranche du barème progressif de l'impôt sur le revenu — voir {@link TaxCalculationInput}.
 *
 * @param upTo plafond de la tranche (inclus) ; {@code null} pour la dernière tranche (pas de plafond)
 * @param rate taux marginal (fraction) appliqué à la portion de revenu dans cette tranche
 */
public record TaxBracket(BigDecimal upTo, BigDecimal rate) {
}
