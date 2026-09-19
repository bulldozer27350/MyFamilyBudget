package com.moe.myfamilybudget.server.internal.marketdata;

import java.util.Optional;

/**
 * Persistance du dernier instantané de marché récupéré avec succès.
 */
public interface MarketSnapshotStore {

    /** Dernier instantané persisté, ou vide s'il n'y en a pas (ou s'il est illisible). */
    Optional<MarketSnapshot> load();

    void save(MarketSnapshot snapshot);
}
