package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Le fichier {@code marketdata/ecb-yc-sample.csv} est la sortie réelle de l'API de la BCE
 * (requête sur les six séries, lastNObservations=1, format=csvdata).
 */
class EcbYieldCurveClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String sample() throws IOException {
        try (InputStream in = EcbYieldCurveClientTest.class.getResourceAsStream("/marketdata/ecb-yc-sample.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertRate(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "attendu " + expected + " mais " + actual);
    }

    @Test
    @DisplayName("parse() lit les six séries d'une sortie réelle de la BCE et convertit les pourcentages en fractions")
    void parseRealSample() throws IOException {
        YieldCurveQuote curve = EcbYieldCurveClient.parse(sample()).orElseThrow();

        assertEquals(LocalDate.of(2026, 9, 17), curve.asOf());
        assertRate("0.031481938332", curve.spot2y());
        assertRate("0.034875063501", curve.spot10y());
        assertRate("0.033048290045", curve.forward1y());
        assertRate("0.033049365222", curve.forward2y());
        assertRate("0.034150346282", curve.forward5y());
        assertRate("0.039938667167", curve.forward10y());
    }

    @Test
    @DisplayName("parse() renvoie vide quand la réponse ne contient que l'en-tête ou rien")
    void parseEmpty() {
        assertTrue(EcbYieldCurveClient.parse("").isEmpty());
        assertTrue(EcbYieldCurveClient.parse("KEY,DATA_TYPE_FM,TIME_PERIOD,OBS_VALUE\n").isEmpty());
    }

    @Test
    @DisplayName("parse() lève MarketDataException si une série manque, si une colonne manque ou si une valeur est illisible")
    void parseInvalid() throws IOException {
        String withoutIf10y = String.join("\n", sample().lines().filter(l -> !l.contains(".IF_10Y,")).toList());
        assertThrows(MarketDataException.class, () -> EcbYieldCurveClient.parse(withoutIf10y));
        assertThrows(MarketDataException.class, () -> EcbYieldCurveClient.parse("A,B\n1,2\n"));
        assertThrows(MarketDataException.class, () -> EcbYieldCurveClient.parse(
                "DATA_TYPE_FM,TIME_PERIOD,OBS_VALUE\nSR_2Y,2026-09-17,abc\n"));
    }

    @Test
    @DisplayName("fetchLatest() interroge le dataset YC avec les six séries jointes par « + » et lastNObservations=1")
    void fetchLatestCallsExpectedUrl() throws IOException {
        AtomicReference<String> rawPath = new AtomicReference<>();
        AtomicReference<String> rawQuery = new AtomicReference<>();
        String body = sample();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            rawPath.set(exchange.getRequestURI().getRawPath());
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        YieldCurveQuote curve = new EcbYieldCurveClient("http://127.0.0.1:" + server.getAddress().getPort(), 5)
                .fetchLatest().orElseThrow();

        assertEquals("/service/data/YC/B.U2.EUR.4F.G_N_A.SV_C_YM.SR_2Y+SR_10Y+IF_1Y+IF_2Y+IF_5Y+IF_10Y", rawPath.get());
        assertEquals("lastNObservations=1&format=csvdata", rawQuery.get());
        assertEquals(LocalDate.of(2026, 9, 17), curve.asOf());
    }

    @Test
    @DisplayName("fetchLatest() lève MarketDataException sur un statut HTTP différent de 200")
    void fetchLatestHttpError() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        assertThrows(MarketDataException.class,
                () -> new EcbYieldCurveClient("http://127.0.0.1:" + server.getAddress().getPort(), 5).fetchLatest());
    }
}
