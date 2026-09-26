package com.moe.myfamilybudget.server.internal.notification;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.server.internal.persistence.entity.NotificationSettingsEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.NotificationSettingsRepository;

/**
 * Implémentation JPA de {@link NotificationSettingsStore} : une seule ligne, remplacée à chaque
 * enregistrement (même approche que {@code JpaLoanAdviceSettingsStore}).
 */
@Component
public class JpaNotificationSettingsStore implements NotificationSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(JpaNotificationSettingsStore.class);
    private static final String SETTINGS_ID = "current";

    private final NotificationSettingsRepository repository;
    private final ObjectMapper objectMapper;

    public JpaNotificationSettingsStore(NotificationSettingsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<NotificationSettingsParameters> load() {
        return repository.findById(SETTINGS_ID).flatMap(entity -> {
            try {
                return Optional.of(NotificationSettingsCodec.fromJson(entity.getPayloadJson(), objectMapper));
            } catch (IllegalStateException e) {
                log.warn("Paramètres de notification persistés ignorés (valeurs par défaut utilisées) : {}",
                        e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public void save(NotificationSettingsParameters parameters) {
        repository.save(new NotificationSettingsEntity(
                SETTINGS_ID, NotificationSettingsCodec.toJson(parameters, objectMapper), Instant.now()));
    }
}
