package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "retirement_person")
public class RetirementPersonEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    private String name;
    private Integer birthYear;
    private String incomeLabel;
    private Integer trimestresValides;
    private String trimestresDate;
    private BigDecimal agircPoints;
    private BigDecimal ratioPointsParEuro;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "retirementPerson")
    private List<SalaryHistoryEntity> salaryHistory = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retirement_id")
    private RetirementEntity retirement;
    
    // Constructors
    public RetirementPersonEntity() {}
    
    public RetirementPersonEntity(String uid, String name, Integer birthYear, String incomeLabel,
                                 Integer trimestresValides, String trimestresDate, BigDecimal agircPoints,
                                 BigDecimal ratioPointsParEuro) {
        this.uid = uid;
        this.name = name;
        this.birthYear = birthYear;
        this.incomeLabel = incomeLabel;
        this.trimestresValides = trimestresValides;
        this.trimestresDate = trimestresDate;
        this.agircPoints = agircPoints;
        this.ratioPointsParEuro = ratioPointsParEuro;
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Integer getBirthYear() {
        return birthYear;
    }
    
    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }
    
    public String getIncomeLabel() {
        return incomeLabel;
    }
    
    public void setIncomeLabel(String incomeLabel) {
        this.incomeLabel = incomeLabel;
    }
    
    public Integer getTrimestresValides() {
        return trimestresValides;
    }
    
    public void setTrimestresValides(Integer trimestresValides) {
        this.trimestresValides = trimestresValides;
    }
    
    public String getTrimestresDate() {
        return trimestresDate;
    }
    
    public void setTrimestresDate(String trimestresDate) {
        this.trimestresDate = trimestresDate;
    }
    
    public BigDecimal getAgircPoints() {
        return agircPoints;
    }
    
    public void setAgircPoints(BigDecimal agircPoints) {
        this.agircPoints = agircPoints;
    }
    
    public BigDecimal getRatioPointsParEuro() {
        return ratioPointsParEuro;
    }
    
    public void setRatioPointsParEuro(BigDecimal ratioPointsParEuro) {
        this.ratioPointsParEuro = ratioPointsParEuro;
    }
    
    public List<SalaryHistoryEntity> getSalaryHistory() {
        return salaryHistory;
    }
    
    public void setSalaryHistory(List<SalaryHistoryEntity> salaryHistory) {
        this.salaryHistory = salaryHistory;
    }
    
    public RetirementEntity getRetirement() {
        return retirement;
    }
    
    public void setRetirement(RetirementEntity retirement) {
        this.retirement = retirement;
    }
}
