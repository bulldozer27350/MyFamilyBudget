package com.moe.myfamilybudget.domain.wealth.model;

/**
 * Publie apres chaque mutation du silo Patrimoine (SILO-215, lot B).
 */
public record WealthMutatedEvent(String mutationKind) {
}
