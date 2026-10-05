package com.moe.myfamilybudget.domain.treasury.core.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Regle de revenu variable (prime, bonus...) du domaine Tresorerie, cible JPA autonome (DB-1060).
 *
 * <p>Contrairement a {@code VariableIncomeEntity}, cette entite n'a <strong>aucune relation vers
 * {@code BudgetDataEntity}</strong> : elle est la racine de son propre agregat. Elle est additive et n'est pas
 * encore utilisee par les adapters (bascule en DB-1061). Le chemin legacy (table {@code variable_income}) reste
 * inchange. Cle technique generee, {@code uid} = identifiant metier (aucune hypothese d'unicite, comme dans le
 * chemin legacy) et {@code position} pour restituer la liste dans l'ordre de saisie.
 */
@Entity
@Table(name = "cashflow_variable_income")
public class CashflowVariableIncomeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label")
    private String label;

    @Column(name = "ref_income_label")
    private String refIncomeLabel;

    @Column(name = "rate", precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(name = "taxable", length = 32)
    private String taxable;

    @Column(name = "variable_type", length = 32)
    private String type;

    @Column(name = "notes", length = 2000)
    private String notes;

    public CashflowVariableIncomeEntity() {}

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

    public String getRefIncomeLabel() {
        return refIncomeLabel;
    }

    public void setRefIncomeLabel(String refIncomeLabel) {
        this.refIncomeLabel = refIncomeLabel;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Integer getStartYear() {
        return startYear;
    }

    public void setStartYear(Integer startYear) {
        this.startYear = startYear;
    }

    public Integer getEndYear() {
        return endYear;
    }

    public void setEndYear(Integer endYear) {
        this.endYear = endYear;
    }

    public String getTaxable() {
        return taxable;
    }

    public void setTaxable(String taxable) {
        this.taxable = taxable;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
