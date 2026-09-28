package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.PatrimoineReader;

/**
 * Adaptateur de persistance pour {@link PatrimoineReader} (RF-B00).
 */
@Component
public class PatrimoinePersistenceAdapter implements PatrimoineReader {

    private final PersistenceManager persistenceManager;

    public PatrimoinePersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public List<PlacementModel> getPlacements() {
        return persistenceManager.getBudgetData().getEffectivePlacements();
    }

    @Override
    public List<RealEstateModel> getRealEstate() {
        return persistenceManager.getBudgetData().getEffectiveRealEstate();
    }

    @Override
    public List<AssetCategoryModel> getAssetCategories() {
        return persistenceManager.getBudgetData().getEffectiveAssetCategories();
    }

    @Override
    public List<TransferModel> getTransfers() {
        return persistenceManager.getBudgetData().getEffectiveTransfers();
    }
}