package com.moe.myfamilybudget.server.internal.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dernier envoi effectif d'une notification, par clé de déduplication
 * ({@link com.moe.myfamilybudget.server.internal.notification.NotificationMessage#dedupKey()}).
 *
 * Utilisée par {@code NotificationDispatchService} pour n'envoyer une même alerte automatique
 * qu'une fois par 24h ; un déclenchement manuel (bouton "Vérifier") l'ignore toujours et remet à
 * jour {@code lastSentAt}, ce qui relance aussi le délai de 24h pour le prochain déclenchement
 * automatique.
 */
@Entity
@Table(name = "notification_sent_log")
public class NotificationSentLogEntity {

    @Id
    @Column(name = "dedup_key", length = 128)
    private String dedupKey;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    public NotificationSentLogEntity() {
    }

    public NotificationSentLogEntity(String dedupKey, Instant lastSentAt) {
        this.dedupKey = dedupKey;
        this.lastSentAt = lastSentAt;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(Instant lastSentAt) {
        this.lastSentAt = lastSentAt;
    }
}
