package com.moe.myfamilybudget.domain.credit.core.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Pret, cible JPA autonome du domaine Credit (DB-1040).
 *
 * <p>Cette entite n'a <strong>aucune relation vers {@code BudgetDataEntity}</strong> : elle est la racine de
 * son propre agregat. Depuis SILO-214 (lot B), elle est la seule source des prets : lue et ecrite par
 * {@code JpaLoanStore}. L'ancienne table {@code loan} du hub n'est plus alimentee ; elle reste en base, sans
 * entite, jusqu'a la suppression des tables legacy (SILO-230).
 *
 * <p>Le taux est stocke en fraction (0,0251 = 2,51 %) avec 8 decimales : une precision par defaut
 * l'arrondirait (voir {@code RatePrecisionPersistenceTest}).
 */
@Entity
@Table(name = "credit_loan")
public class CreditLoanEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    /** Rang dans la liste, pour restituer les prets dans l'ordre de saisie. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "label")
    private String label;

    @Column(name = "crd", precision = 19, scale = 2)
    private BigDecimal crd;

    @Column(name = "rate", precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "monthly", precision = 19, scale = 2)
    private BigDecimal monthly;

    @Column(name = "insurance", precision = 19, scale = 2)
    private BigDecimal insurance;

    @Column(name = "start_date", length = 32)
    private String startDate;

    @Column(name = "end_date", length = 32)
    private String endDate;

    // Informations du contrat bancaire (optionnelles) : tableau d'amortissement.
    @Column(name = "initial_amount", precision = 19, scale = 2)
    private BigDecimal initialAmount;

    @Column(name = "total_installments")
    private Integer totalInstallments;

    @Column(name = "step_date", length = 32)
    private String stepDate;

    public CreditLoanEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getCrd() {
        return crd;
    }

    public void setCrd(BigDecimal crd) {
        this.crd = crd;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getMonthly() {
        return monthly;
    }

    public void setMonthly(BigDecimal monthly) {
        this.monthly = monthly;
    }

    public BigDecimal getInsurance() {
        return insurance;
    }

    public void setInsurance(BigDecimal insurance) {
        this.insurance = insurance;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getInitialAmount() {
        return initialAmount;
    }

    public void setInitialAmount(BigDecimal initialAmount) {
        this.initialAmount = initialAmount;
    }

    public Integer getTotalInstallments() {
        return totalInstallments;
    }

    public void setTotalInstallments(Integer totalInstallments) {
        this.totalInstallments = totalInstallments;
    }

    public String getStepDate() {
        return stepDate;
    }

    public void setStepDate(String stepDate) {
        this.stepDate = stepDate;
    }
}
