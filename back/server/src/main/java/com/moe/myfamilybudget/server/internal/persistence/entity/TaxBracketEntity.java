package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tax_bracket")
public class TaxBracketEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    private BigDecimal upTo;
    private BigDecimal rate;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Constructors
    public TaxBracketEntity() {}
    
    public TaxBracketEntity(String uid, BigDecimal upTo, BigDecimal rate) {
        this.uid = uid;
        this.upTo = upTo;
        this.rate = rate;
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
    
    public BigDecimal getUpTo() {
        return upTo;
    }
    
    public void setUpTo(BigDecimal upTo) {
        this.upTo = upTo;
    }
    
    public BigDecimal getRate() {
        return rate;
    }
    
    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }
    
    public BudgetDataEntity getBudgetData() {
        return budgetData;
    }
    
    public void setBudgetData(BudgetDataEntity budgetData) {
        this.budgetData = budgetData;
    }
}
