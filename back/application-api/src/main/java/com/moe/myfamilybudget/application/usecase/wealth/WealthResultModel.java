package com.moe.myfamilybudget.application.usecase.wealth;

import java.util.List;

/**
 * Modèle applicatif de la réponse Patrimoine : placements, virements, immobilier, catégories
 * d'actifs, projections, ainsi que deux blocs lus chez d'autres silos (prêts, catégories de
 * l'import bancaire) nécessaires au contrat historique de la réponse REST.
 */
public record WealthResultModel(
    List<PlacementModel> placements,
    List<PatrimoineTransferModel> transfers,
    List<RealEstateModel> realEstate,
    List<LoanModel> loans,
    List<AssetCategoryModel> assetCategories,
    List<BankImportCategoryModel> bankCategories,
    PatrimoineProjectionsModel projections
) {}
