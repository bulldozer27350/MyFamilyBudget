package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Paramètres de la projection de trésorerie (RF-400, voir doc/architecture/06-domaine-tresorerie.md
 * et 12-settings.md).
 *
 * @param retireYear    année de départ à la retraite (settings), utilisée telle quelle en sortie
 * @param startBalance  solde de départ de la trésorerie ({@code null} vaut 0) ; déjà résolu par
 *                      l'appelant (pivot manuel ou reconstitué depuis le pointage bancaire — voir
 *                      {@code computePivotBalance})
 * @param inflationRate taux d'inflation annuel, valeur par défaut de la croissance des charges
 *                      dont {@link ChargeProjectionInput#growthRate()} est {@code null}
 *                      ({@code null} vaut 0)
 */
public record TreasuryParameters(int retireYear, BigDecimal startBalance, BigDecimal inflationRate) {
    public TreasuryParameters {
        startBalance = startBalance != null ? startBalance : BigDecimal.ZERO;
        inflationRate = inflationRate != null ? inflationRate : BigDecimal.ZERO;
    }
}
