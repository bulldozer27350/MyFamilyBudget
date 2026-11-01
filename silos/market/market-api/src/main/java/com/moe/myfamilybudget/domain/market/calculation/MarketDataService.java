package com.moe.myfamilybudget.domain.market.calculation;

import com.moe.myfamilybudget.domain.market.model.MarketRatesView;

/**
 * Service d'agrégation des données de marché publiques (taux réglementés, taux des crédits immobiliers, courbe
 * des taux de la zone euro). Contrat du silo Marché (SILO-159) : les consommateurs ne connaissent que cette
 * interface ; l'implémentation ({@code DefaultMarketDataService}, dans {@code market-core}) et son câblage
 * ({@code DomainEngineConfig}) restent hors de leur portée.
 *
 * <p>Ces données ne servent qu'à des suggestions : rien ici ne modifie les taux saisis par l'utilisateur.
 */
public interface MarketDataService {

    /** Vue courante, construite sans aucun appel réseau. */
    MarketRatesView current();

    /** Interroge chaque source, met à jour l'instantané si l'une d'elles a répondu, puis renvoie la vue. */
    MarketRatesView refresh();
}
