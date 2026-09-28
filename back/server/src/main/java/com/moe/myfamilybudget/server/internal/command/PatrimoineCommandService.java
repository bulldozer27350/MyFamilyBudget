package com.moe.myfamilybudget.server.internal.command;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Service de commande du domaine Patrimoine (RF-A00).
 * Encapsule les operations d'ecriture sur le patrimoine (immobilier, placements,
 * categories d'actifs et historique de valorisation).
 */
@Service
public class PatrimoineCommandService {

    private final PersistenceManager persistenceManager;

    public PatrimoineCommandService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> body) {
        return persistenceManager.savePatrimoineRow(listKey, body);
    }

    public void deletePatrimoineRow(String listKey, String id) {
        persistenceManager.deletePatrimoineRow(listKey, id);
    }

    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        return persistenceManager.addPlacementHistoryEntry(placementId, body);
    }

    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> body) {
        return persistenceManager.updatePlacementHistoryEntry(placementId, entryId, body);
    }

    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        persistenceManager.deletePlacementHistoryEntry(placementId, entryId);
    }

    public void addAssetCategory(AssetCategoryModel category) {
        persistenceManager.addAssetCategory(category);
    }

    public void updateAssetCategory(String id, String field, Object value) {
        persistenceManager.updateAssetCategory(id, field, value);
    }

    public void removeAssetCategory(String id) {
        persistenceManager.removeAssetCategory(id);
    }
}