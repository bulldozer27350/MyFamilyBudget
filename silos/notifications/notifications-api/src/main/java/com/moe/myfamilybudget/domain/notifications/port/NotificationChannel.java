package com.moe.myfamilybudget.domain.notifications.port;

import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;
import com.moe.myfamilybudget.domain.notifications.model.NotificationRule;

/**
 * Un canal de transmission d'une notification déjà décidée par une {@link NotificationRule}.
 *
 * Les canaux sont fournis au dispatcher sous la forme d'une {@code List<NotificationChannel>} par le
 * composition root ({@code DomainEngineConfig}) : ajouter un canal (email, SMS...) n'implique aucune
 * modification du dispatcher, seulement une nouvelle classe implémentant cette interface et son bean.
 *
 * Seul {@code WebPushNotificationChannel} (push mobile via Web Push / PWA, dans {@code notifications-core})
 * est fourni pour l'instant.
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
