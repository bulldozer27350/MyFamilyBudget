package com.moe.myfamilybudget.server.internal.marketdata;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptateur vers l'API SDMX publique de la BCE (data-api.ecb.europa.eu, sans clé) : courbe des
 * taux des emprunts d'État de la zone euro notés AAA, dataset YC, série
 * {@code B.U2.EUR.4F.G_N_A.SV_C_YM}.
 *
 * Six séries sont lues en une requête : taux spot 2 et 10 ans, taux forwards instantanés 1, 2, 5 et
 * 10 ans. La réponse est demandée au format {@code csvdata} ; les valeurs sont en pourcentage et
 * converties en fraction.
 */
@Component
public class EcbYieldCurveClient implements YieldCurveProvider {

    private static final String SOURCE_NAME = "la BCE";
    private static final String SERIES_PREFIX = "B.U2.EUR.4F.G_N_A.SV_C_YM.";
    private static final List<String> SERIES = List.of("SR_2Y", "SR_10Y", "IF_1Y", "IF_2Y", "IF_5Y", "IF_10Y");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;

    public EcbYieldCurveClient(
            @Value("${myfamilybudget.market-data.ecb.base-url:https://data-api.ecb.europa.eu}") String baseUrl,
            @Value("${myfamilybudget.market-data.timeout-seconds:5}") int timeoutSeconds) {
        this.baseUrl = MarketHttp.stripTrailingSlash(baseUrl);
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.httpClient = MarketHttp.newClient(this.timeout);
    }

    @Override
    public Optional<YieldCurveQuote> fetchLatest() {
        String csv = MarketHttp.get(httpClient, buildUri(), timeout, Map.of("Accept", "text/csv"), SOURCE_NAME);
        return parse(csv);
    }

    URI buildUri() {
        // Le « + » est l'opérateur « ou » de SDMX : il doit rester littéral dans le chemin.
        return URI.create(baseUrl + "/service/data/YC/" + SERIES_PREFIX + String.join("+", SERIES)
                + "?lastNObservations=1&format=csvdata");
    }

    /** Extrait la dernière observation de chacune des six séries d'un export {@code csvdata}. */
    static Optional<YieldCurveQuote> parse(String csv) {
        List<List<String>> rows = SimpleCsv.parse(csv);
        if (rows.size() < 2) {
            return Optional.empty();
        }
        List<String> header = rows.get(0);
        int typeCol = header.indexOf("DATA_TYPE_FM");
        int dateCol = header.indexOf("TIME_PERIOD");
        int valueCol = header.indexOf("OBS_VALUE");
        if (typeCol < 0 || dateCol < 0 || valueCol < 0) {
            throw new MarketDataException("Réponse de la BCE inattendue : colonnes DATA_TYPE_FM, TIME_PERIOD ou OBS_VALUE absentes");
        }

        Map<String, BigDecimal> values = new HashMap<>();
        LocalDate latest = null;
        for (List<String> row : rows.subList(1, rows.size())) {
            if (row.size() <= Math.max(typeCol, Math.max(dateCol, valueCol))) {
                throw new MarketDataException("Réponse de la BCE inattendue : ligne incomplète");
            }
            String type = row.get(typeCol);
            if (!SERIES.contains(type)) {
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(row.get(dateCol));
                values.put(type, new BigDecimal(row.get(valueCol)).divide(HUNDRED));
                if (latest == null || date.isAfter(latest)) {
                    latest = date;
                }
            } catch (DateTimeParseException | NumberFormatException e) {
                throw new MarketDataException("Réponse de la BCE inattendue : date ou valeur illisible pour " + type, e);
            }
        }
        for (String series : SERIES) {
            if (!values.containsKey(series)) {
                throw new MarketDataException("Réponse de la BCE incomplète : série " + series + " absente");
            }
        }
        return Optional.of(new YieldCurveQuote(latest,
                values.get("SR_2Y"), values.get("SR_10Y"),
                values.get("IF_1Y"), values.get("IF_2Y"), values.get("IF_5Y"), values.get("IF_10Y")));
    }
}
