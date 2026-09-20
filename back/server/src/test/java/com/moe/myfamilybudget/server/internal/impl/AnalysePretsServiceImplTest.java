package com.moe.myfamilybudget.server.internal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.server.internal.impl.AnalysePretsServiceImpl.ResolvedMarketRate;
import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.marketdata.MortgageRateQuote;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness.Status;

class AnalysePretsServiceImplTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static MarketRatesView market(MortgageRateQuote mortgage, Status status) {
        return new MarketRatesView(null, null, Status.UNAVAILABLE, LocalDate.of(2026, 8, 1),
                mortgage, status, true, null, Status.UNAVAILABLE, null);
    }

    private static final MortgageRateQuote BDF = new MortgageRateQuote(YearMonth.of(2026, 5), bd("0.0321"), "MIR1.X");

    @Test
    @DisplayName("Le paramètre de requête l'emporte sur la saisie manuelle et sur la Banque de France")
    void requestParameterWins() {
        LoanAdviceParameters saved = LoanAdviceParameters.defaults(bd("0.035"));

        ResolvedMarketRate resolved = AnalysePretsServiceImpl.resolveMarketRate(bd("0.03"), saved, market(BDF, Status.FRESH));

        assertEquals(0, bd("0.03").compareTo(resolved.rate()));
        assertEquals("Paramètre de requête", resolved.source());
    }

    @Test
    @DisplayName("Le taux saisi dans les hypothèses l'emporte sur la Banque de France")
    void manualRateBeatsBdf() {
        LoanAdviceParameters saved = LoanAdviceParameters.defaults(bd("0.035"));

        ResolvedMarketRate resolved = AnalysePretsServiceImpl.resolveMarketRate(null, saved, market(BDF, Status.FRESH));

        assertEquals(0, bd("0.035").compareTo(resolved.rate()));
        assertEquals("Saisie manuelle", resolved.source());
    }

    @Test
    @DisplayName("Sans saisie, le taux de la Banque de France est utilisé et son mois est indiqué")
    void fallsBackToBdf() {
        ResolvedMarketRate resolved = AnalysePretsServiceImpl.resolveMarketRate(
                null, LoanAdviceParameters.defaults(null), market(BDF, Status.FRESH));

        assertEquals(0, bd("0.0321").compareTo(resolved.rate()));
        assertEquals("Banque de France, mai 2026", resolved.source());
    }

    @Test
    @DisplayName("Un taux Banque de France ancien est signalé comme tel")
    void staleBdfIsFlagged() {
        ResolvedMarketRate resolved = AnalysePretsServiceImpl.resolveMarketRate(
                null, LoanAdviceParameters.defaults(null), market(BDF, Status.STALE));

        assertTrue(resolved.source().endsWith("donnée ancienne"));
    }

    @Test
    @DisplayName("Aucune source de taux : ni taux ni origine")
    void nothingAvailable() {
        ResolvedMarketRate resolved = AnalysePretsServiceImpl.resolveMarketRate(
                null, LoanAdviceParameters.defaults(null), market(null, Status.UNAVAILABLE));

        assertNull(resolved.rate());
        assertNull(resolved.source());
    }
}
