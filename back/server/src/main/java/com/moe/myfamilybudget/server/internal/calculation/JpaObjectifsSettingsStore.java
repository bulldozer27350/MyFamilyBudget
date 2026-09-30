package com.moe.myfamilybudget.server.internal.calculation;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.persistence.entity.ObjectifsSettingsEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.ObjectifsSettingsRepository;

/**
 * Implémentation JPA de {@link ObjectifsSettingsStore} : une seule ligne, remplacée à chaque
 * enregistrement (même approche que {@code JpaLoanAdviceSettingsStore}).
 */
@Component
public class JpaObjectifsSettingsStore implements ObjectifsSettingsStore {

    private static final String SETTINGS_ID = "current";

    private final ObjectifsSettingsRepository repository;

    public JpaObjectifsSettingsStore(ObjectifsSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ObjectifsParameters> load() {
        return repository.findById(SETTINGS_ID)
                .map(e -> new ObjectifsParameters(e.getSecureHorizonMonths(), e.getLiquidHorizonMonths()));
    }

    @Override
    public void save(ObjectifsParameters parameters) {
        repository.save(new ObjectifsSettingsEntity(
                SETTINGS_ID, parameters.secureHorizonMonths(), parameters.liquidHorizonMonths(), Instant.now()));
    }

    @Override
    public void clear() {
        repository.findById(SETTINGS_ID).ifPresent(repository::delete);
    }
}
