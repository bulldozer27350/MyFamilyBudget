package com.moe.myfamilybudget.server.internal.marketdata;

import java.time.Instant;

/**
 * Dernier instantané de données de marché récupéré avec succès, persisté pour que l'application
 * reste utilisable hors ligne ou quand une source publique est indisponible.
 *
 * @param fetchedAt        instant de la récupération réussie
 * @param regulatedRates   taux de l'épargne réglementée, peut être null
 */
public record MarketSnapshot(Instant fetchedAt, RegulatedRatesQuote regulatedRates) {
}
