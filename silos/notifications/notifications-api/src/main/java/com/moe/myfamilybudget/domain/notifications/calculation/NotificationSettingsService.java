package com.moe.myfamilybudget.domain.notifications.calculation;

import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * Paramètres de notification modifiables depuis l'onglet « Notifications » des paramètres généraux. Sans
 * enregistrement, {@link NotificationSettingsParameters#defaults()} s'applique. L'implémentation vit dans
 * {@code notifications-core} ({@code DefaultNotificationSettingsService}).
 */
public interface NotificationSettingsService {

    /** Paramètres en vigueur : enregistrés s'ils existent, sinon les valeurs par défaut. */
    NotificationSettingsParameters current();

    /** Valeurs par défaut. */
    NotificationSettingsParameters defaults();

    /**
     * Valide puis enregistre les paramètres.
     *
     * @throws IllegalArgumentException si un seuil est invalide (traduit en 400)
     */
    NotificationSettingsParameters save(NotificationSettingsParameters parameters);
}
