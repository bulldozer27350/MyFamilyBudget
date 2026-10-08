package com.moe.myfamilybudget.server.internal.testsupport;

import com.moe.myfamilybudget.application.settings.SettingsModelAssembler;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.adapter.SettingsPersistenceAdapter;

/**
 * SILO-100 (lot B) : {@code SettingsReader} de test, composé comme en production par
 * {@link SettingsModelAssembler} à partir des lectures par propriétaire de {@link SettingsPersistenceAdapter} (R-50 :
 * simulation et hypothèses économiques viennent de {@link CacheBackedAppSettings}).
 */
public final class SettingsReaderTestFactory {

    private SettingsReaderTestFactory() {
    }

    public static SettingsModelAssembler of(PersistenceManager persistenceManager) {
        SettingsPersistenceAdapter adapter = new SettingsPersistenceAdapter(persistenceManager);
        CacheBackedAppSettings appSettings = CacheBackedAppSettings.of(persistenceManager);
        return new SettingsModelAssembler(adapter, adapter, adapter, appSettings, appSettings);
    }
}
