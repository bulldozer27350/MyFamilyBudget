package com.moe.myfamilybudget.domain.retirement.core.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Paramètres généraux dont le silo Retraite est propriétaire (SILO-220, lot A) : année de naissance et âge de
 * départ. Singleton fonctionnel : une seule ligne, lue par {@code findFirstByOrderByIdAsc}.
 *
 * <p>R-40 (lot B) : cette entité n'a <strong>aucune relation vers {@code BudgetDataEntity}</strong> et est
 * désormais le support exclusif en lecture et en écriture, via {@code JpaRetirementStore}
 * ({@code RetirementSettingsReader}/{@code RetirementSnapshotWriter}) ; {@code SettingsPersistenceAdapter}
 * conserve son ancienne implémentation (table historique {@code settings}) mais n'est plus utilisé pour ces
 * deux champs (voir {@code BudgetDataModelAllowList}). Le PASS et son taux de croissance sont portés par
 * {@link PensionPlanEntity}.
 */
@Entity
@Table(name = "pension_settings")
public class PensionSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "retire_age")
    private Integer retireAge;

    public PensionSettingsEntity() {}

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
}
