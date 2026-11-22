package com.moe.myfamilybudget.server.internal.updatecheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Appelle l'API publique GitHub (lecture seule, sans authentification) pour connaître la
 * dernière version publiée de la distribution portable. Aucune information de l'utilisateur
 * n'est transmise : seule l'URL "releases/latest" du dépôt est interrogée.
 */
@Component
class GithubReleaseClient {

    private static final Logger log = LoggerFactory.getLogger(GithubReleaseClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    GithubReleaseClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * @return la dernière release publiée (l'API GitHub exclut déjà elle-même les pré-releases et
     *     brouillons sur "releases/latest"), ou vide si injoignable, en erreur, ou si le dépôt n'a
     *     encore aucune release. Ne lève jamais d'exception : un échec de contrôle de mise à jour
     *     ne doit jamais perturber le fonctionnement normal de l'application.
     */
    Optional<GithubRelease> fetchLatestRelease(String apiBaseUrl, String owner, String repo, int timeoutSeconds) {
        String url = apiBaseUrl + "/repos/" + owner + "/" + repo + "/releases/latest";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MyFamilyBudget-update-check")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("Contrôle de mise à jour : réponse HTTP {} de {}", response.statusCode(), url);
                return Optional.empty();
            }
            return Optional.of(parse(response.body()));
        } catch (IOException e) {
            log.debug("Contrôle de mise à jour : {} injoignable ({})", url, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException e) {
            log.debug("Contrôle de mise à jour : réponse de {} illisible ({})", url, e.getMessage());
            return Optional.empty();
        }
    }

    private GithubRelease parse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        String tagName = root.path("tag_name").asText(null);
        String htmlUrl = root.path("html_url").asText(null);
        String releaseNotes = root.path("body").asText(null);
        String publishedAt = root.path("published_at").asText(null);
        String downloadUrl = null;
        for (JsonNode asset : root.path("assets")) {
            String name = asset.path("name").asText("").toLowerCase(Locale.ROOT);
            if (name.contains("windows")) {
                downloadUrl = asset.path("browser_download_url").asText(null);
                break;
            }
        }
        return new GithubRelease(tagName, htmlUrl, downloadUrl, releaseNotes, publishedAt);
    }
}
