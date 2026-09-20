package com.moe.myfamilybudget.server.internal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.TauxMarcheDto;
import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.marketdata.MortgageRateQuote;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness.Status;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRatesQuote;
import com.moe.myfamilybudget.server.internal.marketdata.YieldCurveQuote;

class TauxMarcheMapperTest {

    private final TauxMarcheMapper mapper = new TauxMarcheMapper();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("toDto() d'une donnée périmée : taux en fraction, statut STALE et message explicite")
    void staleView() {
        RegulatedRatesQuote quote = new RegulatedRatesQuote(YearMonth.of(2026, 3), bd("0.015"), bd("0.015"), bd("0.025"));
        MarketRatesView view = new MarketRatesView(Instant.parse("2026-09-19T08:00:00Z"), quote,
                Status.STALE, LocalDate.of(2026, 8, 1), null, Status.UNAVAILABLE, false, null, Status.UNAVAILABLE, null);

        TauxMarcheDto dto = mapper.toDto(view);

        assertEquals("2026-09-19T08:00:00Z", dto.getFetchedAt());
        assertNull(dto.getLastRefreshError());
        assertEquals("2026-03", dto.getReglementes().getAsOf());
        assertEquals(0, bd("0.015").compareTo(dto.getReglementes().getLivretA()));
        assertEquals(0, bd("0.025").compareTo(dto.getReglementes().getLep()));
        assertEquals("STALE", dto.getReglementes().getStatus());
        assertEquals("2026-08-01", dto.getReglementes().getLastRevisionDate());
        assertTrue(dto.getReglementes().getMessage().contains("mars 2026"));
        assertTrue(dto.getReglementes().getMessage().contains("possiblement obsolète"));
    }

    @Test
    @DisplayName("toDto() sans données : statut UNAVAILABLE, aucun taux, erreur de rafraîchissement propagée")
    void unavailableView() {
        MarketRatesView view = new MarketRatesView(null, null, Status.UNAVAILABLE, LocalDate.of(2026, 8, 1),
                null, Status.UNAVAILABLE, true, null, Status.UNAVAILABLE, "Caisse des Dépôts : Source injoignable");

        TauxMarcheDto dto = mapper.toDto(view);

        assertNull(dto.getFetchedAt());
        assertEquals("Caisse des Dépôts : Source injoignable", dto.getLastRefreshError());
        assertEquals("UNAVAILABLE", dto.getReglementes().getStatus());
        assertNull(dto.getReglementes().getLivretA());
        assertNull(dto.getReglementes().getAsOf());
        assertEquals("UNAVAILABLE", dto.getCreditImmobilier().getStatus());
        assertEquals("UNAVAILABLE", dto.getCourbeTaux().getStatus());
    }

    @Test
    @DisplayName("Taux des crédits : valeur, mois et message ; NOT_CONFIGURED explicite quand la clé d'API manque")
    void mortgageRate() {
        MortgageRateQuote quote = new MortgageRateQuote(YearMonth.of(2026, 5), bd("0.0321"), "MIR1.X");
        MarketRatesView configured = new MarketRatesView(Instant.parse("2026-09-19T08:00:00Z"), null, Status.UNAVAILABLE,
                LocalDate.of(2026, 8, 1), quote, Status.STALE, true, null, Status.UNAVAILABLE, null);
        MarketRatesView notConfigured = new MarketRatesView(null, null, Status.UNAVAILABLE, LocalDate.of(2026, 8, 1),
                null, Status.UNAVAILABLE, false, null, Status.UNAVAILABLE, null);

        var stale = mapper.toDto(configured).getCreditImmobilier();
        var missingKey = mapper.toDto(notConfigured).getCreditImmobilier();

        assertEquals("2026-05", stale.getAsOf());
        assertEquals(0, bd("0.0321").compareTo(stale.getRate()));
        assertEquals("STALE", stale.getStatus());
        assertTrue(stale.getMessage().contains("mai 2026"));
        assertEquals("NOT_CONFIGURED", missingKey.getStatus());
        assertNull(missingKey.getRate());
        assertTrue(missingKey.getMessage().contains("MYFAMILYBUDGET_BDF_API_KEY"));
    }

    @Test
    @DisplayName("Courbe des taux : les six points en fraction, le jour de cotation et l'avertissement de composition continue")
    void yieldCurve() {
        YieldCurveQuote curve = new YieldCurveQuote(LocalDate.of(2026, 9, 17), bd("0.031481938332"),
                bd("0.034875063501"), bd("0.033048290045"), bd("0.033049365222"), bd("0.034150346282"), bd("0.039938667167"));
        MarketRatesView view = new MarketRatesView(Instant.parse("2026-09-19T08:00:00Z"), null, Status.UNAVAILABLE,
                LocalDate.of(2026, 8, 1), null, Status.UNAVAILABLE, false, curve, Status.FRESH, null);

        var dto = mapper.toDto(view).getCourbeTaux();

        assertEquals("2026-09-17", dto.getAsOf());
        assertEquals(0, bd("0.031481938332").compareTo(dto.getSpot2y()));
        assertEquals(0, bd("0.034875063501").compareTo(dto.getSpot10y()));
        assertEquals(0, bd("0.033048290045").compareTo(dto.getForward1y()));
        assertEquals(0, bd("0.039938667167").compareTo(dto.getForward10y()));
        assertEquals("FRESH", dto.getStatus());
        assertTrue(dto.getMessage().contains("composition continue"));
        assertTrue(dto.getMessage().contains("17 septembre 2026"));
    }
}
