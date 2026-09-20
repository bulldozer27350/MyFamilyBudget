package com.moe.myfamilybudget.server.internal.marketdata;

import java.time.Instant;

/**
 * Dernier instantané de données de marché récupéré avec succès, persisté pour que l'application
 * reste utilisable hors ligne ou quand une source publique est indisponible. Chaque source est
 * mise à jour indépendamment : une source en échec laisse sa donnée précédente en place.
 *
 * @param fetchedAt        instant de la dernière mise à jour réussie d'au moins une source
 * @param regulatedRates   taux de l'épargne réglementée, peut être null
 * @param mortgageRate     taux moyen des nouveaux crédits immobiliers, peut être null
 * @param yieldCurve       courbe des taux de la zone euro, peut être null
 */
public record MarketSnapshot(
        Instant fetchedAt,
        RegulatedRatesQuote regulatedRates,
        MortgageRateQuote mortgageRate,
        YieldCurveQuote yieldCurve) {

    /** Instantané ne contenant que les taux réglementés. */
    public MarketSnapshot(Instant fetchedAt, RegulatedRatesQuote regulatedRates) {
        this(fetchedAt, regulatedRates, null, null);
    }
}
