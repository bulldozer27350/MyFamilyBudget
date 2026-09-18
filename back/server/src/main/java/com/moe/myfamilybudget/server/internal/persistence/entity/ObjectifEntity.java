package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "objectif")
public class ObjectifEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;
    private String label;
    private BigDecimal targetAmount;
    private String targetDate;
    private String sourcePlacementId;
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;

    // Constructors
    public ObjectifEntity() {}

    public ObjectifEntity(String uid, String label, BigDecimal targetAmount, String targetDate,
                           String sourcePlacementId, String notes) {
        this.uid = uid;
        this.label = label;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.sourcePlacementId = sourcePlacementId;
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

    public BudgetDataEntity getBudgetData() {
        return budgetData;
    }

    public void setBudgetData(BudgetDataEntity budgetData) {
        this.budgetData = budgetData;
    }
}
