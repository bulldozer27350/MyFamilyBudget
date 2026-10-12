package com.moe.myfamilybudget.application.model;

import com.moe.myfamilybudget.application.settings.SettingsView;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import java.util.List;

public record SettingsResultModel(
        SettingsView settings,
        List<AssetCategoryModel> assetCategories,
        int retireYear,
        List<Integer> years,
        BankImportModel bankImport
) {}
