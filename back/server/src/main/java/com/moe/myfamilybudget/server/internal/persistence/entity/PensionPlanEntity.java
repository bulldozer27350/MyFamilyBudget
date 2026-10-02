package com.moe.myfamilybudget.server.internal.persistence.entity;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Hypotheses et personnes du domaine Retraite, cible JPA autonome (DB-1000).
 *
 * <p>Contrairement a {@link RetirementEntity}, cette entite n'a <strong>aucune relation vers
 * {@link BudgetDataEntity}</strong> : elle est la racine de son propre agregat (singleton fonctionnel : une
 * seule ligne, lue par {@code findFirstByOrderByIdAsc}). Elle est additive et n'est pas encore utilisee par
 * {@code RetirementPersistenceAdapter} (bascule en DB-1001). Le chemin legacy ({@code retirement},
 * {@code retirement_person}, {@code salary_history}) reste inchange. Le prefixe {@code Pension*} evite la
 * collision avec les classes legacy {@code Retirement*}.
 *
 * <p>Les taux, valeurs de point et ratios sont stockes avec 8 decimales (une precision par defaut les
 * arrondirait, voir {@code RatePrecisionPersistenceTest}) ; le PASS en 2 decimales.
 *
 * <p>Les collections sont {@code EAGER} en {@link FetchMode#SELECT} : deux listes ({@code people} et
 * {@code salaryHistory}) chargees par jointure declencheraient une {@code MultipleBagFetchException}.
 */
@Entity
@Table(name = "pension_plan")
public class PensionPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pass_2026", precision = 19, scale = 2)
    private BigDecimal pass2026;

    @Column(name = "pass_growth_rate", precision = 19, scale = 8)
    private BigDecimal passGrowthRate;

    @Column(name = "agirc_point_value", precision = 19, scale = 8)
    private BigDecimal agircPointValue;

    @Column(name = "agirc_point_date_global", length = 32)
    private String agircPointDateGlobal;

    @Column(name = "agirc_point_growth_rate", precision = 19, scale = 8)
    private BigDecimal agircPointGrowthRate;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @OrderBy("position ASC")
    private List<PensionPersonEntity> people = new ArrayList<>();

    public PensionPlanEntity() {}

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

    public List<PensionPersonEntity> getPeople() {
        return people;
    }

    public void setPeople(List<PensionPersonEntity> people) {
        this.people = people;
    }
}
