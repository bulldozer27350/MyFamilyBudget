package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "salary_history")
public class SalaryHistoryEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "year_value")
    private Integer year;
    private BigDecimal salary;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retirement_person_id")
    private RetirementPersonEntity retirementPerson;
    
    // Constructors
    public SalaryHistoryEntity() {}
    
    public SalaryHistoryEntity(Integer year, BigDecimal salary) {
        this.year = year;
        this.salary = salary;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Integer getYear() {
        return year;
    }
    
    public void setYear(Integer year) {
        this.year = year;
    }
    
    public BigDecimal getSalary() {
        return salary;
    }
    
    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }
    
    public RetirementPersonEntity getRetirementPerson() {
        return retirementPerson;
    }
    
    public void setRetirementPerson(RetirementPersonEntity retirementPerson) {
        this.retirementPerson = retirementPerson;
    }
}
