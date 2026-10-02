package com.moe.myfamilybudget.server.internal.port;

import java.util.List;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.domain.budget.TransferModel;

/**
 * Port de lecture pour le domaine Patrimoine (RF-B00).
 */
public interface PatrimoineReader {

    List<PlacementModel> getPlacements();

    List<RealEstateModel> getRealEstate();

    List<AssetCategoryModel> getAssetCategories();

    List<TransferModel> getTransfers();
}