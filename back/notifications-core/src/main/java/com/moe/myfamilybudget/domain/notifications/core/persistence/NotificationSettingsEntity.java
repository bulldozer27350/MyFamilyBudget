package com.moe.myfamilybudget.domain.notifications.core.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Paramètres de notification modifiés par l'utilisateur, stockés en JSON (une ligne). Colonne TEXT
 * explicite et non {@code @Lob}, comme {@code LoanAdviceSettingsEntity}.
 */
@Entity
@Table(name = "notification_settings")
public class NotificationSettingsEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    // VARCHAR(10485760), pas columnDefinition = "TEXT" (mismatch de validation Hibernate/Liquibase,
    // echec CI du 22 oct. 2026, cf. BankImportDocumentEntity) ; toujours pas de @Lob.
    @Column(name = "payload_json", length = 10485760)
    private String payloadJson;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public NotificationSettingsEntity() {
    }

    public NotificationSettingsEntity(String id, String payloadJson, Instant updatedAt) {
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
