package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "variable_income")
public class VariableIncomeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    private String label;
    private String refIncomeLabel;
    private BigDecimal rate;
    private Integer startYear;
    private Integer endYear;
    private String taxable;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Constructors
    public VariableIncomeEntity() {}
    
    public VariableIncomeEntity(String uid, String label, String refIncomeLabel, BigDecimal rate,
                               Integer startYear, Integer endYear, String taxable) {
        this.uid = uid;
        this.label = label;
        this.refIncomeLabel = refIncomeLabel;
        this.rate = rate;
        this.startYear = startYear;
        this.endYear = endYear;
        this.taxable = taxable;
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
    
    public String getRefIncomeLabel() {
        return refIncomeLabel;
    }
    
    public void setRefIncomeLabel(String refIncomeLabel) {
        this.refIncomeLabel = refIncomeLabel;
    }
    
    public BigDecimal getRate() {
        return rate;
    }
    
    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }
    
    public Integer getStartYear() {
        return startYear;
    }
    
    public void setStartYear(Integer startYear) {
        this.startYear = startYear;
    }
    
    public Integer getEndYear() {
        return endYear;
    }
    
    public void setEndYear(Integer endYear) {
        this.endYear = endYear;
    }
    
    public String getTaxable() {
        return taxable;
    }
    
    public void setTaxable(String taxable) {
        this.taxable = taxable;
    }
    
    public BudgetDataEntity getBudgetData() {
        return budgetData;
    }
    
    public void setBudgetData(BudgetDataEntity budgetData) {
        this.budgetData = budgetData;
    }
}
