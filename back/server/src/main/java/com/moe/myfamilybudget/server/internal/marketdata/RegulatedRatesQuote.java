package com.moe.myfamilybudget.server.internal.marketdata;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Taux de l'épargne réglementée à une date de référence, exprimés comme dans le reste de
 * l'application : en fraction (0,015 pour 1,5 %), jamais en pourcentage.
 *
 * @param asOf     mois de la donnée publiée par la source (pas la date d'entrée en vigueur du taux)
 * @param livretA  taux du Livret A
 * @param ldds     taux du LDDS (publié aujourd'hui avec le même champ que le Livret A)
 * @param lep      taux du LEP, peut être absent
 */
public record RegulatedRatesQuote(YearMonth asOf, BigDecimal livretA, BigDecimal ldds, BigDecimal lep) {
}
