package com.moe.myfamilybudget.server.internal.calculation;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.server.internal.persistence.entity.LoanAdviceSettingsEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.LoanAdviceSettingsRepository;

/**
 * Implémentation JPA de {@link LoanAdviceSettingsStore} : une seule ligne, remplacée à chaque
 * enregistrement.
 */
@Component
public class JpaLoanAdviceSettingsStore implements LoanAdviceSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(JpaLoanAdviceSettingsStore.class);
    private static final String SETTINGS_ID = "current";

    private final LoanAdviceSettingsRepository repository;
    private final ObjectMapper objectMapper;

    public JpaLoanAdviceSettingsStore(LoanAdviceSettingsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LoanAdviceParameters> load() {
        return repository.findById(SETTINGS_ID).flatMap(entity -> {
            try {
                return Optional.of(LoanAdviceSettingsCodec.fromJson(entity.getPayloadJson(), objectMapper));
            } catch (IllegalStateException e) {
                log.warn("Hypothèses d'analyse des prêts persistées ignorées (valeurs par défaut utilisées) : {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public void save(LoanAdviceParameters parameters) {
        repository.save(new LoanAdviceSettingsEntity(
                SETTINGS_ID, LoanAdviceSettingsCodec.toJson(parameters, objectMapper), Instant.now()));
    }
}
