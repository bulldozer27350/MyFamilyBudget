package com.moe.myfamilybudget.server.internal.marketdata;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue des données de marché à un instant donné : l'instantané persisté, complété du statut de
 * fraîcheur de chaque source (qui évolue avec le temps même si l'instantané, lui, ne change pas).
 *
 * @param fetchedAt          instant de la dernière mise à jour réussie, null s'il n'y en a jamais eu
 * @param regulatedRates     taux de l'épargne réglementée, null si indisponibles
 * @param regulatedStatus    fraîcheur de ces taux
 * @param lastRevisionDate   dernière révision légale des taux réglementés (1er février ou 1er août)
 * @param mortgageRate       taux moyen des nouveaux crédits immobiliers, null si indisponible
 * @param mortgageStatus     fraîcheur de ce taux
 * @param mortgageConfigured faux si la source exige une clé d'API non configurée
 * @param yieldCurve         courbe des taux de la zone euro, null si indisponible
 * @param yieldCurveStatus   fraîcheur de cette courbe
 * @param lastRefreshError   erreurs de la dernière tentative de rafraîchissement (une par source en échec), sinon null
 */
public record MarketRatesView(
        Instant fetchedAt,
        RegulatedRatesQuote regulatedRates,
        RegulatedRateFreshness.Status regulatedStatus,
        LocalDate lastRevisionDate,
        MortgageRateQuote mortgageRate,
        RegulatedRateFreshness.Status mortgageStatus,
        boolean mortgageConfigured,
        YieldCurveQuote yieldCurve,
        RegulatedRateFreshness.Status yieldCurveStatus,
        String lastRefreshError) {
}
