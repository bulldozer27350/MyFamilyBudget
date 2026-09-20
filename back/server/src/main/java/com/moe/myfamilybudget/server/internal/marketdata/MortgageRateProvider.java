package com.moe.myfamilybudget.server.internal.marketdata;

import java.util.Optional;

/**
 * Port vers une source publique du taux moyen des nouveaux crédits immobiliers.
 */
public interface MortgageRateProvider {

    /**
     * @return la donnée la plus récente, ou vide si la source ne publie rien
     * @throws MarketDataException si la source est injoignable ou renvoie un format inattendu
     */
    Optional<MortgageRateQuote> fetchLatest();

    /** Faux si la source exige une configuration absente (clé d'API) : elle est alors ignorée sans erreur. */
    default boolean isConfigured() {
        return true;
    }
}
