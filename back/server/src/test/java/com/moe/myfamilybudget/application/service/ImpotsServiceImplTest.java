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

import com.moe.myfamilybudget.domain.retirement.core.DefaultRetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.core.DefaultTaxCalculationService;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.application.command.TaxCommandService;
import com.moe.myfamilybudget.application.command.RetirementCommandService;
import com.moe.myfamilybudget.application.command.TresorerieCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.command.SimulationSettingsCommandService;
import com.moe.myfamilybudget.application.command.EconomicAssumptionsCommandService;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.mapper.TaxMapper;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryObjectifsSettingsStore;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;

class ImpotsServiceImplTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private ImpotsServiceImpl service;
    private TaxMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TaxMapper();
        CacheBackedAppSettings appSettings = new CacheBackedAppSettings();
        InMemoryTaxStore taxStore = new InMemoryTaxStore();
        InMemoryRetirementStore retirementStore = new InMemoryRetirementStore();
        InMemoryTreasuryStore treasuryStore = new InMemoryTreasuryStore();
        // R-61-15 : SettingsCommandRouterTestFactory.of(PersistenceManager, ...) depend du hub (retrait prevu
        // par R-61-21) ; le routeur est construit ici directement, memes stores par defaut que l'overload a
        // deux arguments de la factory (taxStore/retirementStore/treasuryStore internes non partages, seule
        // l'instance appSettings est partagee avec le service pour que simulateUntilAge/inflationRate restent
        // relus depuis la meme instance qu'en ecriture, comme avant R-61-04).
        SettingsCommandRouter settingsCommandRouter = new SettingsCommandRouter(
                new ObjectifsSettingsService(new InMemoryObjectifsSettingsStore()),
                new TaxCommandService(new InMemoryTaxStore(), silos -> { }, RecordingTransactionRunner.direct()),
                new RetirementCommandService(new InMemoryRetirementStore(), silos -> { }, RecordingTransactionRunner.direct()),
                new TresorerieCommandService(new InMemoryTreasuryStore(), silos -> { }, RecordingTransactionRunner.direct()),
                new SimulationSettingsCommandService(appSettings, silos -> { }, RecordingTransactionRunner.direct()),
                new EconomicAssumptionsCommandService(appSettings, silos -> { }, RecordingTransactionRunner.direct()));
        service = new ImpotsServiceImpl(
                mapper,
                new RetirementInputFactory(),
                new DefaultRetirementCalculationService(),
                new DefaultTaxCalculationService(),
                new TaxCommandService(taxStore, silos -> { }, RecordingTransactionRunner.direct()),
                settingsCommandRouter,
                retirementStore,
                taxStore,
                treasuryStore,
                appSettings,
                appSettings,
                taxStore,
                treasuryStore,
                new PatrimoinePersistenceAdapter(new InMemoryWealthStore().getDelegate(), new InMemoryTreasuryStore().getDelegate()),
                retirementStore,
                bankStore);
    }

    @Test
    @DisplayName("getImpots() doit retourner 200 OK avec la structure complète de simulation d'impôts")
    void testGetImpots() {
        ResponseEntity<Object> response = service.getImpots();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("taxChildren");
        assertThat(body).containsKey("taxBrackets");
        assertThat(body).containsKey("taxRateOverrides");
        assertThat(body).containsKey("taxActualOverrides");
        assertThat(body).containsKey("settings");
        assertThat(body).containsKey("taxPreview");
    }

    @Test
    @DisplayName("saveImpotsConfig() doit sauvegarder la configuration d'impôts et valider l'état via getImpots()")
    void testSaveImpotsConfigAndVerifyState() {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> child1 = new HashMap<>();
        child1.put("id", "tc1");
        child1.put("name", "Alice");
        child1.put("birthYear", 2018);

        Map<String, Object> bracket1 = new HashMap<>();
        bracket1.put("id", "tb1");
        bracket1.put("upTo", new BigDecimal("12000"));
        bracket1.put("rate", BigDecimal.ZERO);

        Map<String, Object> bracket2 = new HashMap<>();
        bracket2.put("id", "tb2");
        bracket2.put("upTo", null);
        bracket2.put("rate", new BigDecimal("0.11"));

        payload.put("taxChildren", List.of(child1));
        payload.put("taxBrackets", List.of(bracket1, bracket2));

        ResponseEntity<Void> saveResponse = service.saveImpotsConfig(payload);
        assertEquals(HttpStatus.OK, saveResponse.getStatusCode());

        // Validation de l'état mis à jour via getImpots() (GET)
        ResponseEntity<Object> getResponse = service.getImpots();
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> getBody = (Map<String, Object>) getResponse.getBody();
        assertNotNull(getBody);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) getBody.get("taxChildren");
        assertThat(children).hasSize(1);
        assertEquals("Alice", children.get(0).get("name"));
        assertEquals(2018, children.get(0).get("birthYear"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> brackets = (List<Map<String, Object>>) getBody.get("taxBrackets");
        assertThat(brackets).hasSize(2);
    }

    @Test
    @DisplayName("saveImpotsConfig() avec action resetDefaultTaxBrackets doit réinitialiser les tranches d'impôt")
    void testResetDefaultTaxBrackets() {
        Map<String, Object> resetPayload = Map.of("action", "resetDefaultTaxBrackets");

        ResponseEntity<Void> response = service.saveImpotsConfig(resetPayload);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ResponseEntity<Object> getResponse = service.getImpots();
        @SuppressWarnings("unchecked")
        Map<String, Object> getBody = (Map<String, Object>) getResponse.getBody();
        assertNotNull(getBody);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> brackets = (List<Map<String, Object>>) getBody.get("taxBrackets");
        assertThat(brackets).isNotEmpty();
    }
}
