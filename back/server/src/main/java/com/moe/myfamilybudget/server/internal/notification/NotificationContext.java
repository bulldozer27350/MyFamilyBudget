package com.moe.myfamilybudget.server.internal.notification;

import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;

/**
 * Contexte fourni à {@link NotificationRule#check}, sans dépendance directe à
 * {@code PersistenceManager} : un instantané du budget et les paramètres de notification en
 * vigueur au moment du contrôle.
 *
 * Contrôle sur l'état courant uniquement (pas de comparaison avec un état précédent) : une règle
 * comme "objectif-reachable" redevient simplement positive tant que l'objectif reste couvert, la
 * déduplication (24h, voir {@link NotificationDispatchService}) évitant le spam.
 */
public record NotificationContext(BudgetDataModel data, NotificationSettingsParameters settings) {
}
