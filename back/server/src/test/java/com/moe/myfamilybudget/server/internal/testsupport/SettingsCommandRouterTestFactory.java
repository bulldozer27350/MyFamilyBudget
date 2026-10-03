package com.moe.myfamilybudget.server.internal.testsupport;

import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.command.EconomicAssumptionsCommandService;
import com.moe.myfamilybudget.application.command.RetirementCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.command.SimulationSettingsCommandService;
import com.moe.myfamilybudget.application.command.TaxCommandService;
import com.moe.myfamilybudget.application.command.TresorerieCommandService;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.SettingsPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.TaxPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.TresoreriePersistenceAdapter;

/** SET-020 : assemble un {@link SettingsCommandRouter} adossé aux adapters d'un {@link PersistenceManager} de test. */
public final class SettingsCommandRouterTestFactory {

    private SettingsCommandRouterTestFactory() {
    }

    public static SettingsCommandRouter of(PersistenceManager persistenceManager,
                                           ObjectifsSettingsService objectifsSettingsService) {
        SettingsPersistenceAdapter settingsAdapter = new SettingsPersistenceAdapter(persistenceManager);
        return new SettingsCommandRouter(
                objectifsSettingsService,
                new TaxCommandService(new TaxPersistenceAdapter(persistenceManager)),
                new RetirementCommandService(new RetirementPersistenceAdapter(persistenceManager)),
                new TresorerieCommandService(new TresoreriePersistenceAdapter(persistenceManager)),
                new SimulationSettingsCommandService(settingsAdapter),
                new EconomicAssumptionsCommandService(settingsAdapter));
    }
}
