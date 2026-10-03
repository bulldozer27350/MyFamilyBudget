package com.moe.myfamilybudget.persistence.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Personne du foyer pour la retraite (DB-1000). Entite enfant de {@link PensionPlanEntity}, qui porte la cle
 * etrangere {@code plan_id} (cote proprietaire). Cle technique generee et {@code position} pour restituer la
 * liste dans l'ordre de saisie ; {@code uid} est l'identifiant metier ({@code RetirementPersonModel#id}),
 * sans hypothese d'unicite.
 */
@Entity
@Table(name = "pension_person")
public class PensionPersonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "name")
    private String name;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "income_label")
    private String incomeLabel;

    @Column(name = "trimestres_valides")
    private Integer trimestresValides;

    @Column(name = "trimestres_date", length = 32)
    private String trimestresDate;

    @Column(name = "agirc_points", precision = 19, scale = 8)
    private BigDecimal agircPoints;

    @Column(name = "ratio_points_par_euro", precision = 19, scale = 8)
    private BigDecimal ratioPointsParEuro;

    @Column(name = "cadre")
    private Boolean cadre;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @OrderBy("position ASC")
    private List<PensionSalaryEntity> salaryHistory = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private PensionPlanEntity plan;

    public PensionPersonEntity() {}

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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
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

    public Boolean getCadre() {
        return cadre;
    }

    public void setCadre(Boolean cadre) {
        this.cadre = cadre;
    }

    public List<PensionSalaryEntity> getSalaryHistory() {
        return salaryHistory;
    }

    public void setSalaryHistory(List<PensionSalaryEntity> salaryHistory) {
        this.salaryHistory = salaryHistory;
    }

    public PensionPlanEntity getPlan() {
        return plan;
    }

    public void setPlan(PensionPlanEntity plan) {
        this.plan = plan;
    }
}
