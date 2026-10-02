package com.moe.myfamilybudget.domain.retirement.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pensions de retraite déjà calculées par {@code RetirementCalculationService} (RF-101),
 * annualisées et sommées pour toutes les personnes du foyer, projection minimale reçue par
 * Trésorerie (RF-400, voir doc/architecture/06-domaine-tresorerie.md).
 *
 * @param years montant annuel de pension par année de l'horizon de trésorerie
 */
public record RetirementIncomeProjection(List<AnnualPension> years) {
    public RetirementIncomeProjection {
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
