package com.moe.myfamilybudget.domain.notifications.core.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.notifications.port.NotificationSentLogStore;

/**
 * Implémentation JPA de {@link NotificationSentLogStore} : une ligne par clé de déduplication, remplacée à
 * chaque envoi (même table qu'avant SILO-180).
 */
@Component
public class JpaNotificationSentLogStore implements NotificationSentLogStore {

    private final NotificationSentLogRepository repository;

    public JpaNotificationSentLogStore(NotificationSentLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Instant> findLastSentAt(String dedupKey) {
        return repository.findById(dedupKey).map(NotificationSentLogEntity::getLastSentAt);
    }

    @Override
    public void markSent(String dedupKey, Instant sentAt) {
        repository.save(new NotificationSentLogEntity(dedupKey, sentAt));
    }
}
