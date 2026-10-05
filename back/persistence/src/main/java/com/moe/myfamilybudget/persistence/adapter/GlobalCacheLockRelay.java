package com.moe.myfamilybudget.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.persistence.PersistenceManager;

/**
 * SILO-206 : relais transitoire vers le verrou global du cache. Seule la persistance connait
 * {@link PersistenceManager} : le composition root ne voit que ce relais, retire avec le cache (SILO-230).
 */
@Component
public class GlobalCacheLockRelay implements Runnable {

    private final PersistenceManager persistenceManager;

    public GlobalCacheLockRelay(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /** Prend, une seule fois par transaction, le verrou global du cache. */
    @Override
    public void run() {
        persistenceManager.lockForCurrentTransaction();
    }
}
