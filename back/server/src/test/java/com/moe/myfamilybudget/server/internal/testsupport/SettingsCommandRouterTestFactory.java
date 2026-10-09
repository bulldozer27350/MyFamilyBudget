package com.moe.myfamilybudget.server.internal.testsupport;

import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.command.EconomicAssumptionsCommandService;
import com.moe.myfamilybudget.application.command.RetirementCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.command.SimulationSettingsCommandService;
import com.moe.myfamilybudget.application.command.TaxCommandService;
import com.moe.myfamilybudget.application.command.TresorerieCommandService;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.TaxPersistenceAdapter;

/** SET-020 : assemble un {@link SettingsCommandRouter} adossé aux adapters d'un {@link PersistenceManager} de test. */
public final class SettingsCommandRouterTestFactory {

    private SettingsCommandRouterTestFactory() {
    }

    public static SettingsCommandRouter of(PersistenceManager persistenceManager,
                                           ObjectifsSettingsService objectifsSettingsService) {
        CacheBackedAppSettings appSettings = CacheBackedAppSettings.of(persistenceManager);
        return new SettingsCommandRouter(
                objectifsSettingsService,
                new TaxCommandService(new TaxPersistenceAdapter(persistenceManager)),
                new RetirementCommandService(new RetirementPersistenceAdapter(persistenceManager)),
                new TresorerieCommandService(new InMemoryTreasuryStore()),
                new SimulationSettingsCommandService(appSettings, silos -> { }, RecordingTransactionRunner.direct()),
                new EconomicAssumptionsCommandService(appSettings, silos -> { }, RecordingTransactionRunner.direct()));
    }
}
