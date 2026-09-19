package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class MarketSnapshotCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Aller-retour JSON sans perte de précision sur les taux")
    void roundTrip() {
        MarketSnapshot original = new MarketSnapshot(
                Instant.parse("2026-09-19T08:00:00Z"),
                new RegulatedRatesQuote(YearMonth.of(2026, 3),
                        new BigDecimal("0.015"), new BigDecimal("0.015"), new BigDecimal("0.025")));

        MarketSnapshot decoded = MarketSnapshotCodec.fromJson(MarketSnapshotCodec.toJson(original, mapper), mapper);

        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("Aller-retour d'un instantané sans taux réglementés ni LEP")
    void roundTripWithoutRates() {
        MarketSnapshot noRates = new MarketSnapshot(Instant.parse("2026-09-19T08:00:00Z"), null);
        MarketSnapshot withoutLep = new MarketSnapshot(Instant.parse("2026-09-19T08:00:00Z"),
                new RegulatedRatesQuote(YearMonth.of(2026, 8), new BigDecimal("0.017"), new BigDecimal("0.017"), null));

        assertNull(MarketSnapshotCodec.fromJson(MarketSnapshotCodec.toJson(noRates, mapper), mapper).regulatedRates());
        assertEquals(withoutLep,
                MarketSnapshotCodec.fromJson(MarketSnapshotCodec.toJson(withoutLep, mapper), mapper));
    }

    @Test
    @DisplayName("fromJson() lève MarketDataException sur un contenu illisible")
    void invalid() {
        assertThrows(MarketDataException.class, () -> MarketSnapshotCodec.fromJson("{ pas du json", mapper));
        assertThrows(MarketDataException.class, () -> MarketSnapshotCodec.fromJson("{\"fetchedAt\":\"hier\"}", mapper));
    }
}
