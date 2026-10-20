package com.moe.myfamilybudget.application.model;

import com.moe.myfamilybudget.application.settings.SettingsView;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import java.util.ArrayList;
import java.util.List;

public class SettingsCalculator {

    public static SettingsResultModel computeSettingsResult(
            SettingsView settings,
            List<AssetCategoryModel> assetCategories,
            BankImportModel bankImport
    ) {
        if (settings == null) {
            throw new IllegalArgumentException("SettingsView ne peut pas être null");
        }

        // R-40 correctif 4 : birthYear/retireAge/simulateUntilAge sont des Integer nullables sur
        // SettingsView (silo non encore renseigné) ; on passe par les accesseurs Effective* (déjà
        // prévus pour défaut) au lieu d'un unboxing direct, qui provoquait un NullPointerException.
        int birthYear = settings.getEffectiveBirthYear();
        if (birthYear < 1900 || birthYear > 2100) {
            throw new IllegalArgumentException("Année de naissance invalide : " + birthYear);
        }

        int retireAge = settings.getEffectiveRetireAge();
        if (retireAge < 0 || retireAge > 120) {
            throw new IllegalArgumentException("Âge de départ à la retraite invalide : " + retireAge);
        }

        int simulateUntilAge = settings.getEffectiveSimulateUntilAge();
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
