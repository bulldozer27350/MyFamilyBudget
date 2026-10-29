package com.moe.myfamilybudget.application.usecase.notifications;

/**
 * Cas d'usage « Notifications » exposé par l'application (R-87, SILO-318).
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type d'un
 * silo ({@code domain.notifications}). Le module {@code web} (aujourd'hui
 * {@code NotificationsServiceImpl}) convertit les résultats vers le monde HTTP.
 */
public interface NotificationsUseCase {

    /**
     * Retourne les paramètres actuels et les valeurs par défaut des alertes de notification.
     */
    NotificationParametersModel getNotificationsParametres();

    /**
     * Valide et enregistre de nouveaux paramètres de notification.
     *
     * @param command nouvelles valeurs à enregistrer
     * @return les paramètres tels qu'enregistrés avec les valeurs par défaut
     * @throws IllegalArgumentException si un paramètre est invalide (traduit en 400)
     */
    NotificationParametersModel updateNotificationsParametres(NotificationParametersSaveCommand command);

    /**
     * Déclenche une vérification manuelle immédiate des règles d'alerte actives.
     *
     * @return résultat contenant le nombre d'alertes envoyées
     */
    NotificationVerificationResultModel verifyNotifications();

    /**
     * Retourne la clé publique VAPID pour les abonnements push du navigateur/mobile.
     */
    PushPublicKeyModel getPushPublicKey();

    /**
     * Enregistre un abonnement push pour recevoir les alertes.
     *
     * @param command informations d'abonnement (endpoint, p256dh, auth)
     * @throws IllegalArgumentException si les informations sont incomplètes
     */
    void registerPushSubscription(PushSubscriptionCommand command);

    /**
     * Supprime un abonnement push à partir de son endpoint.
     *
     * @param endpoint URL de l'abonnement à supprimer
     */
    void unregisterPushSubscription(String endpoint);
}
