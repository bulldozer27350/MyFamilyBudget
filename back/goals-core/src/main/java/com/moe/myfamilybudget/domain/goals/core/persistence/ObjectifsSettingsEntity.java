package com.moe.myfamilybudget.domain.goals.core.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Paramètres du domaine Objectifs (RF-700) : seuils de bascule liquide/illiquide, une seule ligne.
 * Remplace les colonnes {@code goalSecureHorizonMonths}/{@code goalLiquidHorizonMonths} de la
 * table {@code settings}.
 */
@Entity
@Table(name = "objectifs_settings")
public class ObjectifsSettingsEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "secure_horizon_months")
    private Integer secureHorizonMonths;

    @Column(name = "liquid_horizon_months")
    private Integer liquidHorizonMonths;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ObjectifsSettingsEntity() {}

    public ObjectifsSettingsEntity(String id, Integer secureHorizonMonths, Integer liquidHorizonMonths, Instant updatedAt) {
        this.id = id;
        this.secureHorizonMonths = secureHorizonMonths;
        this.liquidHorizonMonths = liquidHorizonMonths;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getSecureHorizonMonths() {
        return secureHorizonMonths;
    }

    public void setSecureHorizonMonths(Integer secureHorizonMonths) {
        this.secureHorizonMonths = secureHorizonMonths;
    }

    public Integer getLiquidHorizonMonths() {
        return liquidHorizonMonths;
    }

    public void setLiquidHorizonMonths(Integer liquidHorizonMonths) {
        this.liquidHorizonMonths = liquidHorizonMonths;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
