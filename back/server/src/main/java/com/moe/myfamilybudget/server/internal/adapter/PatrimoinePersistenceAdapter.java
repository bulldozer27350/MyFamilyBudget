package com.moe.myfamilybudget.server.internal.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieList;
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
 * Routeur cross-silo pour {@link PatrimoineReader} (RF-B00), {@link PatrimoineWriter} (DB-030) et
 * {@link PatrimoineSnapshotWriter} (SILO-119, lot B1).
 *
 * <p>R-30 (SILO-215, lot B) : placements (et historique), immobilier et categories d'actifs sont
 * ecrits et lus directement par le silo Patrimoine ({@link JpaWealthStore}, module {@code wealth-core}),
 * sans plus passer par le {@code PersistenceManager}/{@code BudgetDataModel} (le hub).
 *
 * <p>DA-14 : les virements relevent du silo Tresorerie, pas de Patrimoine. {@code wealth-core} ne depend
 * que de {@code wealth-api} et {@code infra-jpa} (voir le commentaire de son pom) et ne peut donc pas
 * connaitre {@link JpaTreasuryStore} ; cet adaptateur, qui vit desormais dans le module {@code server}
 * (R-61-23b ; vivait dans {@code persistence}, qui voyait tous les silos, jusqu'a ce patch), reste
 * necessaire comme seul point capable de router les virements vers le silo Tresorerie tout en delegant
 * le reste du patrimoine a {@link JpaWealthStore}. Il ne depend plus du tout
 * de {@code PersistenceManager}/{@code BudgetDataModel} (voir {@code BudgetDataModelAllowList}).
 */
@Component
@Primary
public class PatrimoinePersistenceAdapter implements PatrimoineReader, PatrimoineWriter, PatrimoineSnapshotWriter {

    private final JpaWealthStore wealthStore;
    private final JpaTreasuryStore treasuryStore;

    public PatrimoinePersistenceAdapter(JpaWealthStore wealthStore, JpaTreasuryStore treasuryStore) {
        this.wealthStore = wealthStore;
        this.treasuryStore = treasuryStore;
    }

    @Override
    public List<PlacementModel> getPlacements() {
        return wealthStore.getPlacements();
    }

    @Override
    public List<RealEstateModel> getRealEstate() {
        return wealthStore.getRealEstate();
    }

    @Override
    public List<AssetCategoryModel> getAssetCategories() {
        return wealthStore.getAssetCategories();
    }

    @Override
    public List<PatrimoineTransferModel> getTransfers() {
        // DA-14 : Patrimoine delegue la lecture des virements au silo Tresorerie.
        return treasuryStore.getTransfers().stream()
                .map(t -> new PatrimoineTransferModel(t.id(), t.placement(), t.date(), t.amount(), t.notes()))
                .toList();
    }

    @Override
    public Map<String, Object> savePatrimoineRow(PatrimoineList list, Map<String, Object> body) {
        if (list == PatrimoineList.TRANSFERS) {
            return treasuryStore.addTresorerieRow(TresorerieList.TRANSFERS, body);
        }
        return wealthStore.savePatrimoineRow(list, body);
    }

    @Override
    public void deletePatrimoineRow(PatrimoineList list, String id) {
        if (list == PatrimoineList.TRANSFERS) {
            treasuryStore.removeTresorerieRow(TresorerieList.TRANSFERS, id);
            return;
        }
        wealthStore.deletePatrimoineRow(list, id);
    }

    @Override
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        return wealthStore.addPlacementHistoryEntry(placementId, body);
    }

    @Override
    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId,
                                                           Map<String, Object> body) {
        return wealthStore.updatePlacementHistoryEntry(placementId, entryId, body);
    }

    @Override
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        wealthStore.deletePlacementHistoryEntry(placementId, entryId);
    }

    @Override
    public void addAssetCategory(AssetCategoryModel category) {
        wealthStore.addAssetCategory(category);
    }

    @Override
    public void updateAssetCategory(String id, AssetCategoryField field, Object value) {
        wealthStore.updateAssetCategory(id, field, value);
    }

    @Override
    public void removeAssetCategory(String id) {
        wealthStore.removeAssetCategory(id);
    }

    /** R-30 : import du silo Patrimoine (sans virements, DA-14 — voir GlobalBudgetSnapshotService). */
    @Override
    public void replace(List<PlacementModel> placements, List<RealEstateModel> realEstate,
                        List<PatrimoineTransferModel> transfers, List<AssetCategoryModel> assetCategories) {
        wealthStore.replace(placements, realEstate, transfers, assetCategories);
    }

    /** R-30 : remise a zero du silo Patrimoine (sans virements, DA-14). */
    @Override
    public void reset() {
        wealthStore.reset();
    }
}
