package com.moe.myfamilybudget.server.internal.testsupport;

import java.util.Optional;

import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.server.internal.calculation.ObjectifsSettingsStore;

/**
 * {@link ObjectifsSettingsStore} en mémoire pour les tests unitaires, sans base de données.
 */
public final class InMemoryObjectifsSettingsStore implements ObjectifsSettingsStore {

    private ObjectifsParameters stored;

    @Override
    public Optional<ObjectifsParameters> load() {
        return Optional.ofNullable(stored);
    }

    @Override
    public void save(ObjectifsParameters parameters) {
        this.stored = parameters;
    }

    @Override
    public void clear() {
        this.stored = null;
    }
}
