package com.moe.myfamilybudget.domain.treasury.core.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Virement vers un placement du domaine Tresorerie, cible JPA autonome (DB-1060).
 *
 * <p>Contrairement a {@code TransferEntity}, cette entite n'a <strong>aucune relation vers
 * {@code BudgetDataEntity}</strong> : elle est la racine de son propre agregat. Elle est additive et n'est pas
 * encore utilisee par les adapters (bascule en DB-1061). Le chemin legacy (table {@code transfer}) reste
 * inchange. Cle technique generee, {@code uid} = identifiant metier (aucune hypothese d'unicite, comme dans le
 * chemin legacy) et {@code position} pour restituer la liste dans l'ordre de saisie.
 */
@Entity
@Table(name = "cashflow_transfer")
public class CashflowTransferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "placement")
    private String placement;

    @Column(name = "entry_date", length = 32)
    private String date;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "notes", length = 2000)
    private String notes;

    public CashflowTransferEntity() {}

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

    public String getPlacement() {
        return placement;
    }

    public void setPlacement(String placement) {
        this.placement = placement;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
