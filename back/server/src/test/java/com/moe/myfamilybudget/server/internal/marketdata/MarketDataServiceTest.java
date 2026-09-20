package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static RegulatedRatesQuote regulated(YearMonth asOf, String livretA) {
        return new RegulatedRatesQuote(asOf, bd(livretA), bd(livretA), bd("0.025"));
    }

    private static MortgageRateQuote mortgage(String rate) {
        return new MortgageRateQuote(YearMonth.of(2026, 7), bd(rate), "MIR1.X");
    }

    private static YieldCurveQuote curve() {
        return new YieldCurveQuote(LocalDate.of(2026, 9, 17), bd("0.0315"), bd("0.0349"), bd("0.033"), bd("0.033"),
                bd("0.0342"), bd("0.0399"));
    }

    /** Source non configurée : ne doit jamais être interrogée. */
    private static final MortgageRateProvider NO_MORTGAGE = new MortgageRateProvider() {
        @Override
        public Optional<MortgageRateQuote> fetchLatest() {
            throw new AssertionError("Source non configurée interrogée");
        }

        @Override
        public boolean isConfigured() {
            return false;
        }
    };

    private static final YieldCurveProvider NO_CURVE = new YieldCurveProvider() {
        @Override
        public Optional<YieldCurveQuote> fetchLatest() {
            throw new AssertionError("Source non configurée interrogée");
        }

        @Override
        public boolean isConfigured() {
            return false;
        }
    };

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

    private static MarketDataService service(RegulatedRatesProvider r, MortgageRateProvider m, YieldCurveProvider c,
            MarketSnapshotStore store) {
        return new MarketDataService(r, m, c, store, CLOCK);
    }

    @Test
    @DisplayName("Sans instantané, current() est UNAVAILABLE partout et n'appelle jamais les sources")
    void currentWithoutSnapshot() {
        MarketDataService service = service(() -> {
            throw new AssertionError("La source ne doit pas être appelée par current()");
        }, NO_MORTGAGE, NO_CURVE, new InMemoryStore());

        MarketRatesView view = service.current();

        assertEquals(RegulatedRateFreshness.Status.UNAVAILABLE, view.regulatedStatus());
        assertEquals(RegulatedRateFreshness.Status.UNAVAILABLE, view.mortgageStatus());
        assertEquals(RegulatedRateFreshness.Status.UNAVAILABLE, view.yieldCurveStatus());
        assertFalse(view.mortgageConfigured());
        assertNull(view.fetchedAt());
        assertEquals(LocalDate.of(2026, 8, 1), view.lastRevisionDate());
    }

    @Test
    @DisplayName("refresh() persiste l'instantané et signale une donnée de mars comme périmée en septembre")
    void refreshStoresSnapshotAndFlagsStale() {
        InMemoryStore store = new InMemoryStore();
        MarketDataService service = service(() -> Optional.of(regulated(YearMonth.of(2026, 3), "0.015")),
                NO_MORTGAGE, NO_CURVE, store);

        MarketRatesView view = service.refresh();

        assertEquals(1, store.saves);
        assertEquals(CLOCK.instant(), view.fetchedAt());
        assertEquals(RegulatedRateFreshness.Status.STALE, view.regulatedStatus());
        assertEquals(0, bd("0.015").compareTo(view.regulatedRates().livretA()));
        assertNull(view.lastRefreshError());
    }

    @Test
    @DisplayName("Une source non configurée est ignorée sans erreur")
    void unconfiguredSourceIsSkipped() {
        MarketDataService service = service(() -> Optional.of(regulated(YearMonth.of(2026, 8), "0.017")),
                NO_MORTGAGE, NO_CURVE, new InMemoryStore());

        MarketRatesView view = service.refresh();

        assertNull(view.lastRefreshError());
        assertFalse(view.mortgageConfigured());
        assertEquals(RegulatedRateFreshness.Status.FRESH, view.regulatedStatus());
    }

    @Test
    @DisplayName("Les trois sources sont rafraîchies et persistées ensemble")
    void refreshesAllSources() {
        InMemoryStore store = new InMemoryStore();
        MarketDataService service = service(() -> Optional.of(regulated(YearMonth.of(2026, 8), "0.017")),
                () -> Optional.of(mortgage("0.0321")), () -> Optional.of(curve()), store);

        MarketRatesView view = service.refresh();

        assertEquals(1, store.saves);
        assertTrue(view.mortgageConfigured());
        assertEquals(0, bd("0.0321").compareTo(view.mortgageRate().rate()));
        assertEquals(RegulatedRateFreshness.Status.FRESH, view.mortgageStatus());
        assertEquals(LocalDate.of(2026, 9, 17), view.yieldCurve().asOf());
        assertEquals(RegulatedRateFreshness.Status.FRESH, view.yieldCurveStatus());
        assertNull(view.lastRefreshError());
    }

    @Test
    @DisplayName("Une source en échec ne bloque pas les autres et son erreur est signalée")
    void failingSourceDoesNotBlockOthers() {
        InMemoryStore store = new InMemoryStore();
        MarketDataService service = service(() -> Optional.of(regulated(YearMonth.of(2026, 8), "0.017")),
                () -> Optional.of(mortgage("0.0321")), () -> {
                    throw new MarketDataException("BCE injoignable");
                }, store);

        MarketRatesView view = service.refresh();

        assertEquals(1, store.saves);
        assertNotNull(view.regulatedRates());
        assertNotNull(view.mortgageRate());
        assertNull(view.yieldCurve());
        assertEquals("BCE : BCE injoignable", view.lastRefreshError());
    }

    @Test
    @DisplayName("Une source en échec conserve sa donnée précédente pendant que les autres se mettent à jour")
    void failingSourceKeepsItsPreviousData() {
        InMemoryStore store = new InMemoryStore();
        boolean[] curveFails = {false};
        String[] livretA = {"0.017"};
        MarketDataService service = service(() -> Optional.of(regulated(YearMonth.of(2026, 8), livretA[0])),
                () -> Optional.of(mortgage("0.0321")), () -> {
                    if (curveFails[0]) {
                        throw new MarketDataException("BCE injoignable");
                    }
                    return Optional.of(curve());
                }, store);
        service.refresh();
        curveFails[0] = true;
        livretA[0] = "0.018";

        MarketRatesView view = service.refresh();

        assertEquals(2, store.saves);
        assertEquals(0, bd("0.018").compareTo(view.regulatedRates().livretA()));
        assertNotNull(view.yieldCurve());
        assertEquals("BCE : BCE injoignable", view.lastRefreshError());
    }

    @Test
    @DisplayName("Quand toutes les sources échouent, rien n'est enregistré et les erreurs sont concaténées")
    void allSourcesFail() {
        InMemoryStore store = new InMemoryStore();
        MarketDataService service = service(() -> {
            throw new MarketDataException("CDC HS");
        }, () -> Optional.empty(), () -> {
            throw new MarketDataException("BCE HS");
        }, store);

        MarketRatesView view = service.refresh();

        assertEquals(0, store.saves);
        assertEquals(RegulatedRateFreshness.Status.UNAVAILABLE, view.regulatedStatus());
        assertTrue(view.lastRefreshError().contains("Caisse des Dépôts : CDC HS"));
        assertTrue(view.lastRefreshError().contains("Banque de France : aucune donnée renvoyée"));
        assertTrue(view.lastRefreshError().contains("BCE : BCE HS"));
    }

    @Test
    @DisplayName("current() recharge l'instantané persisté au premier appel (redémarrage sans réseau)")
    void loadsPersistedSnapshot() {
        InMemoryStore store = new InMemoryStore();
        store.content = new MarketSnapshot(Instant.parse("2026-08-05T10:00:00Z"),
                regulated(YearMonth.of(2026, 8), "0.017"), mortgage("0.0321"), curve());
        MarketDataService service = service(() -> {
            throw new AssertionError("La source ne doit pas être appelée");
        }, NO_MORTGAGE, NO_CURVE, store);

        MarketRatesView view = service.current();

        assertEquals(Instant.parse("2026-08-05T10:00:00Z"), view.fetchedAt());
        assertEquals(RegulatedRateFreshness.Status.FRESH, view.regulatedStatus());
        assertNotNull(view.mortgageRate());
        assertNotNull(view.yieldCurve());
    }
}
