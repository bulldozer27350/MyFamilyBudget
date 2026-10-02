package com.moe.myfamilybudget.server.internal.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Bien immobilier du domaine Patrimoine, cible JPA autonome (DB-1050). Aucune relation vers
 * {@link BudgetDataEntity} ; additive, pas encore utilisee par {@code PatrimoinePersistenceAdapter}
 * (bascule en DB-1051). Le chemin legacy ({@code real_estate}) reste inchange. Taux de croissance annuel en
 * {@code NUMERIC(19,8)}.
 */
@Entity
@Table(name = "wealth_real_estate")
public class WealthRealEstateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label")
    private String label;

    @Column(name = "type")
    private String type;

    @Column(name = "current_value", precision = 19, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "valuation_year")
    private Integer valuationYear;

    @Column(name = "annual_growth_rate", precision = 19, scale = 8)
    private BigDecimal annualGrowthRate;

    @Column(name = "notes", length = 2000)
    private String notes;

    public WealthRealEstateEntity() {}

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public Integer getValuationYear() {
        return valuationYear;
    }

    public void setValuationYear(Integer valuationYear) {
        this.valuationYear = valuationYear;
    }

    public BigDecimal getAnnualGrowthRate() {
        return annualGrowthRate;
    }

    public void setAnnualGrowthRate(BigDecimal annualGrowthRate) {
        this.annualGrowthRate = annualGrowthRate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
