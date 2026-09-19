package com.moe.myfamilybudget.server.internal.marketdata;

import java.util.Optional;

/**
 * Port vers une source publique de taux de l'épargne réglementée (Livret A, LDDS, LEP).
 */
public interface RegulatedRatesProvider {

    /**
     * Interroge la source et renvoie la donnée la plus récente qu'elle publie.
     *
     * @return la donnée, ou vide si la source ne publie rien
     * @throws MarketDataException si la source est injoignable ou renvoie un format inattendu
     */
    Optional<RegulatedRatesQuote> fetchLatest();
}
