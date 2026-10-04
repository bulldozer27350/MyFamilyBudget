package com.moe.myfamilybudget.application.service;

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

import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.mapper.SettingsMapper;
import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.adapter.BankPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.BudgetMutationLockAdapter;
import com.moe.myfamilybudget.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.SettingsReaderTestFactory;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryObjectifsSettingsStore;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;
import com.moe.myfamilybudget.server.internal.testsupport.SettingsCommandRouterTestFactory;

class ParametersServiceImplTest {

    private ParametersServiceImpl service;
    private SettingsMapper mapper;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        mapper = new SettingsMapper();
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        ObjectifsSettingsService objectifsSettingsService =
                new ObjectifsSettingsService(new InMemoryObjectifsSettingsStore());
        service = new ParametersServiceImpl(
                SettingsReaderTestFactory.of(persistenceManager),
                new PatrimoinePersistenceAdapter(persistenceManager),
                new BankPersistenceAdapter(persistenceManager),
                new RetirementPersistenceAdapter(persistenceManager),
                mapper,
                objectifsSettingsService,
                new PatrimoineCommandService(new PatrimoinePersistenceAdapter(persistenceManager)),
                new BudgetMutationLockAdapter(persistenceManager),
                SettingsCommandRouterTestFactory.of(persistenceManager, objectifsSettingsService));
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
    @DisplayName("SET-020 : un PATCH multi-owners écrit chaque famille et GET /settings les relit toutes")
    void testSettingsRoutedToEachOwner() {
        service.saveSettings(Map.of("settings", Map.of(
                "retireAge", 62,
                "childExitAge", 23,
                "pivotMode", "manual",
                "simulateUntilAge", 90,
                "inflationRate", new BigDecimal("0.03"),
                "goalSecureHorizonMonths", 18)));

        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) ((Map<String, Object>) service.getSettings().getBody())
                .get("settings");
        assertEquals(62, settings.get("retireAge"));
        assertEquals(23, settings.get("childExitAge"));
        assertEquals("manual", settings.get("pivotMode"));
        assertEquals(90, settings.get("simulateUntilAge"));
        assertEquals(new BigDecimal("0.03"), settings.get("inflationRate"));
        assertEquals(18, settings.get("goalSecureHorizonMonths"));
    }

    @Test
    @DisplayName("SET-020 : un champ inconnu est ignoré sans erreur")
    void testUnknownSettingIsIgnored() {
        ResponseEntity<Void> response = service.saveSettings(Map.of("field", "unknownField", "value", 1));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Les seuils des objectifs sont routés vers le domaine Objectifs et restent exposés dans settings")
    void testObjectifsHorizonsRoutedThroughSettingsFacade() {
        ResponseEntity<Object> initial = service.getSettings();
        @SuppressWarnings("unchecked")
        Map<String, Object> initialSettings = (Map<String, Object>) ((Map<String, Object>) initial.getBody()).get("settings");
        assertThat(initialSettings).containsEntry("goalSecureHorizonMonths", null);
        assertThat(initialSettings).containsEntry("goalLiquidHorizonMonths", null);

        service.saveSettings(Map.of("field", "goalSecureHorizonMonths", "value", 18));
        service.saveSettings(Map.of("settings", Map.of("goalLiquidHorizonMonths", "6")));

        ResponseEntity<Object> updated = service.getSettings();
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) ((Map<String, Object>) updated.getBody()).get("settings");
        assertEquals(18, settings.get("goalSecureHorizonMonths"));
        assertEquals(6, settings.get("goalLiquidHorizonMonths"));
        // Les autres paramètres ne sont pas affectés.
        assertEquals(1985, settings.get("birthYear"));
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
