package com.moe.myfamilybudget.server.internal.notification;

import java.util.List;

/**
 * Une règle de notification : ce qui doit être contrôlé (dans {@link #check}) et ce qui doit être
 * transmis en cas de contrôle positif (le ou les {@link NotificationMessage} retournés).
 *
 * Chaque implémentation est un {@code @org.springframework.stereotype.Component}, découverte
 * automatiquement par Spring et injectée par {@link NotificationDispatchService} sous la forme
 * d'une {@code List<NotificationRule>} (aucune liste à maintenir à la main).
 *
 * {@link #key()} sert de clé stable à deux usages : l'activation individuelle de la règle
 * (bascule on/off dans les paramètres de notification, voir {@link NotificationSettingsParameters})
 * et, combinée à {@link NotificationMessage#entityId()}, la déduplication (24h) gérée par
 * {@link NotificationDispatchService}.
 */
public interface NotificationRule {

    /** Clé stable de la règle (ex. "debit-threshold"), utilisée pour l'activation et la dédup. */
    String key();

    /**
     * Évalue l'état courant du budget. Retourne un message par occurrence positive (ex. un
     * message par objectif nouvellement atteignable), une liste vide si rien n'est à signaler.
     *
     * Ne doit jamais lever d'exception pour un état de données incomplet ou inattendu :
     * {@link NotificationDispatchService} journalise et ignore une règle en erreur plutôt que de
     * bloquer le contrôle des autres règles, mais une implémentation robuste évite d'en dépendre.
     */
    List<NotificationMessage> check(NotificationContext context);
}
