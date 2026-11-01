package com.moe.myfamilybudget.domain.notifications.core;

import com.moe.myfamilybudget.domain.notifications.calculation.NotificationSettingsService;
import com.moe.myfamilybudget.domain.notifications.port.NotificationSettingsStore;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * Paramètres de notification modifiables depuis l'onglet "Notifications" des paramètres généraux.
 * Sans enregistrement, {@link NotificationSettingsParameters#defaults()} s'applique.
 */
public class DefaultNotificationSettingsService implements NotificationSettingsService {

    private final NotificationSettingsStore store;

    public DefaultNotificationSettingsService(NotificationSettingsStore store) {
        this.store = store;
    }

    @Override
    public NotificationSettingsParameters current() {
        return store.load().orElseGet(NotificationSettingsParameters::defaults);
    }

    @Override
    public NotificationSettingsParameters defaults() {
        return NotificationSettingsParameters.defaults();
    }

    @Override
    public NotificationSettingsParameters save(NotificationSettingsParameters parameters) {
        parameters.validate();
        store.save(parameters);
        return parameters;
    }
}
