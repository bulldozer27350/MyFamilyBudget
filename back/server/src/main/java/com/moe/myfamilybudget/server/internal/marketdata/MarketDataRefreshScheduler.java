package com.moe.myfamilybudget.server.internal.marketdata;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rafraîchit périodiquement les données de marché : une première fois peu après le démarrage
 * (sans bloquer celui-ci), puis à intervalle régulier.
 *
 * Désactivable avec {@code myfamilybudget.market-data.enabled=false} (utilisé par les tests).
 * Les délais sont des durées ISO-8601 : {@code myfamilybudget.market-data.refresh.initial-delay}
 * (défaut PT30S) et {@code myfamilybudget.market-data.refresh.interval} (défaut PT12H).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "myfamilybudget.market-data.enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataRefreshScheduler {

    private final MarketDataService marketDataService;

    public MarketDataRefreshScheduler(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Scheduled(
            initialDelayString = "${myfamilybudget.market-data.refresh.initial-delay:PT30S}",
            fixedDelayString = "${myfamilybudget.market-data.refresh.interval:PT12H}")
    public void refresh() {
        marketDataService.refresh();
    }
}
