package com.moe.myfamilybudget.server.internal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsCalculatorTest {

    @Test
    @DisplayName("computeSettingsResult() doit calculer correctement retireYear et la liste des années de simulation")
    void testComputeSettingsResultSuccess() {
        SettingsModel settings = new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual",
                new BigDecimal("10000.00"), 21, new BigDecimal("0.10"),
                new BigDecimal("47100"), new BigDecimal("0.015")
        );

        AssetCategoryModel cat1 = new AssetCategoryModel("ac1", "📁", "Cash", "cash");
        List<AssetCategoryModel> categories = List.of(cat1);

        SettingsResultModel result = SettingsCalculator.computeSettingsResult(settings, categories, null);

        assertNotNull(result);
        assertEquals(2049, result.retireYear()); // 1985 + 64 = 2049
        // Années de 2049 à 2070 (1985 + 85 = 2070) -> 22 années
        assertThat(result.years()).hasSize(22);
        assertEquals(2049, result.years().get(0));
        assertEquals(2070, result.years().get(21));
        assertThat(result.assetCategories()).hasSize(1);
        assertEquals("Cash", result.assetCategories().get(0).name());
    }

    @Test
    @DisplayName("computeSettingsResult() avec un objet settings null doit lever une IllegalArgumentException")
    void testComputeSettingsResultNullSettings() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SettingsCalculator.computeSettingsResult(null, List.of(), null)
        );
        assertThat(exception.getMessage()).contains("SettingsModel ne peut pas être null");
    }

    @Test
    @DisplayName("computeSettingsResult() avec une année de naissance invalide doit lever une IllegalArgumentException")
    void testComputeSettingsResultInvalidBirthYear() {
        SettingsModel invalidSettings = new SettingsModel(
                1800, 64, 85, new BigDecimal("0.02"), "", "manual",
                BigDecimal.ZERO, 21, new BigDecimal("0.10"),
                new BigDecimal("47100"), new BigDecimal("0.015")
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SettingsCalculator.computeSettingsResult(invalidSettings, List.of(), null)
        );
        assertThat(exception.getMessage()).contains("Année de naissance invalide");
    }

    @Test
    @DisplayName("computeSettingsResult() avec un âge de fin de simulation inférieur à l'âge de retraite doit lever une IllegalArgumentException")
    void testComputeSettingsResultSimulateAgeLowerThanRetireAge() {
        SettingsModel invalidSettings = new SettingsModel(
                1985, 64, 60, new BigDecimal("0.02"), "", "manual",
                BigDecimal.ZERO, 21, new BigDecimal("0.10"),
                new BigDecimal("47100"), new BigDecimal("0.015")
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SettingsCalculator.computeSettingsResult(invalidSettings, List.of(), null)
        );
        assertThat(exception.getMessage()).contains("L'âge de fin de simulation");
    }
}
