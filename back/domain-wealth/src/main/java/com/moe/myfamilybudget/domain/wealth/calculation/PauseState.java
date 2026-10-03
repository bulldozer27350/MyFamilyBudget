package com.moe.myfamilybudget.domain.wealth.calculation;

/**
 * État courant de la pause automatique des versements (voir {@link ContributionPauseRules}).
 *
 * @param level              niveau de pause en vigueur : un placement dont la priorité de pause est
 *                           inférieure ou égale à ce niveau ne reçoit plus de versement
 * @param levelFromCashAlert part du niveau qui provient de la trésorerie (seuil d'alerte / plafond),
 *                           conservée d'une période à l'autre car elle monte et descend par paliers
 */
public record PauseState(int level, int levelFromCashAlert) {
}
