package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * La forme des enregistrements (champs series_key, time_period, obs_value, ...) est celle d'une
 * réponse réelle du dataset « observations » de Webstat ; les valeurs de ce fixture sont illustratives.
 */
class BdfMortgageRateClientTest {

    private static final String SERIES = "MIR1.M.FR.B.A22HR.A.5.A.2254U6.EUR.N";

    private static final String SAMPLE = """
            {"total_count": 120, "results": [{"dataset_id": "MIR1", "series_key": "MIR1.M.FR.B.A22HR.A.5.A.2254U6.EUR.N",
            "title_fr": "Nouveaux crédits à l'habitat hors renégociations", "time_period": "2026-05",
            "time_period_start": "2026-05-01", "time_period_end": "2026-05-31", "obs_value": 3.21, "obs_status": "A",
            "updated_at": "2026-07-08T09:00:00+00:00", "deleted_at": null, "obs_conf": "F"}]}
            """;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void startServer(int status, String body, AtomicReference<String> rawQuery,
            AtomicReference<String> authorization, AtomicInteger calls) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private BdfMortgageRateClient client(String apiKey) {
        return new BdfMortgageRateClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/explore/v2.1",
                apiKey, SERIES, 5);
    }

    @Test
    @DisplayName("parse() convertit le pourcentage en fraction et lit le mois de la donnée")
    void parseSample() {
        MortgageRateQuote quote = BdfMortgageRateClient.parse(SAMPLE, SERIES).orElseThrow();

        assertEquals(YearMonth.of(2026, 5), quote.asOf());
        assertEquals(0, new BigDecimal("0.0321").compareTo(quote.rate()));
        assertEquals(SERIES, quote.seriesKey());
    }

    @Test
    @DisplayName("parse() renvoie vide sans enregistrement")
    void parseEmpty() {
        assertTrue(BdfMortgageRateClient.parse("{\"total_count\":0,\"results\":[]}", SERIES).isEmpty());
    }

    @Test
    @DisplayName("parse() lève MarketDataException sur un format inattendu, une autre série ou un taux aberrant")
    void parseInvalid() {
        assertThrows(MarketDataException.class, () -> BdfMortgageRateClient.parse("pas du json", SERIES));
        assertThrows(MarketDataException.class, () -> BdfMortgageRateClient.parse("{\"foo\":1}", SERIES));
        assertThrows(MarketDataException.class, () -> BdfMortgageRateClient.parse(
                SAMPLE.replace(SERIES + "\"", "IFI.M.D52.DAC.DES_DAC\""), SERIES));
        assertThrows(MarketDataException.class, () -> BdfMortgageRateClient.parse(
                SAMPLE.replace("\"2026-05\"", "\"mai\""), SERIES));
        assertThrows(MarketDataException.class, () -> BdfMortgageRateClient.parse(
                SAMPLE.replace("3.21", "null"), SERIES));
        assertThrows(MarketDataException.class, () -> BdfMortgageRateClient.parse(
                SAMPLE.replace("3.21", "45.0"), SERIES));
    }

    @Test
    @DisplayName("fetchLatest() envoie la clé dans l'en-tête Authorization et filtre sur la série, la plus récente d'abord")
    void fetchLatestSendsKeyAndFilter() throws IOException {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(200, SAMPLE, rawQuery, authorization, new AtomicInteger());

        MortgageRateQuote quote = client("secret-key").fetchLatest().orElseThrow();

        assertEquals("Apikey secret-key", authorization.get());
        assertEquals("where=series_key%3D%22MIR1.M.FR.B.A22HR.A.5.A.2254U6.EUR.N%22"
                + "&order_by=time_period_end%20desc&limit=1", rawQuery.get());
        assertEquals(YearMonth.of(2026, 5), quote.asOf());
    }

    @Test
    @DisplayName("Sans clé d'API, la source est non configurée et n'émet aucun appel réseau")
    void notConfiguredWithoutKey() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        startServer(200, SAMPLE, new AtomicReference<>(), new AtomicReference<>(), calls);

        BdfMortgageRateClient client = client("   ");

        assertFalse(client.isConfigured());
        assertTrue(client.fetchLatest().isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("Une erreur HTTP lève MarketDataException sans jamais recopier la clé dans le message")
    void httpErrorDoesNotLeakKey() throws IOException {
        startServer(401, "{}", new AtomicReference<>(), new AtomicReference<>(), new AtomicInteger());

        MarketDataException e = assertThrows(MarketDataException.class, () -> client("secret-key").fetchLatest());

        assertFalse(e.getMessage().contains("secret-key"));
        assertTrue(e.getMessage().contains("401"));
    }
}
