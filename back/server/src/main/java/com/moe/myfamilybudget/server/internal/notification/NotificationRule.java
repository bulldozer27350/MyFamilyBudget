package com.moe.myfamilybudget.server.internal.notification;

import java.util.List;

/**
 * Une règle de notification : ce qui doit être contrôlé (dans {@link #check}) et ce qui doit être
 * transmis en cas de contrôle positif (le ou les {@link NotificationMessage} retournés).
 *
 * Depuis RF-702, chaque règle est typée par sa propre entrée minimale ({@code DebitThresholdInput},
 * {@code BalanceFloorInput}, {@code ObjectifReachableInput}) : il n'existe volontairement aucune
 * entrée d'évaluation commune, et une règle ne connaît plus {@code BudgetDataModel}. L'assemblage
 * des entrées est fait en amont, par {@link NotificationDispatchService} (via
 * {@code NotificationInputFactory}).
 *
 * Chaque implémentation est un {@code @org.springframework.stereotype.Component} injecté par type
 * dans {@link NotificationDispatchService}.
 *
 * {@link #key()} sert de clé stable à deux usages : l'activation individuelle de la règle
 * (bascule on/off dans les paramètres de notification, voir {@link NotificationSettingsParameters})
 * et, combinée à {@link NotificationMessage#entityId()}, la déduplication (24h) gérée par
 * {@link NotificationDispatchService}.
 *
 * @param <I> type d'entrée propre à la règle
 */
public interface NotificationRule<I> {

    /** Clé stable de la règle (ex. "debit-threshold"), utilisée pour l'activation et la dédup. */
    String key();

    /**
     * Évalue l'entrée fournie. Retourne un message par occurrence positive (ex. un message par
     * objectif nouvellement atteignable), une liste vide si rien n'est à signaler.
     *
     * Contrôle sur l'état courant uniquement (pas de comparaison avec un état précédent) : une règle
     * comme "objectif-reachable" redevient simplement positive tant que l'objectif reste couvert, la
     * déduplication (24h) évitant le spam.
     *
     * Ne doit jamais lever d'exception pour un état de données incomplet ou inattendu :
     * {@link NotificationDispatchService} journalise et ignore une règle en erreur plutôt que de
     * bloquer le contrôle des autres règles, mais une implémentation robuste évite d'en dépendre.
     */
    List<NotificationMessage> check(I input);
}
