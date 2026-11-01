package com.moe.myfamilybudget.domain.treasury.model;

/**
 * Publie apres chaque mutation du silo Tresorerie (SILO-216, lot B).
 */
public record TreasuryMutatedEvent(String mutationKind) {
}
