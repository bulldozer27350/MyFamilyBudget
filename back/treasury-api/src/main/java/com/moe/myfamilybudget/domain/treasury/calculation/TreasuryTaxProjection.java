package com.moe.myfamilybudget.domain.treasury.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Impôt à prendre en compte dans la trésorerie : contrat d'entrée propre à Trésorerie (SILO-132,
 * décision D1 de doc/architecture/21-plan-silotage.md). Ni parts, ni revenu imposable, ni taux :
 * seuls le prélevé à la source et l'impôt réel de chaque année intéressent la trésorerie.
 *
 * <p>Trésorerie ne connaît pas le domaine Fiscalité : l'application traduit les sorties de Fiscalité
 * vers ce type.
 *
 * @param years montant prélevé à la source et impôt réel par année de l'horizon de trésorerie
 */
public record TreasuryTaxProjection(List<Withholding> years) {
    public TreasuryTaxProjection {
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
