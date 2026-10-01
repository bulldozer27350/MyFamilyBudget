package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.port.PatrimoineWriter;

/**
 * Adaptateur de persistance pour {@link PatrimoineReader} (RF-B00) et {@link PatrimoineWriter} (DB-030).
 */
@Component
public class PatrimoinePersistenceAdapter implements PatrimoineReader, PatrimoineWriter {

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

    @Override
    public Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> body) {
        return persistenceManager.savePatrimoineRow(listKey, body);
    }

    @Override
    public void deletePatrimoineRow(String listKey, String id) {
        persistenceManager.deletePatrimoineRow(listKey, id);
    }

    @Override
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        return persistenceManager.addPlacementHistoryEntry(placementId, body);
    }

    @Override
    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId,
                                                           Map<String, Object> body) {
        return persistenceManager.updatePlacementHistoryEntry(placementId, entryId, body);
    }

    @Override
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        persistenceManager.deletePlacementHistoryEntry(placementId, entryId);
    }

    @Override
    public void addAssetCategory(AssetCategoryModel category) {
        persistenceManager.addAssetCategory(category);
    }

    @Override
    public void updateAssetCategory(String id, String field, Object value) {
        persistenceManager.updateAssetCategory(id, field, value);
    }

    @Override
    public void removeAssetCategory(String id) {
        persistenceManager.removeAssetCategory(id);
    }
}