package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.TaxReader;

/**
 * Adaptateur de persistance pour {@link TaxReader} (RF-B00).
 */
@Component
public class TaxPersistenceAdapter implements TaxReader {

    private final PersistenceManager persistenceManager;

    public TaxPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public List<TaxChildModel> getTaxChildren() {
        return persistenceManager.getBudgetData().getEffectiveTaxChildren();
    }

    @Override
    public List<TaxBracketModel> getTaxBrackets() {
        return persistenceManager.getBudgetData().getEffectiveTaxBrackets();
    }

    @Override
    public List<TaxRateOverrideModel> getTaxRateOverrides() {
        return persistenceManager.getBudgetData().getEffectiveTaxRateOverrides();
    }

    @Override
    public List<TaxActualOverrideModel> getTaxActualOverrides() {
        return persistenceManager.getBudgetData().getEffectiveTaxActualOverrides();
    }
}