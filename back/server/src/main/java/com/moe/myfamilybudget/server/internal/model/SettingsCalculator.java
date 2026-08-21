package com.moe.myfamilybudget.server.internal.model;

import java.util.ArrayList;
import java.util.List;

public class SettingsCalculator {

    public static SettingsResultModel computeSettingsResult(
            SettingsModel settings,
            List<AssetCategoryModel> assetCategories,
            BankImportModel bankImport
    ) {
        if (settings == null) {
            throw new IllegalArgumentException("SettingsModel ne peut pas être null");
        }

        int birthYear = settings.birthYear();
        if (birthYear < 1900 || birthYear > 2100) {
            throw new IllegalArgumentException("Année de naissance invalide : " + birthYear);
        }

        int retireAge = settings.retireAge();
        if (retireAge < 0 || retireAge > 120) {
            throw new IllegalArgumentException("Âge de départ à la retraite invalide : " + retireAge);
        }

        int simulateUntilAge = settings.simulateUntilAge();
        if (simulateUntilAge < retireAge) {
            throw new IllegalArgumentException("L'âge de fin de simulation (" + simulateUntilAge +
                    ") doit être supérieur ou égal à l'âge de retraite (" + retireAge + ")");
        }

        int retireYear = birthYear + retireAge;
        List<Integer> years = new ArrayList<>();
        int endYear = birthYear + simulateUntilAge;
        for (int y = retireYear; y <= endYear; y++) {
            years.add(y);
        }

        List<AssetCategoryModel> effectiveCategories = assetCategories != null ? assetCategories : List.of();

        return new SettingsResultModel(
                settings,
                effectiveCategories,
                retireYear,
                years,
                bankImport
        );
    }
}
