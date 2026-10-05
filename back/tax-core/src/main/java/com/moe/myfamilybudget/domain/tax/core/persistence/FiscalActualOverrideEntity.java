package com.moe.myfamilybudget.domain.tax.core.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Impot reel saisi pour une annee, cible JPA autonome du domaine Fiscalite (DB-1010).
 *
 * <p>Aucune relation vers {@code BudgetDataEntity} : additive, pas encore utilisee par
 * {@code TaxPersistenceAdapter} (bascule en DB-1011). Le chemin legacy reste inchange. Cle technique
 * generee (aucune hypothese d'unicite sur les champs metier, comme dans le chemin legacy) et {@code position}
 * pour restituer la liste dans l'ordre de saisie.
 */
@Entity
@Table(name = "fiscal_actual_override")
public class FiscalActualOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "year_value")
    private Integer year;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    public FiscalActualOverrideEntity() {}

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
