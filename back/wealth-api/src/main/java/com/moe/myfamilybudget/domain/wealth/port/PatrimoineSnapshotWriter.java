package com.moe.myfamilybudget.domain.wealth.port;

import java.util.List;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;

/**
 * Port d'import et de réinitialisation du silo Patrimoine (SILO-119, lot B1). L'export passe par
 * {@link PatrimoineReader}.
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation. Elles ne modifient que les données du silo Patrimoine.
 */
public interface PatrimoineSnapshotWriter {

    /** Remplace le patrimoine ; une liste {@code null} est lue comme vide. */
    void replace(List<PlacementModel> placements, List<RealEstateModel> realEstate,
                 List<PatrimoineTransferModel> transfers, List<AssetCategoryModel> assetCategories);

    /** Remet le silo Patrimoine à ses valeurs par défaut (aucune donnée). */
    void reset();
}
