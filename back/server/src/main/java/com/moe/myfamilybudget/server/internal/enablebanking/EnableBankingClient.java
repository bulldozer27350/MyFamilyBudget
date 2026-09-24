package com.moe.myfamilybudget.server.internal.enablebanking;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Appels HTTP vers l'API Enable Banking (soldes, transactions). Un nouveau jeton JWT est demandé
 * à chaque appel : sa génération est purement locale (signature RSA), donc sans coût réseau.
 */
@Component
class EnableBankingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EnableBankingConfig config;
    private final HttpClient httpClient;

    EnableBankingClient(EnableBankingConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Solde comptable et prévisionnel du compte, tels que renvoyés par Enable Banking. */
    List<JsonNode> fetchBalances(String accountUid) {
        JsonNode body = get(config.apiBaseUrl() + "/accounts/" + encode(accountUid) + "/balances");
        return toList(body.get("balances"));
    }

    /**
     * Toutes les transactions du compte, en suivant la pagination ({@code continuation_key})
     * jusqu'à ce que la banque n'en renvoie plus.
     *
     * @param dateFrom date ISO {@code YYYY-MM-DD} à partir de laquelle récupérer les transactions,
     *                 ou {@code null} pour laisser la banque appliquer sa profondeur d'historique
     *                 par défaut (première synchronisation d'un compte)
     */
    List<JsonNode> fetchTransactions(String accountUid, String dateFrom) {
        List<JsonNode> transactions = new ArrayList<>();
        String continuationKey = null;

        do {
            StringBuilder url = new StringBuilder(config.apiBaseUrl())
                    .append("/accounts/").append(encode(accountUid)).append("/transactions");
            List<String> params = new ArrayList<>();
            if (dateFrom != null && !dateFrom.isBlank()) {
                params.add("date_from=" + encode(dateFrom));
            }
            if (continuationKey != null) {
                params.add("continuation_key=" + encode(continuationKey));
            }
            if (!params.isEmpty()) {
                url.append('?').append(String.join("&", params));
            }

            JsonNode body = get(url.toString());
            toList(body.get("transactions")).forEach(transactions::add);

            JsonNode nextKey = body.get("continuation_key");
            continuationKey = (nextKey != null && !nextKey.isNull()) ? nextKey.asText() : null;
        } while (continuationKey != null);

        return transactions;
    }

    private JsonNode get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(config.timeout())
                .header("Authorization", "Bearer " + config.issueToken())
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new EnableBankingException("Appel à Enable Banking impossible : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EnableBankingException("Appel à Enable Banking interrompu", e);
        }

        if (response.statusCode() != 200) {
            throw new EnableBankingException(
                    "Enable Banking a répondu HTTP " + response.statusCode()
                            + " (le consentement DSP2 a peut-être expiré).");
        }

        try {
            return MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new EnableBankingException("Réponse Enable Banking illisible : " + e.getMessage(), e);
        }
    }

    private static List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(result::add);
        }
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
