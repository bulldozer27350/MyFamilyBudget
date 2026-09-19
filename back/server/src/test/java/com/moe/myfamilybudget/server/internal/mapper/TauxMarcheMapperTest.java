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
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRatesQuote;

class TauxMarcheMapperTest {

    private final TauxMarcheMapper mapper = new TauxMarcheMapper();

    @Test
    @DisplayName("toDto() d'une donnée périmée : taux en fraction, statut STALE et message explicite")
    void staleView() {
        RegulatedRatesQuote quote = new RegulatedRatesQuote(YearMonth.of(2026, 3),
                new BigDecimal("0.015"), new BigDecimal("0.015"), new BigDecimal("0.025"));
        MarketRatesView view = new MarketRatesView(Instant.parse("2026-09-19T08:00:00Z"), quote,
                RegulatedRateFreshness.Status.STALE, LocalDate.of(2026, 8, 1), null);

        TauxMarcheDto dto = mapper.toDto(view);

        assertEquals("2026-09-19T08:00:00Z", dto.getFetchedAt());
        assertNull(dto.getLastRefreshError());
        assertEquals("2026-03", dto.getReglementes().getAsOf());
        assertEquals(0, new BigDecimal("0.015").compareTo(dto.getReglementes().getLivretA()));
        assertEquals(0, new BigDecimal("0.025").compareTo(dto.getReglementes().getLep()));
        assertEquals("STALE", dto.getReglementes().getStatus());
        assertEquals("2026-08-01", dto.getReglementes().getLastRevisionDate());
        assertTrue(dto.getReglementes().getMessage().contains("mars 2026"));
        assertTrue(dto.getReglementes().getMessage().contains("possiblement obsolète"));
    }

    @Test
    @DisplayName("toDto() sans données : statut UNAVAILABLE, aucun taux, erreur de rafraîchissement propagée")
    void unavailableView() {
        MarketRatesView view = new MarketRatesView(null, null,
                RegulatedRateFreshness.Status.UNAVAILABLE, LocalDate.of(2026, 8, 1), "Source injoignable");

        TauxMarcheDto dto = mapper.toDto(view);

        assertNull(dto.getFetchedAt());
        assertEquals("Source injoignable", dto.getLastRefreshError());
        assertEquals("UNAVAILABLE", dto.getReglementes().getStatus());
        assertNull(dto.getReglementes().getLivretA());
        assertNull(dto.getReglementes().getAsOf());
    }
}
