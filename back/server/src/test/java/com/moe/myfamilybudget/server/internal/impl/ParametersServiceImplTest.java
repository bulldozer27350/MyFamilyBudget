package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.server.internal.mapper.SettingsMapper;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

class ParametersServiceImplTest {

    private ParametersServiceImpl service;
    private SettingsMapper mapper;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        mapper = new SettingsMapper();
        persistenceManager = new PersistenceManager();
        persistenceManager.init();
        service = new ParametersServiceImpl(persistenceManager, mapper);
    }

    @Test
    @DisplayName("getSettings() doit retourner 200 OK avec les données de paramètres et années calculées")
    void testGetSettings() {
        ResponseEntity<Object> response = service.getSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("settings");
        assertThat(body).containsKey("assetCategories");
        assertThat(body).containsKey("retireYear");
        assertThat(body).containsKey("years");

        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) body.get("settings");
        assertEquals(1985, settings.get("birthYear"));
        assertEquals(64, settings.get("retireAge"));
        assertEquals(2049, body.get("retireYear"));
    }

    @Test
    @DisplayName("saveSettings() doit mettre à jour les paramètres et valider l'état via getSettings()")
    void testSaveSettingsAndUpdateField() {
        Map<String, Object> updatePayload = Map.of(
                "field", "inflationRate",
                "value", new BigDecimal("0.025")
        );

        ResponseEntity<Void> saveResponse = service.saveSettings(updatePayload);
        assertEquals(HttpStatus.OK, saveResponse.getStatusCode());

        ResponseEntity<Object> getResponse = service.getSettings();
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) getResponse.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) body.get("settings");
        assertEquals(new BigDecimal("0.025"), settings.get("inflationRate"));
    }

    @Test
    @DisplayName("saveSettings() doit ajouter, modifier et supprimer des catégories d'actifs")
    void testSaveSettingsAssetCategoriesLifecycle() {
        // 1. Ajout d'une catégorie d'actif
        Map<String, Object> addRow = new HashMap<>();
        addRow.put("id", "ac_test");
        addRow.put("icon", "📈");
        addRow.put("name", "Actions Crypto");
        addRow.put("bucket", "growth");

        Map<String, Object> addPayload = Map.of(
                "action", "addAssetCategory",
                "row", addRow
        );

        ResponseEntity<Void> addResponse = service.saveSettings(addPayload);
        assertEquals(HttpStatus.OK, addResponse.getStatusCode());

        // Vérification de l'ajout
        ResponseEntity<Object> getAfterAdd = service.getSettings();
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyAdd = (Map<String, Object>) getAfterAdd.getBody();
        assertNotNull(bodyAdd);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categoriesAdd = (List<Map<String, Object>>) bodyAdd.get("assetCategories");
        assertThat(categoriesAdd).extracting("name").contains("Actions Crypto");

        // 2. Modification de la catégorie
        Map<String, Object> updatePayload = Map.of(
                "action", "updateAssetCategory",
                "id", "ac_test",
                "field", "name",
                "value", "Actions & ETF"
        );

        service.saveSettings(updatePayload);

        ResponseEntity<Object> getAfterUpdate = service.getSettings();
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyUpdate = (Map<String, Object>) getAfterUpdate.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categoriesUpdate = (List<Map<String, Object>>) bodyUpdate.get("assetCategories");
        assertThat(categoriesUpdate).extracting("name").contains("Actions & ETF");

        // 3. Suppression de la catégorie
        Map<String, Object> removePayload = Map.of(
                "action", "removeAssetCategory",
                "id", "ac_test"
        );

        service.saveSettings(removePayload);

        ResponseEntity<Object> getAfterRemove = service.getSettings();
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyRemove = (Map<String, Object>) getAfterRemove.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categoriesRemove = (List<Map<String, Object>>) bodyRemove.get("assetCategories");
        assertThat(categoriesRemove).extracting("name").doesNotContain("Actions & ETF");
    }
}
