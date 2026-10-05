package com.moe.myfamilybudget.domain.treasury.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pensions de retraite perçues, annualisées et sommées pour toutes les personnes du foyer : contrat
 * d'entrée propre à Trésorerie (SILO-132, décision D1 de doc/architecture/21-plan-silotage.md).
 *
 * <p>Trésorerie ne connaît pas le domaine Retraite : l'application traduit la projection de Retraite
 * vers ce type.
 *
 * @param years montant annuel de pension par année de l'horizon de trésorerie
 */
public record TreasuryPensionProjection(List<AnnualPension> years) {
    public TreasuryPensionProjection {
        years = years != null ? List.copyOf(years) : List.of();
    }

    /**
     * @param year   année civile
     * @param amount pension annuelle, toutes personnes du foyer confondues ({@code null} vaut 0)
     */
    public record AnnualPension(int year, BigDecimal amount) {
        public AnnualPension {
            amount = amount != null ? amount : BigDecimal.ZERO;
        }
    }
}
