package com.moe.myfamilybudget.domain.wealth.calculation;

import java.util.List;

/**
 * Projection de flux de trésorerie fournie au domaine Patrimoine (RF-300, voir
 * doc/architecture/05-domaine-patrimoine.md), sous la forme d'un flux net annuel hors
 * mouvements de placements. Elle remplace la reconstitution interne, aujourd'hui faite depuis
 * les revenus, charges et dépenses ponctuelles du budget, qui sert à décider la pause
 * automatique des versements.
 *
 * @param years un flux net par année de l'horizon de projection, par ordre chronologique
 */
public record CashflowProjection(List<AnnualCashflow> years) {
    public CashflowProjection {
        years = years != null ? List.copyOf(years) : List.of();
    }
}
