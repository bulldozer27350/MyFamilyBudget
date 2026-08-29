package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "placement")
public class PlacementEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    private String label;
    private String category;
    private BigDecimal balance;
    private String balanceDate;
    private BigDecimal monthly;
    private String monthlyFrom;
    private String monthlyUntil;
    private BigDecimal ratePess;
    private BigDecimal rateCorr;
    private BigDecimal rateOpti;
    private Boolean excludedFromRetirement;
    private String notes;
    private Integer sweepPriority;
    private BigDecimal sweepCap;
    private BigDecimal pauseTriggerBalance;
    private Integer pausePriority;
    private String categoryId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;
    
    // Constructors
    public PlacementEntity() {}
    
    public PlacementEntity(String uid, String label, String category, BigDecimal balance, String balanceDate,
                          BigDecimal monthly, String monthlyFrom, String monthlyUntil, BigDecimal ratePess,
                          BigDecimal rateCorr, BigDecimal rateOpti, Boolean excludedFromRetirement, String notes) {
        this.uid = uid;
        this.label = label;
        this.category = category;
        this.balance = balance;
        this.balanceDate = balanceDate;
        this.monthly = monthly;
        this.monthlyFrom = monthlyFrom;
        this.monthlyUntil = monthlyUntil;
        this.ratePess = ratePess;
        this.rateCorr = rateCorr;
        this.rateOpti = rateOpti;
        this.excludedFromRetirement = excludedFromRetirement;
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
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public BigDecimal getBalance() {
        return balance;
    }
    
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
    
    public String getBalanceDate() {
        return balanceDate;
    }
    
    public void setBalanceDate(String balanceDate) {
        this.balanceDate = balanceDate;
    }
    
    public BigDecimal getMonthly() {
        return monthly;
    }
    
    public void setMonthly(BigDecimal monthly) {
        this.monthly = monthly;
    }
    
    public String getMonthlyFrom() {
        return monthlyFrom;
    }
    
    public void setMonthlyFrom(String monthlyFrom) {
        this.monthlyFrom = monthlyFrom;
    }
    
    public String getMonthlyUntil() {
        return monthlyUntil;
    }
    
    public void setMonthlyUntil(String monthlyUntil) {
        this.monthlyUntil = monthlyUntil;
    }
    
    public BigDecimal getRatePess() {
        return ratePess;
    }
    
    public void setRatePess(BigDecimal ratePess) {
        this.ratePess = ratePess;
    }
    
    public BigDecimal getRateCorr() {
        return rateCorr;
    }
    
    public void setRateCorr(BigDecimal rateCorr) {
        this.rateCorr = rateCorr;
    }
    
    public BigDecimal getRateOpti() {
        return rateOpti;
    }
    
    public void setRateOpti(BigDecimal rateOpti) {
        this.rateOpti = rateOpti;
    }
    
    public Boolean getExcludedFromRetirement() {
        return excludedFromRetirement;
    }
    
    public void setExcludedFromRetirement(Boolean excludedFromRetirement) {
        this.excludedFromRetirement = excludedFromRetirement;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getSweepPriority() {
        return sweepPriority;
    }

    public void setSweepPriority(Integer sweepPriority) {
        this.sweepPriority = sweepPriority;
    }

    public BigDecimal getSweepCap() {
        return sweepCap;
    }

    public void setSweepCap(BigDecimal sweepCap) {
        this.sweepCap = sweepCap;
    }

    public BigDecimal getPauseTriggerBalance() {
        return pauseTriggerBalance;
    }

    public void setPauseTriggerBalance(BigDecimal pauseTriggerBalance) {
        this.pauseTriggerBalance = pauseTriggerBalance;
    }

    public Integer getPausePriority() {
        return pausePriority;
    }

    public void setPausePriority(Integer pausePriority) {
        this.pausePriority = pausePriority;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }
    
    public BudgetDataEntity getBudgetData() {
        return budgetData;
    }
    
    public void setBudgetData(BudgetDataEntity budgetData) {
        this.budgetData = budgetData;
    }
}
