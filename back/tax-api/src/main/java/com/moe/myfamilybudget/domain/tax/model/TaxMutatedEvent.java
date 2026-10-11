package com.moe.myfamilybudget.domain.tax.model;

/**
 * Publie apres chaque mutation du silo Fiscalite (R-42, lot B).
 */
public record TaxMutatedEvent(String mutationKind) {
}
