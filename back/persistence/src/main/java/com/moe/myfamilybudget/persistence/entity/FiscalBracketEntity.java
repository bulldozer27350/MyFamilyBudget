package com.moe.myfamilybudget.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tranche du bareme fiscal (plafond {@code upTo} nul = derniere tranche), cible JPA autonome du domaine Fiscalite (DB-1010).
 *
 * <p>Aucune relation vers {@link BudgetDataEntity} : additive, pas encore utilisee par
 * {@code TaxPersistenceAdapter} (bascule en DB-1011). Le chemin legacy reste inchange. Cle technique
 * generee (aucune hypothese d'unicite sur les champs metier, comme dans le chemin legacy) et {@code position}
 * pour restituer la liste dans l'ordre de saisie.
 */
@Entity
@Table(name = "fiscal_bracket")
public class FiscalBracketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "uid", length = 64)
    private String uid;

    @Column(name = "up_to", precision = 19, scale = 2)
    private BigDecimal upTo;

    @Column(name = "rate", precision = 19, scale = 8)
    private BigDecimal rate;

    public FiscalBracketEntity() {}

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

    public BigDecimal getUpTo() {
        return upTo;
    }

    public void setUpTo(BigDecimal upTo) {
        this.upTo = upTo;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }
}
