package com.moe.myfamilybudget.server.internal.model;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import java.util.List;

public record SettingsResultModel(
        SettingsModel settings,
        List<AssetCategoryModel> assetCategories,
        int retireYear,
        List<Integer> years,
        BankImportModel bankImport
) {}
