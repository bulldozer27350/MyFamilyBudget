package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

public record SettingsResultModel(
        SettingsModel settings,
        List<AssetCategoryModel> assetCategories,
        int retireYear,
        List<Integer> years,
        BankImportModel bankImport
) {}
