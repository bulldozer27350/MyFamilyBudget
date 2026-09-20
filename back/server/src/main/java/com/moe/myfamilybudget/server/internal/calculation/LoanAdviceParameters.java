package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Hypothèses de l'analyse des prêts. Les valeurs par défaut sont des heuristiques courantes
 * (repères de courtiers), pas des règles : elles sont exposées dans la réponse pour que
 * l'utilisateur sache sur quoi repose chaque verdict.
 *
 * @param marketRate                     taux de marché d'un crédit immobilier neuf (fraction), null si inconnu
 * @param repayMarginRate                écart (fraction) sous lequel coût du prêt et rendement sont jugés équivalents
 * @param renegotiationMinGapRate        écart minimal taux du prêt / taux de marché pour envisager une renégociation
 * @param renegotiationMinCrd            capital restant dû minimal (€)
 * @param renegotiationMinRemainingMonths durée restante minimale (mois)
 * @param renegotiationFixedCosts        frais de dossier, garantie et mainlevée estimés (€), hors indemnité
 * @param flatTaxRate                    fiscalité appliquée aux rendements des placements liquides hors livrets (PFU)
 */
public record LoanAdviceParameters(
        BigDecimal marketRate,
        BigDecimal repayMarginRate,
        BigDecimal renegotiationMinGapRate,
        BigDecimal renegotiationMinCrd,
        int renegotiationMinRemainingMonths,
        BigDecimal renegotiationFixedCosts,
        BigDecimal flatTaxRate) {

    /** Plage de taux de marché jugée plausible (0 % à 30 %) ; au-delà, la valeur est ignorée. */
    public static final BigDecimal MAX_PLAUSIBLE_MARKET_RATE = new BigDecimal("0.30");

    public static LoanAdviceParameters defaults(BigDecimal marketRate) {
        return new LoanAdviceParameters(
                marketRate,
                new BigDecimal("0.005"),
                new BigDecimal("0.007"),
                new BigDecimal("70000"),
                84,
                new BigDecimal("1500"),
                new BigDecimal("0.30"));
    }
}
