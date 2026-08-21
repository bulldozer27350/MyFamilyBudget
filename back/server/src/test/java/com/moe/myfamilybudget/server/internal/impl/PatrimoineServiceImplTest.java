package com.moe.myfamilybudget.server.internal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PlacementDto;
import com.moe.myfamilybudget.server.internal.mapper.PatrimoineMapper;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

class PatrimoineServiceImplTest {

    private PatrimoineServiceImpl service;
    private PatrimoineMapper mapper;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        mapper = new PatrimoineMapper();
        persistenceManager = new PersistenceManager();
        persistenceManager.init();
        service = new PatrimoineServiceImpl(mapper, persistenceManager);
    }

    @Test
    void getPatrimoine_returnsValidResponse() {
        ResponseEntity<PatrimoineResponseDto> response = service.getPatrimoine(false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getPlacements());
        assertNotNull(response.getBody().getTransfers());
        assertNotNull(response.getBody().getRealEstate());
        assertNotNull(response.getBody().getPatrimoine());
        assertFalse(response.getBody().getPatrimoine().getTotals().isEmpty());
    }

    @Test
    void savePatrimoineLigne_placements_createsAndUpdates() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Mon PEA");
        body.put("category", "Bourse");
        body.put("balance", new BigDecimal("10000"));
        body.put("balanceDate", "2026-01-01");
        body.put("monthly", new BigDecimal("500"));
        body.put("rateCorr", new BigDecimal("0.05"));

        ResponseEntity<Object> createResp = service.savePatrimoineLigne("placements", body);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        assertTrue(createResp.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) createResp.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);

        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        boolean found = getResp.getBody().getPlacements().stream().anyMatch(p -> "Mon PEA".equals(p.getLabel()));
        assertTrue(found);

        // Update placement
        body.put("id", id);
        body.put("label", "Mon Super PEA");
        service.savePatrimoineLigne("placements", body);

        ResponseEntity<PatrimoineResponseDto> updatedResp = service.getPatrimoine(false);
        boolean foundUpdated = updatedResp.getBody().getPlacements().stream().anyMatch(p -> "Mon Super PEA".equals(p.getLabel()));
        assertTrue(foundUpdated);
    }

    @Test
    void savePatrimoineLigne_transfers_createsAndDeletes() {
        Map<String, Object> body = new HashMap<>();
        body.put("placement", "Mon PEA");
        body.put("date", "2028-06-01");
        body.put("amount", new BigDecimal("3000"));
        body.put("notes", "Achat voiture");

        ResponseEntity<Object> createResp = service.savePatrimoineLigne("transfers", body);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) createResp.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);

        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        boolean found = getResp.getBody().getTransfers().stream().anyMatch(t -> id.equals(t.getId()));
        assertTrue(found);

        // Delete transfer
        ResponseEntity<Void> deleteResp = service.deletePatrimoineLigne("transfers", id);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        ResponseEntity<PatrimoineResponseDto> afterDeleteResp = service.getPatrimoine(false);
        boolean stillPresent = afterDeleteResp.getBody().getTransfers().stream().anyMatch(t -> id.equals(t.getId()));
        assertFalse(stillPresent);
    }

    @Test
    void savePatrimoineLigne_realEstate_createsAndDeletes() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Maison Principale");
        body.put("type", "Résidence Principale");
        body.put("currentValue", new BigDecimal("350000"));
        body.put("valuationYear", 2026);
        body.put("annualGrowthRate", new BigDecimal("0.02"));

        ResponseEntity<Object> createResp = service.savePatrimoineLigne("realEstate", body);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) createResp.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);

        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        boolean found = getResp.getBody().getRealEstate().stream().anyMatch(r -> id.equals(r.getId()));
        assertTrue(found);

        // Delete real estate
        ResponseEntity<Void> deleteResp = service.deletePatrimoineLigne("realEstate", id);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        ResponseEntity<PatrimoineResponseDto> afterDeleteResp = service.getPatrimoine(false);
        boolean stillPresent = afterDeleteResp.getBody().getRealEstate().stream().anyMatch(r -> id.equals(r.getId()));
        assertFalse(stillPresent);
    }

    @Test
    void addPlacementHistoriquePoint_andDelete() {
        // Create placement first
        Map<String, Object> plc = new HashMap<>();
        plc.put("label", "Assurance Vie");
        plc.put("balance", new BigDecimal("5000"));
        ResponseEntity<Object> createPlc = service.savePatrimoineLigne("placements", plc);
        @SuppressWarnings("unchecked")
        String plcId = (String) ((Map<String, Object>) createPlc.getBody()).get("id");

        Map<String, Object> point = new HashMap<>();
        point.put("date", "2026-06-30");
        point.put("value", new BigDecimal("5200"));

        ResponseEntity<Object> pointResp = service.addPlacementHistoriquePoint(plcId, point);
        assertEquals(HttpStatus.OK, pointResp.getStatusCode());

        ResponseEntity<Void> delResp = service.deletePlacementHistoriquePoint(plcId, 0);
        assertEquals(HttpStatus.NO_CONTENT, delResp.getStatusCode());
    }

    @Test
    void computePatrimoineProjections_constantEuros() {
        PatrimoineProjectionsModel projNominal = service.computePatrimoineProjections(persistenceManager.getBudgetData(), false);
        PatrimoineProjectionsModel projReal = service.computePatrimoineProjections(persistenceManager.getBudgetData(), true);

        assertNotNull(projNominal);
        assertNotNull(projReal);
        assertFalse(projNominal.totals().isEmpty());
        assertFalse(projReal.totals().isEmpty());
    }
}
