package com.moe.myfamilybudget.domain.notifications.core.persistence;

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

    // VARCHAR borne (pas TEXT/CLOB) : la contrainte unique sur cette colonne n'est pas indexable sur
    // un CLOB en H2 (echec Liquibase R-01 lot B, CI du 22 oct. 2026) ; 1024 caracteres couvre tres
    // largement les endpoints Web Push observes (FCM, Mozilla, Apple : quelques centaines de
    // caracteres au plus).
    @Column(name = "endpoint", length = 1024, nullable = false)
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
