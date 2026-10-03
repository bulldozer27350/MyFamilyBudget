package com.moe.myfamilybudget.server.internal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.SettingsResultModel;

/**
 * VT-300 : contrat {@code SettingsResultModel} + {@code ObjectifsParameters} -> réponse
 * {@code GET /settings}, sans Spring ni base de données.
 */
class SettingsMapperTest {

    private final SettingsMapper mapper = new SettingsMapper();

    private static SettingsModel settings() {
        return new SettingsModel(1990, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual",
                new BigDecimal("5000"), 21, new BigDecimal("0.10"), true, new BigDecimal("20000"), new BigDecimal("1000"),
                new BigDecimal("300"));
    }

    private static RetirementModel retirement() {
        return new RetirementModel(List.of(), new BigDecimal("47100"), new BigDecimal("0.015"),
                new BigDecimal("1.4386"), "2025-11-01", new BigDecimal("0.01"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> response, String key) {
        return (Map<String, Object>) response.get(key);
    }

    @Test
    @DisplayName("toResponseMap(null) retourne une réponse vide")
    void nullModelGivesEmptyMap() {
        assertTrue(mapper.toResponseMap(null).isEmpty());
        assertTrue(mapper.toResponseMap(null, new ObjectifsParameters(6, 2)).isEmpty());
    }

    @Test
    @DisplayName("SET-040 : pass2026 / passGrowthRate sont relus depuis la retraite, null si elle est absente")
    void readsPassParametersFromRetirement() {
        SettingsResultModel model = new SettingsResultModel(settings(), List.of(), 2054, List.of(), null);

        Map<String, Object> withRetirement = section(
                mapper.toResponseMap(model, new ObjectifsParameters(18, 4), retirement()), "settings");
        assertEquals(new BigDecimal("47100"), withRetirement.get("pass2026"));
        assertEquals(new BigDecimal("0.015"), withRetirement.get("passGrowthRate"));

        Map<String, Object> withoutRetirement = section(
                mapper.toResponseMap(model, new ObjectifsParameters(18, 4), null), "settings");
        assertNull(withoutRetirement.get("pass2026"));
        assertNull(withoutRetirement.get("passGrowthRate"));
    }

    @Test
    @DisplayName("toResponseMap() reprend les paramètres de base et réinjecte les seuils Objectifs")
    void mapsSettingsAndGoals() {
        SettingsResultModel model = new SettingsResultModel(settings(),
                List.of(new AssetCategoryModel("c1", "🏦", "Épargne", "liquid", "#00ff00")),
                2054, List.of(2026, 2027), null);

        Map<String, Object> response = mapper.toResponseMap(model, new ObjectifsParameters(18, 4), retirement());

        Map<String, Object> s = section(response, "settings");
        assertEquals(1990, s.get("birthYear"));
        assertEquals(64, s.get("retireAge"));
        assertEquals(85, s.get("simulateUntilAge"));
        assertEquals(new BigDecimal("0.02"), s.get("inflationRate"));
        assertEquals("2026-01-01", s.get("pivotDate"));
        assertEquals("manual", s.get("pivotMode"));
        assertEquals(new BigDecimal("5000"), s.get("startBalance"));
        assertEquals(21, s.get("childExitAge"));
        assertEquals(new BigDecimal("0.10"), s.get("taxAbattement"));
        assertEquals(new BigDecimal("47100"), s.get("pass2026"));
        assertEquals(new BigDecimal("0.015"), s.get("passGrowthRate"));
        assertEquals(Boolean.TRUE, s.get("sweepEnabled"));
        assertEquals(new BigDecimal("20000"), s.get("cashCeiling"));
        assertEquals(new BigDecimal("1000"), s.get("cashFloor"));
        assertEquals(new BigDecimal("300"), s.get("cashAlertThreshold"));
        assertEquals(18, s.get("goalSecureHorizonMonths"));
        assertEquals(4, s.get("goalLiquidHorizonMonths"));

        assertEquals(2054, response.get("retireYear"));
        assertEquals(List.of(2026, 2027), response.get("years"));
    }

    @Test
    @DisplayName("Sans paramètres Objectifs (ou null), les seuils sont exposés non renseignés")
    void goalsDefaultToUnset() {
        SettingsResultModel model = new SettingsResultModel(settings(), List.of(), 2054, List.of(), null);

        Map<String, Object> withDefaults = section(mapper.toResponseMap(model), "settings");
        Map<String, Object> withNull = section(mapper.toResponseMap(model, null), "settings");

        for (Map<String, Object> s : List.of(withDefaults, withNull)) {
            assertTrue(s.containsKey("goalSecureHorizonMonths"));
            assertTrue(s.containsKey("goalLiquidHorizonMonths"));
            assertNull(s.get("goalSecureHorizonMonths"));
            assertNull(s.get("goalLiquidHorizonMonths"));
        }
    }

    @Test
    @DisplayName("Les composants absents donnent des valeurs vides, jamais null")
    void nullComponentsGiveEmptyValues() {
        SettingsResultModel model = new SettingsResultModel(null, null, 0, null, null);

        Map<String, Object> response = mapper.toResponseMap(model);

        assertNotNull(response.get("settings"));
        assertTrue(section(response, "settings").isEmpty());
        assertEquals(List.of(), response.get("assetCategories"));
        assertEquals(List.of(), response.get("years"));
        assertNotNull(response.get("bankImport"));
        assertEquals(0, response.get("retireYear"));
    }

    @Test
    @DisplayName("Les catégories d'actifs sont converties dans l'ordre")
    void mapsAssetCategoriesInOrder() {
        SettingsResultModel model = new SettingsResultModel(settings(),
                List.of(new AssetCategoryModel("a", "🏠", "Immo", "illiquid", null),
                        new AssetCategoryModel("b", "💶", "Cash", "cash", "#fff")),
                2054, List.of(), null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories =
                (List<Map<String, Object>>) mapper.toResponseMap(model).get("assetCategories");

        assertEquals(2, categories.size());
        assertEquals("a", categories.get(0).get("id"));
        assertEquals("illiquid", categories.get(0).get("bucket"));
        assertNull(categories.get(0).get("color"));
        assertEquals("b", categories.get(1).get("id"));
        assertEquals("#fff", categories.get(1).get("color"));
    }

    @Test
    @DisplayName("Catégorie d'actif : aller-retour modèle -> map -> modèle")
    void assetCategoryRoundTrip() {
        AssetCategoryModel original = new AssetCategoryModel("x1", "🚗", "Voiture", "illiquid", "#123456");

        AssetCategoryModel back = mapper.toAssetCategoryModel(mapper.toAssetCategoryMap(original));

        assertEquals(original, back);
    }

    @Test
    @DisplayName("Catégorie d'actif : champs manquants remplacés par les valeurs par défaut")
    void assetCategoryDefaults() {
        AssetCategoryModel fromNull = mapper.toAssetCategoryModel(null);
        assertEquals("📁", fromNull.icon());
        assertEquals("", fromNull.name());
        assertEquals("cash", fromNull.bucket());
        assertNull(fromNull.color());
        assertFalse(fromNull.id().isBlank());

        Map<String, Object> partial = new HashMap<>();
        partial.put("name", "Livret");
        AssetCategoryModel fromPartial = mapper.toAssetCategoryModel(partial);
        assertEquals("Livret", fromPartial.name());
        assertEquals("📁", fromPartial.icon());
        assertEquals("cash", fromPartial.bucket());
        assertFalse(fromPartial.id().isBlank());

        assertTrue(mapper.toAssetCategoryMap(null).isEmpty());
    }
}
