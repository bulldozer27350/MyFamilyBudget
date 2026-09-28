package com.moe.myfamilybudget.server.internal.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;

/**
 * Adaptateur de persistance pour {@link SettingsReader} (RF-B00).
 */
@Component
public class SettingsPersistenceAdapter implements SettingsReader {

    private final PersistenceManager persistenceManager;

    public SettingsPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public SettingsModel getSettings() {
        return persistenceManager.getBudgetData().getEffectiveSettings();
    }
}