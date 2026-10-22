package com.moe.myfamilybudget.domain.market.core.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Dernier instantané de données de marché (taux publics), stocké en JSON.
 *
 * {@code @Lob} (CLOB) ; voir {@code com.moe.myfamilybudget.infra.jpa.TextClobPostgreSQLDialect} (profil
 * {@code docker}, module {@code infra-jpa}) pour comment le mécanisme « Large Object » de PostgreSQL
 * (celui qui posait problème avec {@code BankImportEntity}, supprimee en DB-1130) est neutralisé sans
 * renoncer à {@code @Lob}.
 */
@Entity
@Table(name = "market_snapshot")
public class MarketSnapshotEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    // @Lob (CLOB) ; le mapping PostgreSQL CLOB->oid (risque documente plus haut) est neutralise
    // par le dialecte TextClobPostgreSQLDialect (profil docker, module infra-jpa), pas ici au
    // niveau du champ.
    @Lob
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
