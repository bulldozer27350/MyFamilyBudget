package com.moe.myfamilybudget.server.internal.marketdata;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Appels HTTP GET communs aux clients de sources publiques : délais explicites, erreurs
 * uniformisées en {@link MarketDataException}. Les en-têtes (clé d'API) ne sont jamais recopiés
 * dans les messages d'erreur.
 */
final class MarketHttp {

    private MarketHttp() {
    }

    static HttpClient newClient(Duration timeout) {
        return HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * @param sourceName nom de la source avec son article (« la BCE »), utilisé dans les messages
     * @return le corps de la réponse HTTP 200
     * @throws MarketDataException si l'appel échoue ou si le statut n'est pas 200
     */
    static String get(HttpClient client, URI uri, Duration timeout, Map<String, String> headers, String sourceName) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout).GET();
        headers.forEach(builder::header);
        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MarketDataException("Appel à " + sourceName + " impossible : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketDataException("Appel à " + sourceName + " interrompu", e);
        }
        if (response.statusCode() != 200) {
            throw new MarketDataException(capitalize(sourceName) + " a répondu HTTP " + response.statusCode());
        }
        return response.body();
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
