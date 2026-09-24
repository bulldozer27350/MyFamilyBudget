package com.moe.myfamilybudget.server.internal.enablebanking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Centralise la configuration Enable Banking et détermine, une seule fois au démarrage, si la
 * synchronisation automatique peut être activée.
 *
 * Le certificat n'est jamais embarqué dans l'image Docker ni dans le dépôt : seul son chemin sur
 * l'hôte est fourni (variable d'environnement, voir {@code docker-compose.prod.yml}). Absent, la
 * synchronisation est simplement désactivée — l'application démarre et fonctionne normalement
 * sans elle (voir {@link #isConfigured()}), exactement comme les sources de données de marché
 * sans clé d'API (voir {@code myfamilybudget.market-data}).
 */
@Component
public class EnableBankingConfig {

    private static final Logger log = LoggerFactory.getLogger(EnableBankingConfig.class);

    private final String applicationId;
    private final String privateKeyPath;
    private final List<EnableBankingAccounts.Account> accounts;
    private final String apiBaseUrl;
    private final Duration timeout;
    private final Clock clock;

    private boolean configured;
    private String unavailableReason;
    private EnableBankingJwtSigner signer;

    @Autowired
    public EnableBankingConfig(
            @Value("${myfamilybudget.enable-banking.application-id:}") String applicationId,
            @Value("${myfamilybudget.enable-banking.private-key-path:}") String privateKeyPath,
            @Value("${myfamilybudget.enable-banking.accounts:}") String accountsRaw,
            @Value("${myfamilybudget.enable-banking.api-base-url:https://api.enablebanking.com}") String apiBaseUrl,
            @Value("${myfamilybudget.enable-banking.timeout-seconds:15}") int timeoutSeconds) {
        this(applicationId, privateKeyPath, accountsRaw, apiBaseUrl, timeoutSeconds, Clock.systemUTC());
    }

    EnableBankingConfig(String applicationId, String privateKeyPath, String accountsRaw,
            String apiBaseUrl, int timeoutSeconds, Clock clock) {
        this.applicationId = applicationId == null ? "" : applicationId.trim();
        this.privateKeyPath = privateKeyPath == null ? "" : privateKeyPath.trim();
        this.accounts = EnableBankingAccounts.parse(accountsRaw);
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.clock = clock;
    }

    @PostConstruct
    void init() {
        if (applicationId.isEmpty()) {
            disable("MYFAMILYBUDGET_ENABLE_BANKING_APPLICATION_ID n'est pas renseignée.");
            return;
        }
        if (privateKeyPath.isEmpty()) {
            disable("MYFAMILYBUDGET_ENABLE_BANKING_PRIVATE_KEY_PATH n'est pas renseignée.");
            return;
        }
        if (accounts.isEmpty()) {
            disable("MYFAMILYBUDGET_ENABLE_BANKING_ACCOUNTS n'est pas renseignée ou vide.");
            return;
        }

        Path keyPath = Path.of(privateKeyPath);
        if (!Files.isReadable(keyPath) || Files.isDirectory(keyPath)) {
            disable("le certificat attendu à \"" + privateKeyPath + "\" est introuvable ou illisible.");
            return;
        }

        try {
            String pemContent = Files.readString(keyPath, StandardCharsets.UTF_8);
            this.signer = new EnableBankingJwtSigner(pemContent, applicationId, clock);
        } catch (IOException | EnableBankingException e) {
            disable("le certificat à \"" + privateKeyPath + "\" n'a pas pu être lu : " + e.getMessage());
            return;
        }

        this.configured = true;
        log.info("Synchronisation Enable Banking activée pour {} compte(s).", accounts.size());
    }

    private void disable(String reason) {
        this.configured = false;
        this.unavailableReason =
                "Synchronisation Enable Banking désactivée : " + reason
                        + " Voir tools/enable-banking-sync (ou la documentation du projet) pour la configuration.";
        log.info(unavailableReason);
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Message explicatif à afficher/journaliser quand {@link #isConfigured()} est faux. */
    public String unavailableReason() {
        return unavailableReason;
    }

    public List<EnableBankingAccounts.Account> accounts() {
        return accounts;
    }

    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    public Duration timeout() {
        return timeout;
    }

    /** @throws EnableBankingException si {@link #isConfigured()} est faux */
    String issueToken() {
        if (!configured) {
            throw new EnableBankingException(unavailableReason);
        }
        return signer.issueToken();
    }
}
