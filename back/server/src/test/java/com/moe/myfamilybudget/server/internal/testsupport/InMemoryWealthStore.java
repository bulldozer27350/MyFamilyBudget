package com.moe.myfamilybudget.server.internal.testsupport;

import java.util.List;
import java.util.Map;

import com.moe.myfamilybudget.domain.wealth.core.persistence.JpaWealthStore;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.wealth.port.AssetCategoryField;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineSnapshotWriter;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineWriter;

/**
 * Silo Patrimoine en memoire pour les tests unitaires (SILO-215, lot B / R-30) : le vrai {@link JpaWealthStore}
 * adosse a des {@link InMemoryWealthRepository}, sans base ni {@code PersistenceManager}. Remplace l'ancien
 * {@code PatrimoinePersistenceAdapter} branche sur le cache global.
 *
 * <p>DA-14 : comme {@link JpaWealthStore}, ne gere pas les virements (relevent du silo Tresorerie) — voir
 * {@code PatrimoinePersistenceAdapter} pour le routage reel.
 */
public final class InMemoryWealthStore implements PatrimoineReader, PatrimoineWriter, PatrimoineSnapshotWriter {

    private final JpaWealthStore delegate = new JpaWealthStore(
            InMemoryWealthRepository.createPlacementRepository(),
            InMemoryWealthRepository.createRealEstateRepository(),
            InMemoryWealthRepository.createCategoryRepository(),
            event -> { });

    public JpaWealthStore getDelegate() {
        return delegate;
    }

    @Override
    public List<PlacementModel> getPlacements() {
        return delegate.getPlacements();
    }

    @Override
    public List<RealEstateModel> getRealEstate() {
        return delegate.getRealEstate();
    }

    @Override
    public List<AssetCategoryModel> getAssetCategories() {
        return delegate.getAssetCategories();
    }

    @Override
    public List<PatrimoineTransferModel> getTransfers() {
        return delegate.getTransfers();
    }

    @Override
    public Map<String, Object> savePatrimoineRow(PatrimoineList list, Map<String, Object> body) {
        return delegate.savePatrimoineRow(list, body);
    }

    @Override
    public void deletePatrimoineRow(PatrimoineList list, String id) {
        delegate.deletePatrimoineRow(list, id);
    }

    @Override
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        return delegate.addPlacementHistoryEntry(placementId, body);
    }

    @Override
    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId,
                                                           Map<String, Object> body) {
        return delegate.updatePlacementHistoryEntry(placementId, entryId, body);
    }

    @Override
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        delegate.deletePlacementHistoryEntry(placementId, entryId);
    }

    @Override
    public void addAssetCategory(AssetCategoryModel category) {
        delegate.addAssetCategory(category);
    }

    @Override
    public void updateAssetCategory(String id, AssetCategoryField field, Object value) {
        delegate.updateAssetCategory(id, field, value);
    }

    @Override
    public void removeAssetCategory(String id) {
        delegate.removeAssetCategory(id);
    }

    @Override
    public void replace(List<PlacementModel> placements, List<RealEstateModel> realEstate,
                        List<PatrimoineTransferModel> transfers, List<AssetCategoryModel> assetCategories) {
        delegate.replace(placements, realEstate, transfers, assetCategories);
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}
