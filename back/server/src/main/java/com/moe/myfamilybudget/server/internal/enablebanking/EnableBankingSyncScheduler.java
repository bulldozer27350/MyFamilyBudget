package com.moe.myfamilybudget.server.internal.enablebanking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Synchronise périodiquement les comptes Enable Banking, tant que l'application (conteneur
 * Docker ou distribution portable) reste ouverte. Ne fait rien tant que le certificat et
 * l'{@code application_id} n'ont pas été fournis (voir {@link EnableBankingConfig}) : la
 * distribution portable destinée à un tiers reste donc silencieuse par défaut, sans risque de
 * fuite ni d'appel réseau inattendu.
 *
 * Désactivable avec {@code myfamilybudget.enable-banking.scheduler-enabled=false} (utilisé par
 * les tests). Les délais sont des durées ISO-8601 : {@code myfamilybudget.enable-banking.refresh.
 * initial-delay} (défaut PT1M) et {@code myfamilybudget.enable-banking.refresh.interval}
 * (défaut PT6H).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "myfamilybudget.enable-banking.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class EnableBankingSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnableBankingSyncScheduler.class);

    private final EnableBankingSyncService syncService;

    public EnableBankingSyncScheduler(EnableBankingSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(
            initialDelayString = "${myfamilybudget.enable-banking.refresh.initial-delay:PT1M}",
            fixedDelayString = "${myfamilybudget.enable-banking.refresh.interval:PT6H}")
    public void refresh() {
        if (!syncService.isConfigured()) {
            log.debug("Synchronisation Enable Banking planifiée ignorée : {}", syncService.unavailableReason());
            return;
        }
        try {
            syncService.sync();
        } catch (EnableBankingException e) {
            log.error("Échec de la synchronisation Enable Banking planifiée : {}", e.getMessage());
        }
    }
}
