package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketDataServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneId.of("Europe/Paris"));

    private static RegulatedRatesQuote quote(YearMonth asOf, String livretA) {
        BigDecimal rate = new BigDecimal(livretA);
        return new RegulatedRatesQuote(asOf, rate, rate, new BigDecimal("0.025"));
    }

    /** Store en mémoire qui compte les sauvegardes. */
    private static final class InMemoryStore implements MarketSnapshotStore {
        MarketSnapshot content;
        int saves;

        @Override
        public Optional<MarketSnapshot> load() {
            return Optional.ofNullable(content);
        }

        @Override
        public void save(MarketSnapshot snapshot) {
            content = snapshot;
            saves++;
        }
    }

    @Test
    @DisplayName("Sans instantané, current() est UNAVAILABLE et n'appelle jamais la source")
    void currentWithoutSnapshot() {
        MarketDataService service = new MarketDataService(() -> {
            throw new AssertionError("La source ne doit pas être appelée par current()");
        }, new InMemoryStore(), CLOCK);

        MarketRatesView view = service.current();

        assertEquals(RegulatedRateFreshness.Status.UNAVAILABLE, view.regulatedStatus());
        assertNull(view.regulatedRates());
        assertNull(view.fetchedAt());
        assertEquals(LocalDate.of(2026, 8, 1), view.lastRevisionDate());
    }

    @Test
    @DisplayName("refresh() persiste l'instantané et signale une donnée de mars comme périmée en septembre")
    void refreshStoresSnapshotAndFlagsStale() {
        InMemoryStore store = new InMemoryStore();
        MarketDataService service = new MarketDataService(
                () -> Optional.of(quote(YearMonth.of(2026, 3), "0.015")), store, CLOCK);

        MarketRatesView view = service.refresh();

        assertEquals(1, store.saves);
        assertEquals(CLOCK.instant(), view.fetchedAt());
        assertEquals(RegulatedRateFreshness.Status.STALE, view.regulatedStatus());
        assertEquals(0, new BigDecimal("0.015").compareTo(view.regulatedRates().livretA()));
        assertNull(view.lastRefreshError());
    }

    @Test
    @DisplayName("refresh() avec une donnée du mois de révision donne le statut FRESH")
    void refreshFresh() {
        MarketDataService service = new MarketDataService(
                () -> Optional.of(quote(YearMonth.of(2026, 8), "0.017")), new InMemoryStore(), CLOCK);

        assertEquals(RegulatedRateFreshness.Status.FRESH, service.refresh().regulatedStatus());
    }

    @Test
    @DisplayName("Une source en échec conserve le dernier instantané et expose l'erreur")
    void failingProviderKeepsPreviousSnapshot() {
        InMemoryStore store = new InMemoryStore();
        boolean[] fail = {false};
        MarketDataService service = new MarketDataService(() -> {
            if (fail[0]) {
                throw new MarketDataException("Source injoignable");
            }
            return Optional.of(quote(YearMonth.of(2026, 8), "0.017"));
        }, store, CLOCK);
        service.refresh();
        fail[0] = true;

        MarketRatesView view = service.refresh();

        assertEquals(1, store.saves);
        assertNotNull(view.regulatedRates());
        assertEquals(0, new BigDecimal("0.017").compareTo(view.regulatedRates().livretA()));
        assertEquals("Source injoignable", view.lastRefreshError());
    }

    @Test
    @DisplayName("Une source qui ne publie rien laisse l'instantané inchangé et signale l'anomalie")
    void emptyProvider() {
        InMemoryStore store = new InMemoryStore();
        MarketDataService service = new MarketDataService(Optional::empty, store, CLOCK);

        MarketRatesView view = service.refresh();

        assertEquals(0, store.saves);
        assertEquals(RegulatedRateFreshness.Status.UNAVAILABLE, view.regulatedStatus());
        assertTrue(view.lastRefreshError() != null && !view.lastRefreshError().isBlank());
    }

    @Test
    @DisplayName("current() recharge l'instantané persisté au premier appel (redémarrage sans réseau)")
    void loadsPersistedSnapshot() {
        InMemoryStore store = new InMemoryStore();
        store.content = new MarketSnapshot(Instant.parse("2026-08-05T10:00:00Z"), quote(YearMonth.of(2026, 8), "0.017"));
        MarketDataService service = new MarketDataService(() -> {
            throw new AssertionError("La source ne doit pas être appelée");
        }, store, CLOCK);

        MarketRatesView view = service.current();

        assertEquals(Instant.parse("2026-08-05T10:00:00Z"), view.fetchedAt());
        assertEquals(RegulatedRateFreshness.Status.FRESH, view.regulatedStatus());
    }
}
