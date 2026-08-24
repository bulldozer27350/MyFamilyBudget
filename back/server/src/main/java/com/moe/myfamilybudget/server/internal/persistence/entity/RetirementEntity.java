package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "retirement")
public class RetirementEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private BigDecimal pass2026;
    private BigDecimal passGrowthRate;
    private BigDecimal agircPointValue;
    private String agircPointDateGlobal;
    private BigDecimal agircPointGrowthRate;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RetirementPersonEntity> people = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Constructors
    public RetirementEntity() {}
    
    public RetirementEntity(BigDecimal pass2026, BigDecimal passGrowthRate, 
                            BigDecimal agircPointValue, String agircPointDateGlobal, 
                            BigDecimal agircPointGrowthRate) {
        this.pass2026 = pass2026;
        this.passGrowthRate = passGrowthRate;
        this.agircPointValue = agircPointValue;
        this.agircPointDateGlobal = agircPointDateGlobal;
        this.agircPointGrowthRate = agircPointGrowthRate;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public BigDecimal getPass2026() {
        return pass2026;
    }
    
    public void setPass2026(BigDecimal pass2026) {
        this.pass2026 = pass2026;
    }
    
    public BigDecimal getPassGrowthRate() {
        return passGrowthRate;
    }
    
    public void setPassGrowthRate(BigDecimal passGrowthRate) {
        this.passGrowthRate = passGrowthRate;
    }
    
    public BigDecimal getAgircPointValue() {
        return agircPointValue;
    }
    
    public void setAgircPointValue(BigDecimal agircPointValue) {
        this.agircPointValue = agircPointValue;
    }
    
    public String getAgircPointDateGlobal() {
        return agircPointDateGlobal;
    }
    
    public void setAgircPointDateGlobal(String agircPointDateGlobal) {
        this.agircPointDateGlobal = agircPointDateGlobal;
    }
    
    public BigDecimal getAgircPointGrowthRate() {
        return agircPointGrowthRate;
    }
    
    public void setAgircPointGrowthRate(BigDecimal agircPointGrowthRate) {
        this.agircPointGrowthRate = agircPointGrowthRate;
    }
    
    public List<RetirementPersonEntity> getPeople() {
        return people;
    }
    
    public void setPeople(List<RetirementPersonEntity> people) {
        this.people = people;
    }
    
    public BudgetDataEntity getBudgetData() {
        return budgetData;
    }
    
    public void setBudgetData(BudgetDataEntity budgetData) {
        this.budgetData = budgetData;
    }
}
