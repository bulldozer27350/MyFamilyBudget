package com.moe.myfamilybudget.transition.port;

/**
 * Port de lecture pour le domaine Budget / Tresorerie (RF-B00).
 *
 * @deprecated Utiliser {@link com.moe.myfamilybudget.domain.treasury.port.BudgetReader} (SILO-216, lot B).
 */
@Deprecated(since = "SILO-216")
public interface BudgetReader extends com.moe.myfamilybudget.domain.treasury.port.BudgetReader {
}