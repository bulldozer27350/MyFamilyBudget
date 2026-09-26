package com.moe.myfamilybudget.server.internal.notification;

/**
 * Un canal de transmission d'une notification déjà décidée par une {@link NotificationRule}.
 *
 * Comme {@link NotificationRule}, chaque implémentation est un
 * {@code @org.springframework.stereotype.Component} injecté automatiquement par Spring sous la
 * forme d'une {@code List<NotificationChannel>} dans {@link NotificationDispatchService} : ajouter
 * un canal (email, SMS...) n'implique aucune modification du dispatcher, seulement une nouvelle
 * classe implémentant cette interface.
 *
 * Seul {@code WebPushNotificationChannel} (push mobile via Web Push / PWA) est fourni pour
 * l'instant.
 */
public interface NotificationChannel {

    /** Clé stable du canal (ex. "push-mobile"), à des fins de journalisation. */
    String key();

    /**
     * Transmet le message. Une erreur d'envoi (réseau, abonnement expiré...) doit être gérée en
     * interne (journalisée) plutôt que propagée : un canal en échec ne doit pas empêcher les
     * autres canaux d'envoyer le même message.
     */
    void send(NotificationMessage message);
}
