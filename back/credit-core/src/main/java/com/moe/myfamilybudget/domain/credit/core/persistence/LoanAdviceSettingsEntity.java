package com.moe.myfamilybudget.domain.credit.core.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Hypothèses de l'analyse des prêts modifiées par l'utilisateur, stockées en JSON (une ligne).
 * {@code @JdbcTypeCode(SqlTypes.LONG32VARCHAR)}, pas {@code @Lob}, comme {@code MarketSnapshotEntity} ;
 * voir le Javadoc de {@code BankImportDocumentEntity} (module {@code bank-pointage-core}) pour pourquoi.
 */
@Entity
@Table(name = "loan_advice_settings")
public class LoanAdviceSettingsEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    // @JdbcTypeCode(SqlTypes.LONG32VARCHAR), pas @Lob (risque documente dans BankImportDocumentEntity) :
    // text chez PostgreSQL, clob chez H2, lecture/ecriture en chaine ordinaire (jamais de Large Object).
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public LoanAdviceSettingsEntity() {}

    public LoanAdviceSettingsEntity(String id, String payloadJson, Instant updatedAt) {
        this.id = id;
        this.payloadJson = payloadJson;
        this.updatedAt = updatedAt;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
