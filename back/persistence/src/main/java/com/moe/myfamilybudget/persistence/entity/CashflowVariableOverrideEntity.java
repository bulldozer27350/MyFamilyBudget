package com.moe.myfamilybudget.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Surcharge annuelle d'un revenu variable du domaine Tresorerie, cible JPA autonome (DB-1060).
 *
 * <p>Contrairement a {@link VariableOverrideEntity}, cette entite n'a <strong>aucune relation vers
 * {@link BudgetDataEntity}</strong> : elle est la racine de son propre agregat. Elle est additive et n'est pas
 * encore utilisee par les adapters (bascule en DB-1061). Le chemin legacy (table {@code variable_override}) reste
 * inchange. Cle technique generee, {@code uid} = identifiant metier (aucune hypothese d'unicite, comme dans le
 * chemin legacy) et {@code position} pour restituer la liste dans l'ordre de saisie.
 */
@Entity
@Table(name = "cashflow_variable_override")
public class CashflowVariableOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label")
    private String label;

    @Column(name = "year_value")
    private Integer year;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "taxable", length = 32)
    private String taxable;

    @Column(name = "notes", length = 2000)
    private String notes;

    public CashflowVariableOverrideEntity() {}

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

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTaxable() {
        return taxable;
    }

    public void setTaxable(String taxable) {
        this.taxable = taxable;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
