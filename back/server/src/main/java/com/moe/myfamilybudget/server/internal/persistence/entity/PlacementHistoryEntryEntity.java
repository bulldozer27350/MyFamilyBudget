package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Une valeur reelle constatee pour un placement/compte a une date donnee (releve mensuel,
 * annuel...), saisie depuis la fenetre dediee "Historique" de l'onglet Patrimoine.
 */
@Entity
@Table(name = "placement_history_entry")
public class PlacementHistoryEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;
    private String date;
    // "value" est un mot reserve H2 (mot-cle VALUE) : sans @Column explicite, Hibernate
    // genere une colonne "value" non echappee, ce qui casse le SELECT genere a l'execution
    // (meme probleme deja rencontre sur SalaryHistoryEntity#year -> "year_value").
    @Column(name = "value_amount")
    private BigDecimal value;
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_id")
    private PlacementEntity placement;

    // Constructors
    public PlacementHistoryEntryEntity() {}

    public PlacementHistoryEntryEntity(String uid, String date, BigDecimal value, String notes) {
        this.uid = uid;
        this.date = date;
        this.value = value;
        this.notes = notes;
    }

    // Getters and Setters
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

    public PlacementEntity getPlacement() {
        return placement;
    }

    public void setPlacement(PlacementEntity placement) {
        this.placement = placement;
    }
}
