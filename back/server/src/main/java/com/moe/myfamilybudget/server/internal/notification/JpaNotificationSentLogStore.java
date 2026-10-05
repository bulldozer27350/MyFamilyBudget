package com.moe.myfamilybudget.server.internal.notification;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.notifications.port.NotificationSentLogStore;
import com.moe.myfamilybudget.persistence.entity.NotificationSentLogEntity;
import com.moe.myfamilybudget.persistence.repository.NotificationSentLogRepository;

/**
 * Implémentation JPA de {@link NotificationSentLogStore} : une ligne par clé de déduplication, remplacée à
 * chaque envoi (même table qu'avant SILO-180). Reste dans le composition root jusqu'à SILO-217.
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
