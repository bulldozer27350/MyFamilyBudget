package com.moe.myfamilybudget.application.model;

import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import java.util.List;

public record SettingsResultModel(
        SettingsModel settings,
        List<AssetCategoryModel> assetCategories,
        int retireYear,
        List<Integer> years,
        BankImportModel bankImport
) {}
