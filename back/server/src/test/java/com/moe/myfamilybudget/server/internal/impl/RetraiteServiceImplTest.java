package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.server.internal.mapper.RetraiteMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.RetraiteResultModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

class RetraiteServiceImplTest {

    private RetraiteServiceImpl service;
    private RetraiteMapper mapper;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        mapper = new RetraiteMapper();
        persistenceManager = new PersistenceManager();
        persistenceManager.init();
        service = new RetraiteServiceImpl(persistenceManager, mapper);
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

        savePayload.put("people", List.of(personPayload));

        ResponseEntity<Object> response = service.saveRetraite(savePayload);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertNotNull(responseBody);

        @SuppressWarnings("unchecked")
        Map<String, Object> retirement = (Map<String, Object>) responseBody.get("retirement");
        assertNotNull(retirement);
        assertEquals(new BigDecimal("48000"), retirement.get("pass2026"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> people = (List<Map<String, Object>>) retirement.get("people");
        assertThat(people).hasSize(1);
        assertEquals("Jean Dupont", people.get(0).get("name"));
    }

    @Test
    @DisplayName("computeRetirementProjection() calcule la décote lorsque le nombre de trimestres est inférieur à 172")
    void testComputeRetirementProjectionDecote() {
        BudgetDataModel data = persistenceManager.getBudgetData();

        RetirementModel.RetirementPersonModel person = new RetirementModel.RetirementPersonModel(
            "p1", "Alice", 1985, "", 120, "2025-01-01", List.of(), new BigDecimal("1000"), new BigDecimal("0.0051")
        );

        RetirementProjectionModel projection = service.computeRetirementProjection(data, person, 2049);

        assertNotNull(projection);
        assertTrue(projection.manqueTauxPlein());
        assertThat(projection.trimestresRequis()).isEqualTo(172);
        assertThat(projection.tauxApplique()).isLessThan(new BigDecimal("0.50"));
        assertThat(projection.decote()).isGreaterThan(BigDecimal.ZERO);
        assertThat(projection.surcote()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeRetirementProjection() calcule la surcote lorsque le nombre de trimestres est supérieur à 172")
    void testComputeRetirementProjectionSurcote() {
        BudgetDataModel data = persistenceManager.getBudgetData();

        RetirementModel.RetirementPersonModel person = new RetirementModel.RetirementPersonModel(
            "p2", "Bob", 1970, "", 180, "2025-01-01", List.of(), new BigDecimal("3000"), new BigDecimal("0.0051")
        );

        RetirementProjectionModel projection = service.computeRetirementProjection(data, person, 2034);

        assertNotNull(projection);
        assertFalse(projection.manqueTauxPlein());
        assertThat(projection.surcote()).isGreaterThan(BigDecimal.ZERO);
        assertThat(projection.tauxApplique()).isGreaterThan(new BigDecimal("0.50"));
        assertThat(projection.decote()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("computeRetirementProjection() applique une majoration de 10% si la famille compte 3 enfants ou plus")
    void testComputeRetirementProjectionMajoration3Enfants() {
        BudgetDataModel defaultData = persistenceManager.getBudgetData();

        List<TaxChildModel> threeChildren = List.of(
            new TaxChildModel("c1", "Enfant 1", 2010),
            new TaxChildModel("c2", "Enfant 2", 2012),
            new TaxChildModel("c3", "Enfant 3", 2015)
        );

        BudgetDataModel dataWithChildren = new BudgetDataModel(
            defaultData.settings(), defaultData.incomes(), defaultData.charges(), defaultData.placements(),
            defaultData.realEstate(), defaultData.retirement(), threeChildren, defaultData.taxBrackets(),
            defaultData.taxRateOverrides(), defaultData.taxActualOverrides(), defaultData.oneoff(),
            defaultData.transfers(), defaultData.variableIncomes(), defaultData.variableOverrides(),
            defaultData.bankImport()
        );

        RetirementModel.RetirementPersonModel person = new RetirementModel.RetirementPersonModel(
            "p3", "Charlie", 1980, "", 172, "2025-01-01", List.of(), new BigDecimal("2000"), new BigDecimal("0.0051")
        );

        RetirementProjectionModel projection = service.computeRetirementProjection(dataWithChildren, person, 2044);

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
