package com.moe.myfamilybudget.server.internal.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dernier instantané de données de marché (taux publics), stocké en JSON.
 *
 * Colonne TEXT explicite et non {@code @Lob}, pour la même raison que
 * {@link BankImportEntity} : éviter le mécanisme « Large Object » de PostgreSQL.
 */
@Entity
@Table(name = "market_snapshot")
public class MarketSnapshotEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    public MarketSnapshotEntity() {}

    public MarketSnapshotEntity(String id, String payloadJson, Instant fetchedAt) {
        this.id = id;
        this.payloadJson = payloadJson;
        this.fetchedAt = fetchedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
