package com.moe.myfamilybudget.domain.settings.core.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Paramètres propres à l'application, sans propriétaire métier (SILO-220, lot A2) : profondeur de simulation
 * et taux d'inflation. Singleton fonctionnel : une seule ligne, lue par {@code findFirstByOrderByIdAsc}.
 *
 * <p>Additif : aucune relation vers {@code BudgetDataEntity}, pas encore utilisée par
 * {@code SettingsPersistenceAdapter} (bascule au lot B). Les colonnes équivalentes de la table historique
 * {@code settings} restent la source de vérité jusque-là. Le taux d'inflation est stocké avec 8 décimales,
 * comme dans la table historique.
 */
@Entity
@Table(name = "app_settings")
public class AppSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulate_until_age")
    private Integer simulateUntilAge;

    @Column(name = "inflation_rate", precision = 19, scale = 8)
    private BigDecimal inflationRate;

    public AppSettingsEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getSimulateUntilAge() {
        return simulateUntilAge;
    }

    public void setSimulateUntilAge(Integer simulateUntilAge) {
        this.simulateUntilAge = simulateUntilAge;
    }

    public BigDecimal getInflationRate() {
        return inflationRate;
    }

    public void setInflationRate(BigDecimal inflationRate) {
        this.inflationRate = inflationRate;
    }
}
