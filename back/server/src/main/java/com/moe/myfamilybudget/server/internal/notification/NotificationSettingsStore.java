package com.moe.myfamilybudget.server.internal.notification;

import java.util.Optional;

/**
 * Persistance des paramètres de notification modifiés par l'utilisateur.
 */
public interface NotificationSettingsStore {

    /** Paramètres enregistrés, ou vide s'il n'y en a pas (ou s'ils sont illisibles). */
    Optional<NotificationSettingsParameters> load();

    void save(NotificationSettingsParameters parameters);
}
