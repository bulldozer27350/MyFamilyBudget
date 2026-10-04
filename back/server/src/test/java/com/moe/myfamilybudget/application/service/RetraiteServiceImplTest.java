package com.moe.myfamilybudget.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationInput;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementParameters;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementPersonInput;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.mapper.RetraiteMapper;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.application.model.RetraiteResultModel;
import com.moe.myfamilybudget.application.command.RetirementCommandService;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.SettingsPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.SettingsReaderTestFactory;
import com.moe.myfamilybudget.persistence.adapter.TaxPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

class RetraiteServiceImplTest {

    private RetraiteServiceImpl service;
    private RetraiteMapper mapper;
    private PersistenceManager persistenceManager;
    private RetirementCalculationService calculationService;

    @BeforeEach
    void setUp() {
        mapper = new RetraiteMapper();
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        calculationService = new RetirementCalculationService();
        service = new RetraiteServiceImpl(
                mapper,
                new RetirementInputFactory(),
                calculationService,
                new RetirementCommandService(new RetirementPersistenceAdapter(persistenceManager)),
                SettingsReaderTestFactory.of(persistenceManager),
                new SettingsPersistenceAdapter(persistenceManager),
                new RetirementPersistenceAdapter(persistenceManager),
                new TaxPersistenceAdapter(persistenceManager),
                new BudgetPersistenceAdapter(persistenceManager));
    }

    @Test
    @DisplayName("getRetraite() doit retourner 200 OK avec le modèle de réponse complet")
    void testGetRetraite() {
        ResponseEntity<Object> response = service.getRetraite();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(body.containsKey("retirement"));
        assertTrue(body.containsKey("retireYear"));
        assertTrue(body.containsKey("incomes"));
        assertTrue(body.containsKey("settings"));
    }

    @Test
    @DisplayName("saveRetraite() doit sauvegarder les données de retraite et retourner les résultats à jour")
    void testSaveRetraite() {
        Map<String, Object> savePayload = new HashMap<>();
        savePayload.put("pass2026", new BigDecimal("48000"));
        savePayload.put("passGrowthRate", new BigDecimal("0.02"));
        savePayload.put("agircPointValue", new BigDecimal("1.50"));
        savePayload.put("agircPointGrowthRate", new BigDecimal("0.012"));
        savePayload.put("agircPointDateGlobal", "2026-01-01");

        Map<String, Object> personPayload = new HashMap<>();
        personPayload.put("id", "p1");
        personPayload.put("name", "Jean Dupont");
        personPayload.put("birthYear", 1980);
        personPayload.put("trimestresValides", 120);
        personPayload.put("trimestresDate", "2025-01-01");
        personPayload.put("agircPoints", new BigDecimal("2000"));
        personPayload.put("cadre", true);

        savePayload.put("people", List.of(personPayload));

        ResponseEntity<Void> response = service.saveRetraite(savePayload);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verification de l'etat persiste via getRetraite()
        ResponseEntity<Object> getResponse = service.getRetraite();
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) getResponse.getBody();
        assertNotNull(responseBody);

        @SuppressWarnings("unchecked")
        Map<String, Object> retirement = (Map<String, Object>) responseBody.get("retirement");
        assertNotNull(retirement);
        assertEquals(new BigDecimal("48000"), retirement.get("pass2026"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> people = (List<Map<String, Object>>) retirement.get("people");
        assertThat(people).hasSize(1);
        assertEquals("Jean Dupont", people.get(0).get("name"));
        assertEquals(true, people.get(0).get("cadre"));
    }

    @Test
    @DisplayName("RetirementCalculationService.compute() calcule la décote lorsque le nombre de trimestres est inférieur à 172")
    void testComputeRetirementProjectionDecote() {
        RetirementPersonInput person = new RetirementPersonInput(
            1985, 120, LocalDate.of(2025, 1, 1), List.of(), new BigDecimal("1000"), new BigDecimal("0.0051"), List.of()
        );
        RetirementCalculationInput input = new RetirementCalculationInput(
            2049, new RetirementParameters(null, null, null, null, null), 0, List.of(person)
        );

        RetirementProjectionModel projection = calculationService.compute(input).people().get(0);

        assertNotNull(projection);
        assertTrue(projection.manqueTauxPlein());
        assertThat(projection.trimestresRequis()).isEqualTo(172);
        assertThat(projection.tauxApplique()).isLessThan(new BigDecimal("0.50"));
        assertThat(projection.decote()).isGreaterThan(BigDecimal.ZERO);
        assertThat(projection.surcote()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RetirementCalculationService.compute() calcule la surcote lorsque le nombre de trimestres est supérieur à 172")
    void testComputeRetirementProjectionSurcote() {
        RetirementPersonInput person = new RetirementPersonInput(
            1970, 180, LocalDate.of(2025, 1, 1), List.of(), new BigDecimal("3000"), new BigDecimal("0.0051"), List.of()
        );
        RetirementCalculationInput input = new RetirementCalculationInput(
            2034, new RetirementParameters(null, null, null, null, null), 0, List.of(person)
        );

        RetirementProjectionModel projection = calculationService.compute(input).people().get(0);

        assertNotNull(projection);
        assertFalse(projection.manqueTauxPlein());
        assertThat(projection.surcote()).isGreaterThan(BigDecimal.ZERO);
        assertThat(projection.tauxApplique()).isGreaterThan(new BigDecimal("0.50"));
        assertThat(projection.decote()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RetirementCalculationService.compute() applique une majoration de 10% si la famille compte 3 enfants ou plus")
    void testComputeRetirementProjectionMajoration3Enfants() {
        RetirementPersonInput person = new RetirementPersonInput(
            1980, 172, LocalDate.of(2025, 1, 1), List.of(), new BigDecimal("2000"), new BigDecimal("0.0051"), List.of()
        );
        RetirementCalculationInput input = new RetirementCalculationInput(
            2044, new RetirementParameters(null, null, null, null, null), 3, List.of(person)
        );

        RetirementProjectionModel projection = calculationService.compute(input).people().get(0);

        assertNotNull(projection);
        assertThat(projection.majoration()).isEqualTo(new BigDecimal("1.10"));
    }

    @Test
    @DisplayName("buildRetraiteResult() renvoie un modèle valide enrichi des projections")
    void testBuildRetraiteResult() {
        RetraiteResultModel result = service.buildRetraiteResult();

        assertNotNull(result);
        assertNotNull(result.retirement());
        assertNotNull(result.retireYear());
        assertNotNull(result.incomes());
        assertNotNull(result.settings());
    }
}
