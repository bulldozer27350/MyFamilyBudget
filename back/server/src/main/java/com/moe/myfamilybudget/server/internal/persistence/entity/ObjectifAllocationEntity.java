package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Une allocation d'un objectif d'épargne sur un compte donné (voir ObjectifAllocationModel).
 * Mêmes précautions que PlacementHistoryEntryEntity : entité enfant possédant la colonne FK
 * "objectif_id", @ManyToOne côté propriétaire.
 */
@Entity
@Table(name = "objectif_allocation")
public class ObjectifAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;
    private String placementId;
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "objectif_id")
    private ObjectifEntity objectif;

    // Constructors
    public ObjectifAllocationEntity() {}

    public ObjectifAllocationEntity(String uid, String placementId, BigDecimal amount) {
        this.uid = uid;
        this.placementId = placementId;
        this.amount = amount;
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

    public String getPlacementId() {
        return placementId;
    }

    public void setPlacementId(String placementId) {
        this.placementId = placementId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public ObjectifEntity getObjectif() {
        return objectif;
    }

    public void setObjectif(ObjectifEntity objectif) {
        this.objectif = objectif;
    }
}
