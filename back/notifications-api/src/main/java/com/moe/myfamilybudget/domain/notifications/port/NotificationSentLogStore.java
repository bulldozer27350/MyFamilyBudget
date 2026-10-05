package com.moe.myfamilybudget.domain.notifications.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Port de persistance du journal d'envoi des notifications : date du dernier envoi par clé de déduplication.
 * Implémenté hors du silo (adaptateur JPA du composition root, déplacé dans la persistance du silo
 * Notifications par SILO-217).
 */
public interface NotificationSentLogStore {

    /** Date du dernier envoi pour cette clé de déduplication, si elle est connue. */
    Optional<Instant> findLastSentAt(String dedupKey);

    /** Enregistre (ou remplace) la date du dernier envoi pour cette clé. */
    void markSent(String dedupKey, Instant sentAt);
}
