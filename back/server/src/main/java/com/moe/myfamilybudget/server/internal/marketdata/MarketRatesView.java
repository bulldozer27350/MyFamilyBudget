package com.moe.myfamilybudget.server.internal.marketdata;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue des données de marché à un instant donné : l'instantané persisté, complété de son statut
 * de fraîcheur (qui évolue avec le temps même si l'instantané, lui, ne change pas).
 *
 * @param fetchedAt          instant du dernier instantané réussi, null s'il n'y en a jamais eu
 * @param regulatedRates     taux de l'épargne réglementée, null si indisponibles
 * @param regulatedStatus    fraîcheur de ces taux
 * @param lastRevisionDate   dernière révision légale des taux (1er février ou 1er août)
 * @param lastRefreshError   message de la dernière tentative de rafraîchissement échouée, sinon null
 */
public record MarketRatesView(
        Instant fetchedAt,
        RegulatedRatesQuote regulatedRates,
        RegulatedRateFreshness.Status regulatedStatus,
        LocalDate lastRevisionDate,
        String lastRefreshError) {
}
