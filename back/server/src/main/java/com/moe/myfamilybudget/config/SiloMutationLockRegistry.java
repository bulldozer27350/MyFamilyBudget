package com.moe.myfamilybudget.config;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;

/**
 * Implémentation du port {@link SiloMutationLock} (SILO-206) : un {@link ReentrantLock} par silo, pris dans
 * l'ordre de {@link MutationSilo} et conservé jusqu'à la fin de la transaction (validation ou annulation), comme
 * le verrou global qu'il remplace dans les façades (VT-350b). Deux façades qui visent des silos disjoints ne
 * s'attendent plus ; l'ordre fixe écarte l'interblocage entre deux façades qui partagent plusieurs silos.
 *
 * <p>Transition : tant que toutes les écritures passent par le cache global ({@code PersistenceManager}, qui
 * sauvegarde le modèle complet), une transaction qui a verrouillé au moins un silo prend aussi le verrou global
 * du cache, après les verrous de silos et avant tout verrou de ligne de la base. Ce relais ({@code
 * legacyGlobalLock}) est retiré avec le cache (SILO-230) ; chaque silo qui écrit directement dans ses tables
 * (lots B de SILO-210 à SILO-216) cesse alors de dépendre de lui. Ce composant rejoindra le module de démarrage
 * (SILO-330).
 *
 * <p>Sans transaction en cours (tests unitaires sur adaptateurs en mémoire), ou avec un ensemble vide, l'appel
 * ne bloque rien.
 */
public class SiloMutationLockRegistry implements SiloMutationLock {

    private final Map<MutationSilo, ReentrantLock> locks = new EnumMap<>(MutationSilo.class);
    private final Runnable legacyGlobalLock;
    /** Clé de la ressource liée à la transaction en cours : l'état des verrous qu'elle détient. */
    private final Object heldKey = new Object();

    public SiloMutationLockRegistry(Runnable legacyGlobalLock) {
        this.legacyGlobalLock = Objects.requireNonNull(legacyGlobalLock, "legacyGlobalLock");
        for (MutationSilo silo : MutationSilo.values()) {
            locks.put(silo, new ReentrantLock());
        }
    }

    @Override
    public void lockForCurrentTransaction(Set<MutationSilo> silos) {
        Objects.requireNonNull(silos, "silos");
        if (silos.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        Held held = (Held) TransactionSynchronizationManager.getResource(heldKey);
        if (held == null) {
            held = enlistInCurrentTransaction();
        }
        // EnumSet itère dans l'ordre de déclaration : c'est l'ordre de prise des verrous.
        for (MutationSilo silo : EnumSet.copyOf(silos)) {
            if (held.locked.contains(silo)) {
                continue; // ré-entrance : cette transaction détient déjà ce silo
            }
            if (silo.ordinal() < held.highest) {
                throw new IllegalStateException("Ordre de verrouillage violé : " + silo
                        + " demandé après un silo d'ordre supérieur ; déclarer tous les silos dans un seul appel");
            }
            locks.get(silo).lock();
            held.locked.add(silo);
            held.highest = silo.ordinal();
        }
        if (!held.legacyGlobalLockTaken) {
            legacyGlobalLock.run();
            held.legacyGlobalLockTaken = true;
        }
    }

    /** Lie un état de verrous à la transaction en cours et enregistre leur libération après sa fin. */
    private Held enlistInCurrentTransaction() {
        Held held = new Held();
        TransactionSynchronizationManager.bindResource(heldKey, held);
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(heldKey);
                    release(held);
                }
            });
        } catch (RuntimeException | Error e) {
            TransactionSynchronizationManager.unbindResourceIfPossible(heldKey);
            throw e;
        }
        return held;
    }

    /** Relâche les silos tenus, du plus grand au plus petit (inverse de la prise). */
    private void release(Held held) {
        MutationSilo[] all = MutationSilo.values();
        for (int i = all.length - 1; i >= 0; i--) {
            if (held.locked.remove(all[i])) {
                locks.get(all[i]).unlock();
            }
        }
    }

    /** État des verrous détenus par une transaction ; accédé par le seul thread de cette transaction. */
    private static final class Held {
        private final EnumSet<MutationSilo> locked = EnumSet.noneOf(MutationSilo.class);
        private int highest = -1;
        private boolean legacyGlobalLockTaken;
    }
}
