package com.moe.myfamilybudget.server.internal.port;

import java.util.Map;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;

/**
 * Port d'ecriture pour le domaine Patrimoine (DB-030). Seul le command service du domaine
 * ({@code PatrimoineCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager} et
 * prepare la bascule JPA (DB-1051) sans changer l'appelant.
 *
 * <p>Les contrats {@code listKey} / corps generique sont conserves tels quels : leur retrait releve de
 * DB-050.
 */
public interface PatrimoineWriter {

    /** Cree ou remplace une ligne ; renvoie la ligne enregistree (dont son {@code id}). */
    Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> body);

    void deletePatrimoineRow(String listKey, String id);

    Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body);

    Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> body);

    void deletePlacementHistoryEntry(String placementId, String entryId);

    void addAssetCategory(AssetCategoryModel category);

    void updateAssetCategory(String id, String field, Object value);

    void removeAssetCategory(String id);
}
