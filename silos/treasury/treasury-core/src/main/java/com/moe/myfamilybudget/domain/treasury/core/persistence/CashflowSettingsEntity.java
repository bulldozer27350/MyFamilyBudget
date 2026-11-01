package com.moe.myfamilybudget.domain.treasury.core.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Paramètres généraux dont le silo Trésorerie est propriétaire (SILO-220, lot A) : date et mode pivot, solde
 * de départ, balayage (sweep) et seuils de cash. Singleton fonctionnel : une seule ligne, lue par
 * {@code findFirstByOrderByIdAsc}.
 *
 * <p>Additif : aucune relation vers {@code BudgetDataEntity}, pas encore utilisée par
 * {@code SettingsPersistenceAdapter} (bascule au lot B). Les colonnes équivalentes de la table historique
 * {@code settings} restent la source de vérité jusque-là.
 */
@Entity
@Table(name = "cashflow_settings")
public class CashflowSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pivot_date", length = 32)
    private String pivotDate;

    @Column(name = "pivot_mode", length = 32)
    private String pivotMode;

    @Column(name = "start_balance", precision = 19, scale = 2)
    private BigDecimal startBalance;

    @Column(name = "sweep_enabled")
    private Boolean sweepEnabled;

    @Column(name = "cash_ceiling", precision = 19, scale = 2)
    private BigDecimal cashCeiling;

    @Column(name = "cash_floor", precision = 19, scale = 2)
    private BigDecimal cashFloor;

    @Column(name = "cash_alert_threshold", precision = 19, scale = 2)
    private BigDecimal cashAlertThreshold;

    public CashflowSettingsEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPivotDate() {
        return pivotDate;
    }

    public void setPivotDate(String pivotDate) {
        this.pivotDate = pivotDate;
    }

    public String getPivotMode() {
        return pivotMode;
    }

    public void setPivotMode(String pivotMode) {
        this.pivotMode = pivotMode;
    }

    public BigDecimal getStartBalance() {
        return startBalance;
    }

    public void setStartBalance(BigDecimal startBalance) {
        this.startBalance = startBalance;
    }

    public Boolean getSweepEnabled() {
        return sweepEnabled;
    }

    public void setSweepEnabled(Boolean sweepEnabled) {
        this.sweepEnabled = sweepEnabled;
    }

    public BigDecimal getCashCeiling() {
        return cashCeiling;
    }

    public void setCashCeiling(BigDecimal cashCeiling) {
        this.cashCeiling = cashCeiling;
    }

    public BigDecimal getCashFloor() {
        return cashFloor;
    }

    public void setCashFloor(BigDecimal cashFloor) {
        this.cashFloor = cashFloor;
    }

    public BigDecimal getCashAlertThreshold() {
        return cashAlertThreshold;
    }

    public void setCashAlertThreshold(BigDecimal cashAlertThreshold) {
        this.cashAlertThreshold = cashAlertThreshold;
    }
}
