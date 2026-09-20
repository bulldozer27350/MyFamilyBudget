package com.moe.myfamilybudget.server.internal.marketdata;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Adaptateur vers Webstat (Banque de France, API Explore v2.1) : taux moyen des nouveaux crédits à
 * l'habitat hors renégociations, série configurable (défaut {@code MIR1.M.FR.B.A22HR.A.5.A.2254U6.EUR.N}).
 *
 * L'API exige une clé, envoyée dans l'en-tête {@code Authorization: Apikey ...} et fournie par la
 * propriété {@code myfamilybudget.market-data.bdf.api-key} (variable d'environnement
 * {@code MYFAMILYBUDGET_BDF_API_KEY}). Sans clé, la source est ignorée sans erreur. La clé n'est
 * jamais journalisée ni renvoyée par l'API de l'application.
 */
@Component
public class BdfMortgageRateClient implements MortgageRateProvider {

    private static final String SOURCE_NAME = "la Banque de France";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Un taux de crédit au-delà de 30 % est jugé aberrant. */
    private static final BigDecimal MAX_PLAUSIBLE_RATE = new BigDecimal("0.30");

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String seriesKey;
    private final Duration timeout;

    public BdfMortgageRateClient(
            @Value("${myfamilybudget.market-data.bdf.base-url:https://webstat.banque-france.fr/api/explore/v2.1}") String baseUrl,
            @Value("${myfamilybudget.market-data.bdf.api-key:}") String apiKey,
            @Value("${myfamilybudget.market-data.bdf.mortgage-series-key:MIR1.M.FR.B.A22HR.A.5.A.2254U6.EUR.N}") String seriesKey,
            @Value("${myfamilybudget.market-data.timeout-seconds:5}") int timeoutSeconds) {
        this.baseUrl = MarketHttp.stripTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.seriesKey = seriesKey;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.httpClient = MarketHttp.newClient(this.timeout);
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public Optional<MortgageRateQuote> fetchLatest() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String json = MarketHttp.get(httpClient, buildUri(), timeout,
                Map.of("Accept", "application/json", "Authorization", "Apikey " + apiKey), SOURCE_NAME);
        return parse(json, seriesKey);
    }

    URI buildUri() {
        return URI.create(baseUrl + "/catalog/datasets/observations/records?where="
                + MarketHttp.encode("series_key=\"" + seriesKey + "\"")
                + "&order_by=" + MarketHttp.encode("time_period_end desc") + "&limit=1");
    }

    /** Extrait l'observation la plus récente d'une réponse « records » du dataset observations. */
    static Optional<MortgageRateQuote> parse(String json, String expectedSeriesKey) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new MarketDataException("Réponse de la Banque de France illisible", e);
        }
        JsonNode results = root == null ? null : root.get("results");
        if (results == null || !results.isArray()) {
            throw new MarketDataException("Réponse de la Banque de France inattendue : champ 'results' absent");
        }
        if (results.isEmpty()) {
            return Optional.empty();
        }
        JsonNode record = results.get(0);

        String returnedKey = record.path("series_key").asText("");
        if (!returnedKey.isEmpty() && !returnedKey.equals(expectedSeriesKey)) {
            throw new MarketDataException("Réponse de la Banque de France inattendue : série " + returnedKey);
        }
        YearMonth asOf;
        try {
            asOf = YearMonth.parse(record.path("time_period").asText(""));
        } catch (DateTimeParseException e) {
            throw new MarketDataException("Période invalide dans la réponse de la Banque de France", e);
        }
        JsonNode value = record.get("obs_value");
        if (value == null || value.isNull() || !value.isNumber()) {
            throw new MarketDataException("Valeur absente dans la réponse de la Banque de France");
        }
        BigDecimal rate = value.decimalValue().divide(HUNDRED);
        if (rate.signum() <= 0 || rate.compareTo(MAX_PLAUSIBLE_RATE) > 0) {
            throw new MarketDataException("Taux hors plage plausible dans la réponse de la Banque de France : " + value.asText());
        }
        return Optional.of(new MortgageRateQuote(asOf, rate, expectedSeriesKey));
    }
}
