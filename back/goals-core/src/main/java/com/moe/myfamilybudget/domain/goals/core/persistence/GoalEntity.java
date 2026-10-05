package com.moe.myfamilybudget.domain.goals.core.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Objectif d'epargne, cible JPA autonome du domaine Objectifs (DB-1020).
 *
 * <p>Cette entite n'a <strong>aucune relation vers {@code BudgetDataEntity}</strong> : elle est la racine de
 * son propre agregat. Elle est lue et ecrite par {@link JpaGoalStore} (SILO-212, lot B1) et, depuis DB-1120,
 * c'est aussi la seule source de chargement du cache : les anciennes tables {@code objectif} /
 * {@code objectif_allocation} et leurs entites ont ete supprimees.
 */
@Entity
@Table(name = "goal")
public class GoalEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    /** Rang dans la liste, pour restituer les objectifs dans l'ordre de saisie. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label")
    private String label;

    @Column(name = "target_amount", precision = 19, scale = 2)
    private BigDecimal targetAmount;

    // Champs historiques (mono-compte), conserves a l'identique du modele pour un aller-retour sans perte.
    @Column(name = "allocated_amount", precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(name = "target_date", length = 32)
    private String targetDate;

    @Column(name = "source_placement_id", length = 64)
    private String sourcePlacementId;

    @Column(name = "notes", length = 2000)
    private String notes;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<GoalAllocationEntity> allocations = new ArrayList<>();

    public GoalEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public void setAllocatedAmount(BigDecimal allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
    }

    public String getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(String targetDate) {
        this.targetDate = targetDate;
    }

    public String getSourcePlacementId() {
        return sourcePlacementId;
    }

    public void setSourcePlacementId(String sourcePlacementId) {
        this.sourcePlacementId = sourcePlacementId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<GoalAllocationEntity> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<GoalAllocationEntity> allocations) {
        this.allocations = allocations;
    }
}
