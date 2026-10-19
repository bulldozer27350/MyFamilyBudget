package com.moe.myfamilybudget.domain.retirement.model;

/**
 * Publie apres chaque mutation du silo Retraite (R-40, lot B).
 */
public record RetirementMutatedEvent(String mutationKind) {
}
