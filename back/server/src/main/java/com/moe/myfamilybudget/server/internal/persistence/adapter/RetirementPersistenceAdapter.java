package com.moe.myfamilybudget.server.internal.persistence.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.converter.PensionEntityMapper;
import com.moe.myfamilybudget.server.internal.persistence.repository.PensionPlanRepository;
import com.moe.myfamilybudget.server.internal.port.RetirementReader;
import com.moe.myfamilybudget.server.internal.port.RetirementSettingField;
import com.moe.myfamilybudget.server.internal.port.RetirementWriter;

/**
 * Adaptateur de persistance pour {@link RetirementReader} (RF-B00) et {@link RetirementWriter} (DB-020).
 *
 * <p>DB-1001 : en production, la lecture passe par {@link PensionPlanRepository} (tables autonomes
 * {@code pension_plan}, {@code pension_person}, {@code pension_salary}, DB-1000). Les ecritures passent toujours
 * par le {@code PersistenceManager} : la passerelle de persistance recopie la retraite du modele dans les tables
 * autonomes dans la meme transaction. Une retraite absente du modele est relue {@code null}, comme depuis le
 * cache.
 *
 * <p>Le constructeur sans repository conserve l'ancienne lecture depuis le cache memoire ; il sert aux tests
 * unitaires adosses a des repositories mockes et constitue le chemin de retour arriere.
 */
@Component
public class RetirementPersistenceAdapter implements RetirementReader, RetirementWriter {

    private final PersistenceManager persistenceManager;
    private final PensionPlanRepository pensionPlanRepository;

    public RetirementPersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null);
    }

    @Autowired
    public RetirementPersistenceAdapter(PersistenceManager persistenceManager,
                                        PensionPlanRepository pensionPlanRepository) {
        this.persistenceManager = persistenceManager;
        this.pensionPlanRepository = pensionPlanRepository;
    }

    @Override
    public RetirementModel getRetirement() {
        if (pensionPlanRepository == null) {
            return persistenceManager.getBudgetData().retirement();
        }
        return pensionPlanRepository.findFirstByOrderByIdAsc()
                .map(PensionEntityMapper::toModel)
                .orElse(null);
    }

    @Override
    public void updateRetirement(RetirementModel retirement) {
        persistenceManager.write(m -> m.updateRetirement(retirement));
    }

    /**
     * SET-020 : le stockage physique des paramètres reste partagé ({@code SettingsEntity}) ; la mutation de
     * transition est la même que pour les autres familles. Sa séparation relève des patchs DB-xxx, la double
     * écriture {@code pass2026} / {@code passGrowthRate} de SET-040.
     */
    @Override
    public void updateRetirementSetting(RetirementSettingField field, Object value) {
        persistenceManager.write(m -> m.updateTaxSettings(field.key(), value));
    }
}
