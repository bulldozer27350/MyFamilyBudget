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

/** SET-020 : assemble un {@link SettingsCommandRouter} adossé aux adapters d'un {@link PersistenceManager} de test. */
public final class SettingsCommandRouterTestFactory {

    private SettingsCommandRouterTestFactory() {
    }

    public static SettingsCommandRouter of(PersistenceManager persistenceManager,
                                           ObjectifsSettingsService objectifsSettingsService) {
        return of(persistenceManager, objectifsSettingsService, new InMemoryTaxStore());
    }

    /**
     * SET-020 : variante acceptant le {@link InMemoryTaxStore} du silo Fiscalite en paramètre, pour que
     * l'appelant puisse relire les écritures routées vers ce silo (ex. {@code childExitAge}) depuis la même
     * instance que celle utilisée en écriture, au lieu d'un silo déconnecté créé en interne.
     */
    public static SettingsCommandRouter of(PersistenceManager persistenceManager,
                                           ObjectifsSettingsService objectifsSettingsService,
                                           InMemoryTaxStore taxStore) {
        CacheBackedAppSettings appSettings = CacheBackedAppSettings.of(persistenceManager);
        return new SettingsCommandRouter(
                objectifsSettingsService,
                new TaxCommandService(taxStore, silos -> { }, RecordingTransactionRunner.direct()),
                new RetirementCommandService(new RetirementPersistenceAdapter(persistenceManager)),
                new TresorerieCommandService(new InMemoryTreasuryStore(), silos -> { }, RecordingTransactionRunner.direct()),
                new SimulationSettingsCommandService(appSettings, silos -> { }, RecordingTransactionRunner.direct()),
                new EconomicAssumptionsCommandService(appSettings, silos -> { }, RecordingTransactionRunner.direct()));
    }
}
