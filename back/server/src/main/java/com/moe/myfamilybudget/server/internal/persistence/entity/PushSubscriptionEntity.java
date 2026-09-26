package com.moe.myfamilybudget.server.internal.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Abonnement Web Push d'un navigateur (PWA ajoutée à l'écran d'accueil ou onglet ouvert ayant
 * autorisé les notifications). {@code endpoint} + {@code p256dh}/{@code auth} sont fournis par le
 * {@code PushManager} du navigateur lors de l'abonnement ; conservés tels quels, aucun décodage
 * côté serveur au-delà de ce qu'exige la bibliothèque d'envoi Web Push.
 */
@Entity
@Table(name = "push_subscription", uniqueConstraints = @UniqueConstraint(columnNames = "endpoint"))
public class PushSubscriptionEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "endpoint", columnDefinition = "TEXT", nullable = false)
    private String endpoint;

    @Column(name = "p256dh", length = 255, nullable = false)
    private String p256dh;

    @Column(name = "auth", length = 255, nullable = false)
    private String auth;

    @Column(name = "created_at")
    private Instant createdAt;

    public PushSubscriptionEntity() {
    }

    public PushSubscriptionEntity(String id, String endpoint, String p256dh, String auth, Instant createdAt) {
        this.id = id;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
