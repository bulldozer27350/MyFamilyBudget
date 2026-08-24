package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "real_estate")
public class RealEstateEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    private String label;
    private String type;
    private BigDecimal currentValue;
    private Integer valuationYear;
    private BigDecimal annualGrowthRate;
    private String notes;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Constructors
    public RealEstateEntity() {}
    
    public RealEstateEntity(String uid, String label, String type, BigDecimal currentValue, 
                           Integer valuationYear, BigDecimal annualGrowthRate, String notes) {
        this.uid = uid;
        this.label = label;
        this.type = type;
        this.currentValue = currentValue;
        this.valuationYear = valuationYear;
        this.annualGrowthRate = annualGrowthRate;
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
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public BigDecimal getCurrentValue() {
        return currentValue;
    }
    
    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }
    
    public Integer getValuationYear() {
        return valuationYear;
    }
    
    public void setValuationYear(Integer valuationYear) {
        this.valuationYear = valuationYear;
    }
    
    public BigDecimal getAnnualGrowthRate() {
        return annualGrowthRate;
    }
    
    public void setAnnualGrowthRate(BigDecimal annualGrowthRate) {
        this.annualGrowthRate = annualGrowthRate;
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
