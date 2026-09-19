package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class CdcRegulatedRatesClientTest {

    private static final String SAMPLE = """
            {
              "total_count": 27,
              "results": [
                {
                  "date": "2026-03",
                  "flux_la_ldds": -0.412074219679994,
                  "flux_la": -0.48780433044999,
                  "flux_ldds": 0.0757301107700243,
                  "flux_lep": -0.118874007639999,
                  "annee": "2026",
                  "tla_tldds_percent": 1.5,
                  "tlep_percent": 2.5
                }
              ]
            }
            """;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("parse() convertit les pourcentages de la source en fractions et lit le mois de référence")
    void parseSample() {
        Optional<RegulatedRatesQuote> quote = CdcRegulatedRatesClient.parse(SAMPLE);

        assertTrue(quote.isPresent());
        assertEquals(YearMonth.of(2026, 3), quote.get().asOf());
        assertEquals(0, new BigDecimal("0.015").compareTo(quote.get().livretA()));
        assertEquals(0, new BigDecimal("0.015").compareTo(quote.get().ldds()));
        assertEquals(0, new BigDecimal("0.025").compareTo(quote.get().lep()));
    }

    @Test
    @DisplayName("parse() accepte l'absence de taux LEP")
    void parseWithoutLep() {
        String json = "{\"results\":[{\"date\":\"2026-08\",\"tla_tldds_percent\":1.7}]}";

        RegulatedRatesQuote quote = CdcRegulatedRatesClient.parse(json).orElseThrow();

        assertEquals(0, new BigDecimal("0.017").compareTo(quote.livretA()));
        assertNull(quote.lep());
    }

    @Test
    @DisplayName("parse() renvoie vide quand la source ne publie aucun enregistrement")
    void parseEmptyResults() {
        assertTrue(CdcRegulatedRatesClient.parse("{\"total_count\":0,\"results\":[]}").isEmpty());
    }

    @Test
    @DisplayName("parse() lève MarketDataException sur un format inattendu")
    void parseInvalid() {
        assertThrows(MarketDataException.class, () -> CdcRegulatedRatesClient.parse("pas du json"));
        assertThrows(MarketDataException.class, () -> CdcRegulatedRatesClient.parse("{\"foo\":1}"));
        assertThrows(MarketDataException.class,
                () -> CdcRegulatedRatesClient.parse("{\"results\":[{\"date\":\"mars\",\"tla_tldds_percent\":1.5}]}"));
        assertThrows(MarketDataException.class,
                () -> CdcRegulatedRatesClient.parse("{\"results\":[{\"date\":\"2026-03\"}]}"));
        assertThrows(MarketDataException.class,
                () -> CdcRegulatedRatesClient.parse("{\"results\":[{\"date\":\"2026-03\",\"tla_tldds_percent\":\"abc\"}]}"));
    }

    @Test
    @DisplayName("fetchLatest() interroge le bon chemin avec order_by encodé et limit=1, puis parse la réponse")
    void fetchLatestCallsExpectedUrl() throws IOException {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        startServer(200, SAMPLE, rawQuery, path);

        RegulatedRatesQuote quote = newClient().fetchLatest().orElseThrow();

        assertEquals("/catalog/datasets/flux-et-taux-la-ldds-lep/records", path.get());
        assertEquals("order_by=date%20desc&limit=1", rawQuery.get());
        assertEquals(YearMonth.of(2026, 3), quote.asOf());
    }

    @Test
    @DisplayName("fetchLatest() lève MarketDataException sur un statut HTTP différent de 200")
    void fetchLatestHttpError() throws IOException {
        startServer(500, "{}", new AtomicReference<>(), new AtomicReference<>());

        assertThrows(MarketDataException.class, () -> newClient().fetchLatest());
    }

    private CdcRegulatedRatesClient newClient() {
        return new CdcRegulatedRatesClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/", "flux-et-taux-la-ldds-lep", 5);
    }

    private void startServer(int status, String body, AtomicReference<String> rawQuery, AtomicReference<String> path)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
