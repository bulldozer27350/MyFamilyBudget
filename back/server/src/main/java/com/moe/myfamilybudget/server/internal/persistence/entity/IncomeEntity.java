package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "income")
public class IncomeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    private String label;
    private BigDecimal monthly;
    private String start;
    @Column(name = "end_date")
    private String end;
    private BigDecimal growthRate;
    private String categoryId;
    private String notes;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Constructors
    public IncomeEntity() {}
    
    public IncomeEntity(String uid, String label, BigDecimal monthly, String start, String end, 
                       BigDecimal growthRate, String categoryId, String notes) {
        this.uid = uid;
        this.label = label;
        this.monthly = monthly;
        this.start = start;
        this.end = end;
        this.growthRate = growthRate;
        this.categoryId = categoryId;
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
    
    public BigDecimal getMonthly() {
        return monthly;
    }
    
    public void setMonthly(BigDecimal monthly) {
        this.monthly = monthly;
    }
    
    public String getStart() {
        return start;
    }
    
    public void setStart(String start) {
        this.start = start;
    }
    
    public String getEnd() {
        return end;
    }
    
    public void setEnd(String end) {
        this.end = end;
    }
    
    public BigDecimal getGrowthRate() {
        return growthRate;
    }
    
    public void setGrowthRate(BigDecimal growthRate) {
        this.growthRate = growthRate;
    }
    
    public String getCategoryId() {
        return categoryId;
    }
    
    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
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
