package com.moe.myfamilybudget.server.internal.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Valeur reelle constatee d'un placement a une date donnee (DB-1050). Entite enfant de
 * {@link WealthPlacementEntity}, qui porte la cle etrangere {@code placement_id} (cote proprietaire).
 * {@code value} est un mot reserve H2 : la colonne s'appelle {@code value_amount}.
 */
@Entity
@Table(name = "wealth_placement_history")
public class WealthPlacementHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    /** Rang dans l'historique du placement. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "entry_date", length = 32)
    private String date;

    @Column(name = "value_amount", precision = 19, scale = 2)
    private BigDecimal value;

    @Column(name = "notes", length = 2000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_id", nullable = false)
    private WealthPlacementEntity placement;

    public WealthPlacementHistoryEntity() {}

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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public WealthPlacementEntity getPlacement() {
        return placement;
    }

    public void setPlacement(WealthPlacementEntity placement) {
        this.placement = placement;
    }
}
