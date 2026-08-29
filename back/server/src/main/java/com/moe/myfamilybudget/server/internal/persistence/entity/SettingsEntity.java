package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "settings")
public class SettingsEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Integer birthYear;
    private Integer retireAge;
    private Integer simulateUntilAge;
    private BigDecimal inflationRate;
    private String pivotDate;
    private String pivotMode;
    private BigDecimal startBalance;
    private Integer childExitAge;
    private BigDecimal taxAbattement;
    private BigDecimal pass2026;
    private BigDecimal passGrowthRate;
    private Boolean sweepEnabled;
    private BigDecimal cashCeiling;
    private BigDecimal cashFloor;
    
    // Constructors
    public SettingsEntity() {}
    
    public SettingsEntity(Integer birthYear, Integer retireAge, Integer simulateUntilAge, 
                          BigDecimal inflationRate, String pivotDate, String pivotMode,
                          BigDecimal startBalance, Integer childExitAge, BigDecimal taxAbattement,
                          BigDecimal pass2026, BigDecimal passGrowthRate) {
        this.birthYear = birthYear;
        this.retireAge = retireAge;
        this.simulateUntilAge = simulateUntilAge;
        this.inflationRate = inflationRate;
        this.pivotDate = pivotDate;
        this.pivotMode = pivotMode;
        this.startBalance = startBalance;
        this.childExitAge = childExitAge;
        this.taxAbattement = taxAbattement;
        this.pass2026 = pass2026;
        this.passGrowthRate = passGrowthRate;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Integer getBirthYear() {
        return birthYear;
    }
    
    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }
    
    public Integer getRetireAge() {
        return retireAge;
    }
    
    public void setRetireAge(Integer retireAge) {
        this.retireAge = retireAge;
    }
    
    public Integer getSimulateUntilAge() {
        return simulateUntilAge;
    }
    
    public void setSimulateUntilAge(Integer simulateUntilAge) {
        this.simulateUntilAge = simulateUntilAge;
    }
    
    public BigDecimal getInflationRate() {
        return inflationRate;
    }
    
    public void setInflationRate(BigDecimal inflationRate) {
        this.inflationRate = inflationRate;
    }
    
    public String getPivotDate() {
        return pivotDate;
    }
    
    public void setPivotDate(String pivotDate) {
        this.pivotDate = pivotDate;
    }
    
    public String getPivotMode() {
        return pivotMode;
    }
    
    public void setPivotMode(String pivotMode) {
        this.pivotMode = pivotMode;
    }
    
    public BigDecimal getStartBalance() {
        return startBalance;
    }
    
    public void setStartBalance(BigDecimal startBalance) {
        this.startBalance = startBalance;
    }
    
    public Integer getChildExitAge() {
        return childExitAge;
    }
    
    public void setChildExitAge(Integer childExitAge) {
        this.childExitAge = childExitAge;
    }
    
    public BigDecimal getTaxAbattement() {
        return taxAbattement;
    }
    
    public void setTaxAbattement(BigDecimal taxAbattement) {
        this.taxAbattement = taxAbattement;
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

    public Boolean getSweepEnabled() {
        return sweepEnabled;
    }

    public void setSweepEnabled(Boolean sweepEnabled) {
        this.sweepEnabled = sweepEnabled;
    }

    public BigDecimal getCashCeiling() {
        return cashCeiling;
    }

    public void setCashCeiling(BigDecimal cashCeiling) {
        this.cashCeiling = cashCeiling;
    }

    public BigDecimal getCashFloor() {
        return cashFloor;
    }

    public void setCashFloor(BigDecimal cashFloor) {
        this.cashFloor = cashFloor;
    }
}
