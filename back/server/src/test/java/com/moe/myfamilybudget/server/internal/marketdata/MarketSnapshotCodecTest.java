package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    @DisplayName("Aller-retour d'un instantané complet : taux réglementés, taux des crédits et courbe des taux")
    void roundTripFullSnapshot() {
        MarketSnapshot full = new MarketSnapshot(Instant.parse("2026-09-19T08:00:00Z"),
                new RegulatedRatesQuote(YearMonth.of(2026, 8), new BigDecimal("0.017"), new BigDecimal("0.017"), new BigDecimal("0.022")),
                new MortgageRateQuote(YearMonth.of(2026, 5), new BigDecimal("0.0321"), "MIR1.M.FR.B.A22HR.A.5.A.2254U6.EUR.N"),
                new YieldCurveQuote(LocalDate.of(2026, 9, 17), new BigDecimal("0.031481938332"),
                        new BigDecimal("0.034875063501"), new BigDecimal("0.033048290045"),
                        new BigDecimal("0.033049365222"), new BigDecimal("0.034150346282"),
                        new BigDecimal("0.039938667167")));

        assertEquals(full, MarketSnapshotCodec.fromJson(MarketSnapshotCodec.toJson(full, mapper), mapper));
    }

    @Test
    @DisplayName("Un instantané persisté par l'ancienne version (taux réglementés seuls) reste lisible")
    void readsSnapshotWrittenBeforeNewSources() {
        String legacy = "{\"fetchedAt\":\"2026-09-19T08:00:00Z\",\"regulatedRates\":{\"asOf\":\"2026-03\","
                + "\"livretA\":\"0.015\",\"ldds\":\"0.015\",\"lep\":\"0.025\"}}";

        MarketSnapshot decoded = MarketSnapshotCodec.fromJson(legacy, mapper);

        assertEquals(YearMonth.of(2026, 3), decoded.regulatedRates().asOf());
        assertNull(decoded.mortgageRate());
        assertNull(decoded.yieldCurve());
    }

    @Test
    @DisplayName("fromJson() lève MarketDataException sur un contenu illisible")
    void invalid() {
        assertThrows(MarketDataException.class, () -> MarketSnapshotCodec.fromJson("{ pas du json", mapper));
        assertThrows(MarketDataException.class, () -> MarketSnapshotCodec.fromJson("{\"fetchedAt\":\"hier\"}", mapper));
    }
}
