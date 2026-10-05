package com.moe.myfamilybudget.domain.tax.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Impôt déjà calculé par le domaine Fiscalité, projection minimale reçue par Trésorerie (RF-400,
 * voir doc/architecture/06-domaine-tresorerie.md) : ni parts, ni revenu imposable, ni taux — ces
 * détails n'intéressent que le calcul fiscal lui-même ({@code TaxCalculator}, {@code TaxYearlyModel}).
 *
 * @param years montant prélevé à la source et impôt réel par année de l'horizon de trésorerie
 */
public record TaxProjection(List<Withholding> years) {
    public TaxProjection {
        years = years != null ? List.copyOf(years) : List.of();
    }

    /**
     * @param year     année civile
     * @param withheld impôt prélevé à la source cette année-là ({@code null} vaut 0)
     * @param actual   impôt réel de cette année-là, utilisé pour calculer la régularisation de
     *                 l'année suivante ({@code null} vaut 0)
     */
    public record Withholding(int year, BigDecimal withheld, BigDecimal actual) {
        public Withholding {
            withheld = withheld != null ? withheld : BigDecimal.ZERO;
            actual = actual != null ? actual : BigDecimal.ZERO;
        }
    }
}
