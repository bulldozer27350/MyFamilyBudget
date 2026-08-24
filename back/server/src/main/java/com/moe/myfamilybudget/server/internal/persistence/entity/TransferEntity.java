package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "transfer")
public class TransferEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    private String placement;
    private String date;
    private BigDecimal amount;
    private String notes;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Constructors
    public TransferEntity() {}
    
    public TransferEntity(String uid, String placement, String date, BigDecimal amount, String notes) {
        this.uid = uid;
        this.placement = placement;
        this.date = date;
        this.amount = amount;
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
    
    public BudgetDataEntity getBudgetData() {
        return budgetData;
    }
    
    public void setBudgetData(BudgetDataEntity budgetData) {
        this.budgetData = budgetData;
    }
}
