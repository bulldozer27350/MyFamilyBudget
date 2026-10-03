package com.moe.myfamilybudget.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Salaire annuel d'une personne (DB-1000). Entite enfant de {@link PensionPersonEntity}, qui porte la cle
 * etrangere {@code person_id} (cote proprietaire). Aucune hypothese d'unicite sur l'annee.
 */
@Entity
@Table(name = "pension_salary")
public class PensionSalaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Rang dans l'historique de la personne. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "year_value")
    private Integer year;

    @Column(name = "salary", precision = 19, scale = 2)
    private BigDecimal salary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private PensionPersonEntity person;

    public PensionSalaryEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
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

    public PensionPersonEntity getPerson() {
        return person;
    }

    public void setPerson(PensionPersonEntity person) {
        this.person = person;
    }
}
