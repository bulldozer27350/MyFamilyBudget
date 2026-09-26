package com.moe.myfamilybudget.server.internal.notification;

import org.springframework.stereotype.Service;

/**
 * Paramètres de notification modifiables depuis l'onglet "Notifications" des paramètres généraux.
 * Sans enregistrement, {@link NotificationSettingsParameters#defaults()} s'applique.
 */
@Service
public class NotificationSettingsService {

    private final NotificationSettingsStore store;

    public NotificationSettingsService(NotificationSettingsStore store) {
        this.store = store;
    }

    /** Paramètres en vigueur : enregistrés s'ils existent, sinon les valeurs par défaut. */
    public NotificationSettingsParameters current() {
        return store.load().orElseGet(NotificationSettingsParameters::defaults);
    }

    /** Valeurs par défaut. */
    public NotificationSettingsParameters defaults() {
        return NotificationSettingsParameters.defaults();
    }

    /**
     * Valide puis enregistre les paramètres.
     *
     * @throws IllegalArgumentException si un seuil est invalide (traduit en 400)
     */
    public NotificationSettingsParameters save(NotificationSettingsParameters parameters) {
        parameters.validate();
        store.save(parameters);
        return parameters;
    }
}
