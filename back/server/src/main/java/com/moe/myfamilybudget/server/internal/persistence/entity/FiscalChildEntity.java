package com.moe.myfamilybudget.server.internal.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Enfant a charge du foyer fiscal, cible JPA autonome du domaine Fiscalite (DB-1010).
 *
 * <p>Aucune relation vers {@link BudgetDataEntity} : additive, pas encore utilisee par
 * {@code TaxPersistenceAdapter} (bascule en DB-1011). Le chemin legacy reste inchange. Cle technique
 * generee (aucune hypothese d'unicite sur les champs metier, comme dans le chemin legacy) et {@code position}
 * pour restituer la liste dans l'ordre de saisie.
 */
@Entity
@Table(name = "fiscal_child")
public class FiscalChildEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "name")
    private String name;

    @Column(name = "birth_year")
    private Integer birthYear;

    public FiscalChildEntity() {}

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
}
