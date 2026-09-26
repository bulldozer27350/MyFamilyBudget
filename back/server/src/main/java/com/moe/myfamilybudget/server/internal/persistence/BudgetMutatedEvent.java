package com.moe.myfamilybudget.server.internal.persistence;

/**
 * Événement publié par {@link PersistenceManager} après chaque mutation réussie du budget. Écouté
 * par {@code NotificationDispatchService} pour déclencher automatiquement un contrôle des règles
 * de notification ("toute modification doit déclencher un contrôle des notifications").
 *
 * @param mutationKind nom de la méthode de {@link PersistenceManager} à l'origine de l'événement,
 *                      à titre diagnostique uniquement (journalisation)
 */
public record BudgetMutatedEvent(String mutationKind) {
}
