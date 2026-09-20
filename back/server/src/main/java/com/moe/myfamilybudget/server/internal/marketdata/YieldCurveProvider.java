package com.moe.myfamilybudget.server.internal.marketdata;

import java.util.Optional;

/**
 * Port vers une source publique de la courbe des taux de la zone euro.
 */
public interface YieldCurveProvider {

    /**
     * @return la dernière courbe publiée, ou vide si la source ne publie rien
     * @throws MarketDataException si la source est injoignable ou renvoie un format inattendu
     */
    Optional<YieldCurveQuote> fetchLatest();

    /** Faux si la source exige une configuration absente : elle est alors ignorée sans erreur. */
    default boolean isConfigured() {
        return true;
    }
}
