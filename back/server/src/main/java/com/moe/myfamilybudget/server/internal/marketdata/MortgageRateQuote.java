package com.moe.myfamilybudget.server.internal.marketdata;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Taux moyen des nouveaux crédits à l'habitat (hors renégociations) publié par la Banque de France,
 * en fraction (0,0321 pour 3,21 %).
 *
 * @param asOf       mois de la donnée (publiée avec plusieurs semaines de décalage)
 * @param rate       taux moyen
 * @param seriesKey  clé de la série Webstat utilisée
 */
public record MortgageRateQuote(YearMonth asOf, BigDecimal rate, String seriesKey) {
}
