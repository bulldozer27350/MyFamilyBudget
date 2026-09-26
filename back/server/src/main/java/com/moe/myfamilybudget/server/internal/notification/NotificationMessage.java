package com.moe.myfamilybudget.server.internal.notification;

/**
 * Contenu à transmettre pour une occurrence positive d'une {@link NotificationRule}.
 *
 * @param ruleKey  clé de la règle à l'origine du message ({@link NotificationRule#key()})
 * @param entityId identifiant de la sous-entité concernée si la règle en gère plusieurs
 *                 simultanément (ex. l'id de l'objectif pour "objectif-reachable", l'id de la
 *                 transaction pour "debit-threshold") ; {@code null} pour une règle globale sans
 *                 sous-entité (ex. "balance-floor", un seul solde total)
 * @param title    titre court affiché dans la notification
 * @param body     corps du message
 */
public record NotificationMessage(String ruleKey, String entityId, String title, String body) {

    /** Clé de déduplication (24h) utilisée par {@link NotificationDispatchService}. */
    public String dedupKey() {
        return (entityId == null || entityId.isBlank()) ? ruleKey : ruleKey + ":" + entityId;
    }
}
