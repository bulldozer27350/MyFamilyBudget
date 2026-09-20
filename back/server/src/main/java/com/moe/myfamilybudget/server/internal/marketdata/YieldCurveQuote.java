package com.moe.myfamilybudget.server.internal.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Points de la courbe des taux des emprunts d'État de la zone euro notés AAA (BCE, modèle de
 * Svensson), en fraction (0,0349 pour 3,49 %).
 *
 * Les taux sont en <b>composition continue</b>, pas en taux annuel effectif : pour comparer à un
 * taux de livret ou de prêt, utiliser exp(taux) - 1. Les taux forwards instantanés sont ceux que le
 * marché anticipe pour le court terme à l'horizon indiqué (consensus implicite du marché).
 *
 * @param asOf       jour de cotation
 * @param spot2y     taux spot à 2 ans
 * @param spot10y    taux spot à 10 ans
 * @param forward1y  taux forward instantané dans 1 an
 * @param forward2y  taux forward instantané dans 2 ans
 * @param forward5y  taux forward instantané dans 5 ans
 * @param forward10y taux forward instantané dans 10 ans
 */
public record YieldCurveQuote(
        LocalDate asOf,
        BigDecimal spot2y,
        BigDecimal spot10y,
        BigDecimal forward1y,
        BigDecimal forward2y,
        BigDecimal forward5y,
        BigDecimal forward10y) {
}
