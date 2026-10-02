package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.converter.CashflowEntityMapper;
import com.moe.myfamilybudget.server.internal.persistence.converter.WealthEntityMapper;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowTransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthPlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthRealEstateRepository;
import com.moe.myfamilybudget.server.internal.port.AssetCategoryField;
import com.moe.myfamilybudget.server.internal.port.PatrimoineList;
import com.moe.myfamilybudget.server.internal.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.port.PatrimoineWriter;

/**
 * Adaptateur de persistance pour {@link PatrimoineReader} (RF-B00) et {@link PatrimoineWriter} (DB-030).
 *
 * <p>DB-1051 : en production, la lecture des placements, des biens immobiliers et des categories d'actifs
 * passe par les repositories autonomes {@code wealth_*} (DB-1050). Les ecritures passent toujours par le
 * {@code PersistenceManager} : la passerelle de persistance recopie le patrimoine dans les tables autonomes
 * dans la meme transaction. Les virements ({@link #getTransfers()}) relevent de Tresorerie : depuis DB-1061 ils
 * sont lus depuis la table autonome {@code cashflow_transfer} (DB-1060).
 *
 * <p>Le constructeur sans repository conserve l'ancienne lecture depuis le cache memoire ; il sert aux tests
 * unitaires adosses a des repositories mockes et constitue le chemin de retour arriere.
 */
@Component
public class PatrimoinePersistenceAdapter implements PatrimoineReader, PatrimoineWriter {

    private final PersistenceManager persistenceManager;
    private final WealthPlacementRepository wealthPlacementRepository;
    private final WealthRealEstateRepository wealthRealEstateRepository;
    private final WealthCategoryRepository wealthCategoryRepository;
    private final CashflowTransferRepository cashflowTransferRepository;

    public PatrimoinePersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null, null, null, null);
    }

    @Autowired
    public PatrimoinePersistenceAdapter(PersistenceManager persistenceManager,
                                        WealthPlacementRepository wealthPlacementRepository,
                                        WealthRealEstateRepository wealthRealEstateRepository,
                                        WealthCategoryRepository wealthCategoryRepository,
                                        CashflowTransferRepository cashflowTransferRepository) {
        this.persistenceManager = persistenceManager;
        this.wealthPlacementRepository = wealthPlacementRepository;
        this.wealthRealEstateRepository = wealthRealEstateRepository;
        this.wealthCategoryRepository = wealthCategoryRepository;
        this.cashflowTransferRepository = cashflowTransferRepository;
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
    public List<TransferModel> getTransfers() {
        if (cashflowTransferRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveTransfers();
        }
        return CashflowEntityMapper.toTransferModels(cashflowTransferRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public Map<String, Object> savePatrimoineRow(PatrimoineList list, Map<String, Object> body) {
        return persistenceManager.writeAndGet(m -> m.savePatrimoineRow(list.key(), body));
    }

    @Override
    public void deletePatrimoineRow(PatrimoineList list, String id) {
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
}