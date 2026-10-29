package com.moe.myfamilybudget.application.usecase.notifications;

/**
 * Commande d'enregistrement d'un abonnement push (R-87, SILO-318).
 *
 * @param endpoint URL de souscription push
 * @param p256dh   clé publique P-256 de chiffrement du client
 * @param auth     secret d'authentification du client
 */
public record PushSubscriptionCommand(
        String endpoint,
        String p256dh,
        String auth) {
}
