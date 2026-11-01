package com.moe.myfamilybudget.domain.market.core.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dernier instantané de données de marché (taux publics), stocké en JSON.
 *
 * {@code @JdbcTypeCode(SqlTypes.LONG32VARCHAR)}, pas {@code @Lob} : voir le Javadoc de
 * {@code BankImportDocumentEntity} (module {@code bank-pointage-core}) pour comment le mécanisme
 * « Large Object » de PostgreSQL (celui qui posait problème avec {@code BankImportEntity}, supprimee
 * en DB-1130) est évité sans jamais le solliciter.
 */
@Entity
@Table(name = "market_snapshot")
public class MarketSnapshotEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    // @JdbcTypeCode(SqlTypes.LONG32VARCHAR), pas @Lob (risque documente dans BankImportDocumentEntity) :
    // text chez PostgreSQL, clob chez H2, lecture/ecriture en chaine ordinaire (jamais de Large Object).
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "payload_json")
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
