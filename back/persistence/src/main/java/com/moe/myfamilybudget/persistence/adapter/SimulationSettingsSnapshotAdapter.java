package com.moe.myfamilybudget.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link SimulationSettingsSnapshotWriter} (SILO-119, lot B1). Classe distincte de
 * {@link SettingsPersistenceAdapter} : les ports de simulation et d'hypothèses économiques portent chacun une
 * méthode {@code reset()} qui ne doit pas être fusionnée. Rejoint la transaction ouverte par l'appelant ; le stockage
 * physique reste {@code SettingsEntity} jusqu'à SILO-220.
 */
@Component
public class SimulationSettingsSnapshotAdapter implements SimulationSettingsSnapshotWriter {

    private final PersistenceManager persistenceManager;

    public SimulationSettingsSnapshotAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void replace(SimulationSettingsModel settings) {
        persistenceManager.write(m -> m.replaceSimulationSettingsSnapshot(settings));
    }

    @Override
    public void reset() {
        persistenceManager.write(m -> m.resetSimulationSettingsSnapshot());
    }
}
