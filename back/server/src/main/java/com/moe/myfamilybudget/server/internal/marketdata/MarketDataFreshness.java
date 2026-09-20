package com.moe.myfamilybudget.server.internal.marketdata;

import java.time.LocalDate;
import java.time.YearMonth;

import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness.Status;

/**
 * Fraîcheur des sources de marché autres que les taux réglementés (voir
 * {@link RegulatedRateFreshness}, qui gère leur calendrier de révision propre).
 */
public final class MarketDataFreshness {

    /** Le taux moyen des crédits est publié avec ~6 semaines de retard : au-delà de 3 mois, il est ancien. */
    public static final int MORTGAGE_MAX_AGE_MONTHS = 3;
    /** La courbe est quotidienne (jours ouvrés) : au-delà de 10 jours, elle est ancienne. */
    public static final int YIELD_CURVE_MAX_AGE_DAYS = 10;

    private MarketDataFreshness() {
    }

    public static Status evaluateMortgageRate(YearMonth asOf, LocalDate today) {
        if (asOf == null) {
            return Status.UNAVAILABLE;
        }
        return asOf.isBefore(YearMonth.from(today).minusMonths(MORTGAGE_MAX_AGE_MONTHS)) ? Status.STALE : Status.FRESH;
    }

    public static Status evaluateYieldCurve(LocalDate asOf, LocalDate today) {
        if (asOf == null) {
            return Status.UNAVAILABLE;
        }
        return asOf.isBefore(today.minusDays(YIELD_CURVE_MAX_AGE_DAYS)) ? Status.STALE : Status.FRESH;
    }
}
