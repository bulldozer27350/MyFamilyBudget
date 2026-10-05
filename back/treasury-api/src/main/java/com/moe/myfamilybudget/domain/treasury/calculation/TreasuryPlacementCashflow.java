package com.moe.myfamilybudget.domain.treasury.calculation;

import java.math.BigDecimal;

/**
 * Versements vers les placements pour une année, déjà agrégés tous placements confondus : contrat
 * d'entrée propre à Trésorerie (SILO-132, décision D1 de doc/architecture/21-plan-silotage.md).
 *
 * <p>Ce montant est une somme simple des versements mensuels configurés sur la fenêtre de chaque
 * placement, <b>sans tenir compte du mécanisme de pause</b> du domaine Patrimoine : Trésorerie ne
 * consomme pas la sortie simulée de Patrimoine (pas de cycle Patrimoine ↔ Trésorerie, voir
 * 00-principes.md). Trésorerie ne connaît pas le domaine Patrimoine : l'application calcule ce
 * montant à partir des placements.
 *
 * @param year   année civile
 * @param amount versements annuels vers les placements, tous placements confondus ({@code null} vaut 0)
 */
public record TreasuryPlacementCashflow(int year, BigDecimal amount) {
    public TreasuryPlacementCashflow {
        amount = amount != null ? amount : BigDecimal.ZERO;
    }
}
