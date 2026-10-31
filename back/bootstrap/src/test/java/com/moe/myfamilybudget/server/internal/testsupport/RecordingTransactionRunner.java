package com.moe.myfamilybudget.server.internal.testsupport;

import java.util.function.Supplier;

import com.moe.myfamilybudget.application.port.TransactionRunner;

/**
 * {@link TransactionRunner} de test (SILO-205) : exécute l'action directement, sans transaction, et mémorise
 * le nombre d'appels et si une action est en cours d'exécution ({@link #isActive()}).
 */
public final class RecordingTransactionRunner implements TransactionRunner {

    private int calls;
    private boolean active;

    public static RecordingTransactionRunner direct() {
        return new RecordingTransactionRunner();
    }

    @Override
    public <T> T inTransaction(Supplier<T> action) {
        calls++;
        boolean previous = active;
        active = true;
        try {
            return action.get();
        } finally {
            active = previous;
        }
    }

    public int calls() {
        return calls;
    }

    public boolean isActive() {
        return active;
    }
}
