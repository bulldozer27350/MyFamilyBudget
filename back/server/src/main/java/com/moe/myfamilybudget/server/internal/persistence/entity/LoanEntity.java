package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "loan")
public class LoanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;
    private String label;
    private BigDecimal crd;
    private BigDecimal rate;
    private BigDecimal monthly;
    private BigDecimal insurance;
    private String startDate;
    private String endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_data_id")
    private BudgetDataEntity budgetData;

    // Constructors
    public LoanEntity() {}

    public LoanEntity(String uid, String label, BigDecimal crd, BigDecimal rate,
                      BigDecimal monthly, BigDecimal insurance, String startDate, String endDate) {
        this.uid = uid;
        this.label = label;
        this.crd = crd;
        this.rate = rate;
        this.monthly = monthly;
        this.insurance = insurance;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public BigDecimal getCrd() { return crd; }
    public void setCrd(BigDecimal crd) { this.crd = crd; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public BigDecimal getMonthly() { return monthly; }
    public void setMonthly(BigDecimal monthly) { this.monthly = monthly; }
    public BigDecimal getInsurance() { return insurance; }
    public void setInsurance(BigDecimal insurance) { this.insurance = insurance; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public BudgetDataEntity getBudgetData() { return budgetData; }
    public void setBudgetData(BudgetDataEntity budgetData) { this.budgetData = budgetData; }
}
