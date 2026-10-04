package com.moe.myfamilybudget.persistence.adapter;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.converter.FiscalEntityMapper;
import com.moe.myfamilybudget.persistence.repository.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalBracketRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalChildRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxWriter;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link TaxReader} (RF-B00) et {@link TaxWriter} (DB-021).
 *
 * <p>DB-1011 : en production, la lecture passe par les repositories autonomes {@code Fiscal*Repository}
 * (tables {@code fiscal_*}, DB-1010). Les ecritures passent toujours par le {@code PersistenceManager} : la
 * passerelle de persistance recopie la fiscalite dans les tables autonomes dans la meme transaction. Le bareme
 * lu est le bareme effectif (bareme par defaut si aucune tranche n'est saisie), recopie tel quel par la
 * passerelle.
 *
 * <p>Le constructeur sans repository conserve l'ancienne lecture depuis le cache memoire ; il sert aux tests
 * unitaires adosses a des repositories mockes et constitue le chemin de retour arriere.
 */
@Component
public class TaxPersistenceAdapter implements TaxReader, TaxWriter, TaxSnapshotWriter {

    private final PersistenceManager persistenceManager;
    private final FiscalChildRepository fiscalChildRepository;
    private final FiscalBracketRepository fiscalBracketRepository;
    private final FiscalRateOverrideRepository fiscalRateOverrideRepository;
    private final FiscalActualOverrideRepository fiscalActualOverrideRepository;

    public TaxPersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null, null, null, null);
    }

    @Autowired
    public TaxPersistenceAdapter(PersistenceManager persistenceManager,
                                 FiscalChildRepository fiscalChildRepository,
                                 FiscalBracketRepository fiscalBracketRepository,
                                 FiscalRateOverrideRepository fiscalRateOverrideRepository,
                                 FiscalActualOverrideRepository fiscalActualOverrideRepository) {
        this.persistenceManager = persistenceManager;
        this.fiscalChildRepository = fiscalChildRepository;
        this.fiscalBracketRepository = fiscalBracketRepository;
        this.fiscalRateOverrideRepository = fiscalRateOverrideRepository;
        this.fiscalActualOverrideRepository = fiscalActualOverrideRepository;
    }

    @Override
    public List<TaxChildModel> getTaxChildren() {
        if (fiscalChildRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveTaxChildren();
        }
        return FiscalEntityMapper.toChildModels(fiscalChildRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<TaxBracketModel> getTaxBrackets() {
        if (fiscalBracketRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveTaxBrackets();
        }
        return FiscalEntityMapper.toBracketModels(fiscalBracketRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<TaxRateOverrideModel> getTaxRateOverrides() {
        if (fiscalRateOverrideRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveTaxRateOverrides();
        }
        return FiscalEntityMapper.toRateOverrideModels(fiscalRateOverrideRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<TaxActualOverrideModel> getTaxActualOverrides() {
        if (fiscalActualOverrideRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveTaxActualOverrides();
        }
        return FiscalEntityMapper.toActualOverrideModels(fiscalActualOverrideRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides,
                                List<TaxActualOverrideModel> actualOverrides) {
        persistenceManager.write(m -> m.updateTaxConfig(children, brackets, rateOverrides, actualOverrides));
    }

    @Override
    public void updateTaxSettings(TaxSettingField field, Object value) {
        persistenceManager.write(m -> m.updateFiscalSetting(field, value));
    }

    @Override
    public void resetDefaultTaxBrackets() {
        persistenceManager.write(m -> m.resetDefaultTaxBrackets());
    }

    /** SILO-119 (lot B1) : import du silo Fiscalité (paramètres et configuration fiscale). */
    @Override
    public void replace(TaxSettingsModel settings, List<TaxChildModel> children, List<TaxBracketModel> brackets,
                        List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        persistenceManager.write(m -> m.replaceTaxSnapshot(settings, children, brackets, rateOverrides,
                actualOverrides));
    }

    /** SILO-119 (lot B1) : remise à zéro du silo Fiscalité. */
    @Override
    public void reset() {
        persistenceManager.write(m -> m.resetTaxSnapshot());
    }
}