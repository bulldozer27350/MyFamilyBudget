package com.moe.myfamilybudget.domain.credit.model;

/**
 * Evenement publie par l'adaptateur du silo Credit apres chaque ecriture des prets (SILO-214, lot B).
 *
 * <p>Depuis que les prets s'ecrivent directement dans leur table, ils ne passent plus par le
 * {@code PersistenceManager} et son {@code BudgetMutatedEvent} : cet evenement conserve la regle « toute
 * modification declenche un controle des notifications ». Il est ecoute apres le commit de la transaction en
 * cours, dans le composition root.
 *
 * @param mutationKind nature de l'ecriture (diagnostic uniquement : {@code saveLoanRow}, {@code deleteLoanRow},
 *                     {@code replace}, {@code reset})
 */
public record LoansMutatedEvent(String mutationKind) {
}
