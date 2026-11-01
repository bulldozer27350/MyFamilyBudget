package com.moe.myfamilybudget.domain.tax.core.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Paramètres généraux dont le silo Fiscalité est propriétaire (SILO-220, lot A) : âge de sortie des enfants et
 * abattement. Singleton fonctionnel : une seule ligne, lue par {@code findFirstByOrderByIdAsc}.
 *
 * <p>Additif : aucune relation vers {@code BudgetDataEntity}, pas encore utilisée par
 * {@code SettingsPersistenceAdapter} (bascule au lot B). Les colonnes équivalentes de la table historique
 * {@code settings} restent la source de vérité jusque-là. L'abattement est stocké avec 8 décimales, comme
 * dans la table historique.
 */
@Entity
@Table(name = "fiscal_settings")
public class FiscalSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_exit_age")
    private Integer childExitAge;

    @Column(name = "tax_abattement", precision = 19, scale = 8)
    private BigDecimal taxAbattement;

    public FiscalSettingsEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
