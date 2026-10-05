package com.moe.myfamilybudget.domain.goals.model;

/**
 * Evenement publie par l'adaptateur du silo Objectifs apres chaque ecriture des objectifs (SILO-212, lot B1).
 *
 * <p>Depuis que les objectifs s'ecrivent directement dans leurs tables, ils ne passent plus par le
 * {@code PersistenceManager} et son {@code BudgetMutatedEvent} : cet evenement conserve la regle « toute
 * modification declenche un controle des notifications ». Il est ecoute apres le commit de la transaction en
 * cours, dans le composition root.
 *
 * @param mutationKind nature de l'ecriture (diagnostic uniquement : {@code saveGoalRow}, {@code deleteGoalRow},
 *                     {@code replace}, {@code reset})
 */
public record GoalsMutatedEvent(String mutationKind) {
}
