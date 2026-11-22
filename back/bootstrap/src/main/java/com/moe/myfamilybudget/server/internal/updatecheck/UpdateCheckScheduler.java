package com.moe.myfamilybudget.server.internal.updatecheck;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Planifie le contrôle de disponibilité d'une nouvelle version, uniquement pour la distribution
 * portable (profil Docker exclu, voir {@code UpdateCheckService}).
 *
 * <p>Désactivable avec {@code myfamilybudget.update-check.enabled=false} (utilisé par les tests).
 * Les délais sont des durées ISO-8601 : {@code myfamilybudget.update-check.refresh.initial-delay}
 * (défaut PT10S) et {@code myfamilybudget.update-check.refresh.interval} (défaut PT24H).
 */
@Component
@EnableScheduling
@Profile("!docker")
@ConditionalOnProperty(name = "myfamilybudget.update-check.enabled", havingValue = "true", matchIfMissing = true)
class UpdateCheckScheduler {

    private final UpdateCheckService updateCheckService;

    UpdateCheckScheduler(UpdateCheckService updateCheckService) {
        this.updateCheckService = updateCheckService;
    }

    @Scheduled(
            initialDelayString = "${myfamilybudget.update-check.refresh.initial-delay:PT10S}",
            fixedDelayString = "${myfamilybudget.update-check.refresh.interval:PT24H}")
    void refresh() {
        updateCheckService.refresh();
    }
}
