package com.moe.myfamilybudget.domain.treasury.core.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Charge mensuelle du domaine Tresorerie, cible JPA autonome (DB-1060).
 *
 * <p>Contrairement a {@code ChargeEntity}, cette entite n'a <strong>aucune relation vers
 * {@code BudgetDataEntity}</strong> : elle est la racine de son propre agregat. Elle est additive et n'est pas
 * encore utilisee par les adapters (bascule en DB-1061). Le chemin legacy (table {@code charge}) reste
 * inchange. Cle technique generee, {@code uid} = identifiant metier (aucune hypothese d'unicite, comme dans le
 * chemin legacy) et {@code position} pour restituer la liste dans l'ordre de saisie.
 */
@Entity
@Table(name = "cashflow_charge")
public class CashflowChargeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label")
    private String label;

    @Column(name = "monthly", precision = 19, scale = 2)
    private BigDecimal monthly;

    @Column(name = "start_date", length = 32)
    private String start;

    @Column(name = "end_date", length = 32)
    private String end;

    @Column(name = "growth_rate", precision = 19, scale = 8)
    private BigDecimal growthRate;

    @Column(name = "category_id", length = 64)
    private String categoryId;

    @Column(name = "notes", length = 2000)
    private String notes;

    public CashflowChargeEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getMonthly() {
        return monthly;
    }

    public void setMonthly(BigDecimal monthly) {
        this.monthly = monthly;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public BigDecimal getGrowthRate() {
        return growthRate;
    }

    public void setGrowthRate(BigDecimal growthRate) {
        this.growthRate = growthRate;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
