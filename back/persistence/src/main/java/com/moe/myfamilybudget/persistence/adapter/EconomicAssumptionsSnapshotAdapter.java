package com.moe.myfamilybudget.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.transition.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.transition.port.EconomicAssumptionsSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link EconomicAssumptionsSnapshotWriter} (SILO-119, lot B1). Classe distincte de
 * {@link SettingsPersistenceAdapter} : les ports de simulation et d'hypothèses économiques portent chacun une
 * méthode {@code reset()} qui ne doit pas être fusionnée. Rejoint la transaction ouverte par l'appelant ; le stockage
 * physique reste {@code SettingsEntity} jusqu'à SILO-220.
 */
@Component
public class EconomicAssumptionsSnapshotAdapter implements EconomicAssumptionsSnapshotWriter {

    private final PersistenceManager persistenceManager;

    public EconomicAssumptionsSnapshotAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void replace(EconomicAssumptionsModel assumptions) {
        persistenceManager.write(m -> m.replaceEconomicAssumptionsSnapshot(assumptions));
    }

    @Override
    public void reset() {
        persistenceManager.write(m -> m.resetEconomicAssumptionsSnapshot());
    }
}
