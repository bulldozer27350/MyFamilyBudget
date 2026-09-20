package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness.Status;

class MarketDataFreshnessTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    @Test
    @DisplayName("Taux des crédits : ancien au-delà de 3 mois, à jour en deçà, UNAVAILABLE sans donnée")
    void mortgageRate() {
        assertEquals(Status.FRESH, MarketDataFreshness.evaluateMortgageRate(YearMonth.of(2026, 7), TODAY));
        assertEquals(Status.FRESH, MarketDataFreshness.evaluateMortgageRate(YearMonth.of(2026, 6), TODAY));
        assertEquals(Status.STALE, MarketDataFreshness.evaluateMortgageRate(YearMonth.of(2026, 5), TODAY));
        assertEquals(Status.UNAVAILABLE, MarketDataFreshness.evaluateMortgageRate(null, TODAY));
    }

    @Test
    @DisplayName("Courbe des taux : ancienne au-delà de 10 jours, à jour en deçà, UNAVAILABLE sans donnée")
    void yieldCurve() {
        assertEquals(Status.FRESH, MarketDataFreshness.evaluateYieldCurve(LocalDate.of(2026, 9, 17), TODAY));
        assertEquals(Status.FRESH, MarketDataFreshness.evaluateYieldCurve(LocalDate.of(2026, 9, 9), TODAY));
        assertEquals(Status.STALE, MarketDataFreshness.evaluateYieldCurve(LocalDate.of(2026, 9, 8), TODAY));
        assertEquals(Status.UNAVAILABLE, MarketDataFreshness.evaluateYieldCurve(null, TODAY));
    }
}
