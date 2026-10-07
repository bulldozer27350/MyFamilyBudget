package com.moe.myfamilybudget.persistence.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthEntityMapper;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;
import com.moe.myfamilybudget.domain.wealth.port.AssetCategoryField;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineWriter;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link PatrimoineReader} (RF-B00) et {@link PatrimoineWriter} (DB-030).
 *
 * <p>DB-1051 : en production, la lecture des placements, des biens immobiliers et des categories d'actifs
 * passe par les repositories autonomes {@code wealth_*} (DB-1050). Les ecritures passent toujours par le
 * {@code PersistenceManager} : la passerelle de persistance recopie le patrimoine dans les tables autonomes
 * dans la meme transaction.
 *
 * <p>SILO-216, DA-14 : les virements appartiennent au silo Tresorerie ; Patrimoine delegue leur lecture et
 * ecriture au {@link JpaTreasuryStore}.
 */
@Component
public class PatrimoinePersistenceAdapter implements PatrimoineReader, PatrimoineWriter, PatrimoineSnapshotWriter {

    private final PersistenceManager persistenceManager;
    private final WealthPlacementRepository wealthPlacementRepository;
    private final WealthRealEstateRepository wealthRealEstateRepository;
    private final WealthCategoryRepository wealthCategoryRepository;
    private final JpaTreasuryStore jpaTreasuryStore;

    public PatrimoinePersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null, null, null, null);
    }

    public PatrimoinePersistenceAdapter(PersistenceManager persistenceManager,
                                        WealthPlacementRepository wealthPlacementRepository,
                                        WealthRealEstateRepository wealthRealEstateRepository,
                                        WealthCategoryRepository wealthCategoryRepository) {
        this(persistenceManager, wealthPlacementRepository, wealthRealEstateRepository, wealthCategoryRepository, null);
    }

    @Autowired
    public PatrimoinePersistenceAdapter(PersistenceManager persistenceManager,
                                        WealthPlacementRepository wealthPlacementRepository,
                                        WealthRealEstateRepository wealthRealEstateRepository,
                                        WealthCategoryRepository wealthCategoryRepository,
                                        @Autowired(required = false) JpaTreasuryStore jpaTreasuryStore) {
        this.persistenceManager = persistenceManager;
        this.wealthPlacementRepository = wealthPlacementRepository;
        this.wealthRealEstateRepository = wealthRealEstateRepository;
        this.wealthCategoryRepository = wealthCategoryRepository;
        this.jpaTreasuryStore = jpaTreasuryStore;
    }

    @Override
    public List<PlacementModel> getPlacements() {
        if (wealthPlacementRepository == null) {
            return persistenceManager.getBudgetData().getEffectivePlacements();
        }
        return WealthEntityMapper.toPlacementModels(wealthPlacementRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<RealEstateModel> getRealEstate() {
        if (wealthRealEstateRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveRealEstate();
        }
        return WealthEntityMapper.toRealEstateModels(wealthRealEstateRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<AssetCategoryModel> getAssetCategories() {
        if (wealthCategoryRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveAssetCategories();
        }
        return WealthEntityMapper.toCategoryModels(wealthCategoryRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<PatrimoineTransferModel> getTransfers() {
        // DA-14 : Patrimoine delegue la lecture des virements au silo Tresorerie
        if (jpaTreasuryStore == null) {
            return persistenceManager.getBudgetData().getEffectiveTransfers().stream()
                    .map(t -> new PatrimoineTransferModel(t.id(), t.placement(), t.date(), t.amount(), t.notes()))
                    .toList();
        }
        return jpaTreasuryStore.getTransfers().stream()
                .map(t -> new PatrimoineTransferModel(t.id(), t.placement(), t.date(), t.amount(), t.notes()))
                .toList();
    }

    @Override
    public Map<String, Object> savePatrimoineRow(PatrimoineList list, Map<String, Object> body) {
        if (list == PatrimoineList.TRANSFERS) {
            if (jpaTreasuryStore != null) {
                return jpaTreasuryStore.addTresorerieRow(com.moe.myfamilybudget.domain.treasury.port.TresorerieList.TRANSFERS, body);
            }
            return persistenceManager.writeAndGet(m -> m.savePatrimoineRow(list.key(), body));
        }
        return persistenceManager.writeAndGet(m -> m.savePatrimoineRow(list.key(), body));
    }

    @Override
    public void deletePatrimoineRow(PatrimoineList list, String id) {
        if (list == PatrimoineList.TRANSFERS) {
            if (jpaTreasuryStore != null) {
                jpaTreasuryStore.removeTresorerieRow(com.moe.myfamilybudget.domain.treasury.port.TresorerieList.TRANSFERS, id);
                return;
            }
            persistenceManager.write(m -> m.deletePatrimoineRow(list.key(), id));
            return;
        }
        persistenceManager.write(m -> m.deletePatrimoineRow(list.key(), id));
    }

    @Override
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        return persistenceManager.writeAndGet(m -> m.addPlacementHistoryEntry(placementId, body));
    }

    @Override
    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId,
                                                           Map<String, Object> body) {
        return persistenceManager.writeAndGet(m -> m.updatePlacementHistoryEntry(placementId, entryId, body));
    }

    @Override
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        persistenceManager.write(m -> m.deletePlacementHistoryEntry(placementId, entryId));
    }

    @Override
    public void addAssetCategory(AssetCategoryModel category) {
        persistenceManager.write(m -> m.addAssetCategory(category));
    }

    @Override
    public void updateAssetCategory(String id, AssetCategoryField field, Object value) {
        persistenceManager.write(m -> m.updateAssetCategory(id, field.key(), value));
    }

    @Override
    public void removeAssetCategory(String id) {
        persistenceManager.write(m -> m.removeAssetCategory(id));
    }

    /** SILO-119 (lot B1) : import du silo Patrimoine (sans virements, DA-14). */
    @Override
    public void replace(List<PlacementModel> placements, List<RealEstateModel> realEstate,
                        List<PatrimoineTransferModel> transfers, List<AssetCategoryModel> assetCategories) {
        persistenceManager.write(m -> m.replacePatrimoineSnapshot(placements, realEstate, assetCategories));
    }

    /** SILO-119 (lot B1) : remise à zéro du silo Patrimoine. */
    @Override
    public void reset() {
        persistenceManager.write(m -> m.resetPatrimoineSnapshot());
    }
}