package com.moe.myfamilybudget.server.internal.marketdata;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Adaptateur vers le jeu de données « flux-et-taux-la-ldds-lep » de la Caisse des Dépôts
 * (API Explore v2.1 d'opendata.caissedesdepots.fr).
 *
 * La source publie les taux en pourcentage ({@code tla_tldds_percent}: 1.5) ; ils sont convertis
 * en fraction (0,015) pour rester cohérents avec le reste de l'application. Le taux du Livret A
 * et celui du LDDS partagent un même champ.
 *
 * Utilise le client HTTP du JDK (aucune dépendance supplémentaire) avec des délais explicites.
 */
@Component
public class CdcRegulatedRatesClient implements RegulatedRatesProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String dataset;
    private final Duration timeout;

    public CdcRegulatedRatesClient(
            @Value("${myfamilybudget.market-data.cdc.base-url:https://opendata.caissedesdepots.fr/api/explore/v2.1}") String baseUrl,
            @Value("${myfamilybudget.market-data.cdc.dataset:flux-et-taux-la-ldds-lep}") String dataset,
            @Value("${myfamilybudget.market-data.timeout-seconds:5}") int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.dataset = dataset;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Optional<RegulatedRatesQuote> fetchLatest() {
        HttpRequest request = HttpRequest.newBuilder(buildUri())
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MarketDataException("Appel à la Caisse des Dépôts impossible : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketDataException("Appel à la Caisse des Dépôts interrompu", e);
        }
        if (response.statusCode() != 200) {
            throw new MarketDataException("La Caisse des Dépôts a répondu HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    URI buildUri() {
        return URI.create(baseUrl + "/catalog/datasets/" + encode(dataset)
                + "/records?order_by=" + encode("date desc") + "&limit=1");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Extrait l'enregistrement le plus récent d'une réponse « records » de l'API Explore. */
    static Optional<RegulatedRatesQuote> parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new MarketDataException("Réponse de la Caisse des Dépôts illisible", e);
        }
        JsonNode results = root == null ? null : root.get("results");
        if (results == null || !results.isArray()) {
            throw new MarketDataException("Réponse de la Caisse des Dépôts inattendue : champ 'results' absent");
        }
        if (results.isEmpty()) {
            return Optional.empty();
        }
        JsonNode record = results.get(0);

        YearMonth asOf;
        try {
            asOf = YearMonth.parse(record.path("date").asText(""));
        } catch (DateTimeParseException e) {
            throw new MarketDataException("Date de référence invalide dans la réponse de la Caisse des Dépôts", e);
        }
        BigDecimal livretA = percentToFraction(record.get("tla_tldds_percent"));
        if (livretA == null) {
            throw new MarketDataException("Taux du Livret A absent de la réponse de la Caisse des Dépôts");
        }
        BigDecimal lep = percentToFraction(record.get("tlep_percent"));
        return Optional.of(new RegulatedRatesQuote(asOf, livretA, livretA, lep));
    }

    private static BigDecimal percentToFraction(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText()).divide(HUNDRED);
        } catch (NumberFormatException e) {
            throw new MarketDataException("Taux illisible dans la réponse de la Caisse des Dépôts : " + node.asText(), e);
        }
    }
}
