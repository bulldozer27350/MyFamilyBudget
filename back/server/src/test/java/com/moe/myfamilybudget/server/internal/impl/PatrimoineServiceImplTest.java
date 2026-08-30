package com.moe.myfamilybudget.server.internal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.api.model.AddPlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.LoanDto;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PlacementDto;
import com.moe.myfamilybudget.api.model.RealEstateDto;
import com.moe.myfamilybudget.api.model.TransferDto;
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

    // -------------------------------------------------------------------------
    // GET /patrimoine
    // -------------------------------------------------------------------------

    @Test
    void getPatrimoine_returnsValidResponse_nominal() {
        ResponseEntity<PatrimoineResponseDto> response = service.getPatrimoine(false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getPlacements());
        assertNotNull(response.getBody().getTransfers());
        assertNotNull(response.getBody().getLoans());
        assertNotNull(response.getBody().getRealEstate());
        assertNotNull(response.getBody().getPatrimoine());
        assertNotNull(response.getBody().getPatrimoine().getTotals());
        assertFalse(response.getBody().getPatrimoine().getTotals().isEmpty());
    }

    @Test
    void getPatrimoine_returnsValidResponse_constantEuros() {
        ResponseEntity<PatrimoineResponseDto> response = service.getPatrimoine(true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getPatrimoine());
        assertFalse(response.getBody().getPatrimoine().getTotals().isEmpty());
    }

    // -------------------------------------------------------------------------
    // POST & DELETE /patrimoine/placements
    // -------------------------------------------------------------------------

    @Test
    void savePatrimoineLigne_placements_createsAndUpdates() {
        String testId = "plc_test_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", testId);
        body.put("label", "Mon PEA");
        body.put("category", "Bourse");
        body.put("balance", new BigDecimal("10000"));
        body.put("balanceDate", "2026-01-01");
        body.put("monthly", new BigDecimal("500"));
        body.put("rateCorr", new BigDecimal("0.05"));

        // 1. Création
        ResponseEntity<Void> createResp = service.savePatrimoineLigne("placements", body);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertNull(createResp.getBody());

        // 2. Vérification création
        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        PlacementDto created = getResp.getBody().getPlacements().stream()
                .filter(p -> testId.equals(p.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(created);
        assertEquals("Mon PEA", created.getLabel());
        assertEquals(new BigDecimal("10000"), created.getBalance());

        // 3. Mise à jour
        body.put("label", "Mon Super PEA");
        body.put("balance", new BigDecimal("12000"));
        ResponseEntity<Void> updateResp = service.savePatrimoineLigne("placements", body);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());

        // 4. Vérification mise à jour
        ResponseEntity<PatrimoineResponseDto> updatedResp = service.getPatrimoine(false);
        PlacementDto updated = updatedResp.getBody().getPlacements().stream()
                .filter(p -> testId.equals(p.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals("Mon Super PEA", updated.getLabel());
        assertEquals(new BigDecimal("12000"), updated.getBalance());
    }

    @Test
    void deletePatrimoineLigne_placements_deletesSuccessfully() {
        String testId = "plc_del_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", testId);
        body.put("label", "Placement Temporaire");
        body.put("balance", new BigDecimal("1000"));

        service.savePatrimoineLigne("placements", body);

        // Vérification présence
        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        assertTrue(getResp.getBody().getPlacements().stream().anyMatch(p -> testId.equals(p.getId())));

        // Suppression
        ResponseEntity<Void> deleteResp = service.deletePatrimoineLigne("placements", testId);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // Vérification absence
        ResponseEntity<PatrimoineResponseDto> afterDeleteResp = service.getPatrimoine(false);
        assertFalse(afterDeleteResp.getBody().getPlacements().stream().anyMatch(p -> testId.equals(p.getId())));
    }

    // -------------------------------------------------------------------------
    // POST & DELETE /patrimoine/transfers
    // -------------------------------------------------------------------------

    @Test
    void savePatrimoineLigne_transfers_createsAndUpdates() {
        String transferId = "tr_test_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", transferId);
        body.put("placement", "Mon PEA");
        body.put("date", "2028-06-01");
        body.put("amount", new BigDecimal("3000"));
        body.put("notes", "Achat voiture");

        // 1. Création
        ResponseEntity<Void> createResp = service.savePatrimoineLigne("transfers", body);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertNull(createResp.getBody());

        // 2. Vérification présence
        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        TransferDto created = getResp.getBody().getTransfers().stream()
                .filter(t -> transferId.equals(t.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(created);
        assertEquals("Mon PEA", created.getPlacement());
        assertEquals(new BigDecimal("3000"), created.getAmount());

        // 3. Mise à jour
        body.put("amount", new BigDecimal("4500"));
        service.savePatrimoineLigne("transfers", body);

        ResponseEntity<PatrimoineResponseDto> updatedResp = service.getPatrimoine(false);
        TransferDto updated = updatedResp.getBody().getTransfers().stream()
                .filter(t -> transferId.equals(t.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("4500"), updated.getAmount());
    }

    @Test
    void deletePatrimoineLigne_transfers_deletesSuccessfully() {
        String transferId = "tr_del_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", transferId);
        body.put("placement", "Mon PEA");
        body.put("date", "2028-06-01");
        body.put("amount", new BigDecimal("3000"));

        service.savePatrimoineLigne("transfers", body);

        // Suppression
        ResponseEntity<Void> deleteResp = service.deletePatrimoineLigne("transfers", transferId);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // Vérification absence
        ResponseEntity<PatrimoineResponseDto> afterDeleteResp = service.getPatrimoine(false);
        assertFalse(afterDeleteResp.getBody().getTransfers().stream().anyMatch(t -> transferId.equals(t.getId())));
    }

    // -------------------------------------------------------------------------
    // POST & DELETE /patrimoine/realEstate
    // -------------------------------------------------------------------------

    @Test
    void savePatrimoineLigne_realEstate_createsAndUpdates() {
        String reId = "re_test_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", reId);
        body.put("label", "Maison Principale");
        body.put("type", "Résidence Principale");
        body.put("currentValue", new BigDecimal("350000"));
        body.put("valuationYear", 2026);
        body.put("annualGrowthRate", new BigDecimal("0.02"));

        // 1. Création
        ResponseEntity<Void> createResp = service.savePatrimoineLigne("realEstate", body);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertNull(createResp.getBody());

        // 2. Vérification présence
        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        RealEstateDto created = getResp.getBody().getRealEstate().stream()
                .filter(r -> reId.equals(r.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(created);
        assertEquals("Maison Principale", created.getLabel());
        assertEquals(new BigDecimal("350000"), created.getCurrentValue());

        // 3. Mise à jour
        body.put("currentValue", new BigDecimal("380000"));
        service.savePatrimoineLigne("realEstate", body);

        ResponseEntity<PatrimoineResponseDto> updatedResp = service.getPatrimoine(false);
        RealEstateDto updated = updatedResp.getBody().getRealEstate().stream()
                .filter(r -> reId.equals(r.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("380000"), updated.getCurrentValue());
    }

    @Test
    void deletePatrimoineLigne_realEstate_deletesSuccessfully() {
        String reId = "re_del_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", reId);
        body.put("label", "Appartement Locatif");
        body.put("currentValue", new BigDecimal("120000"));

        service.savePatrimoineLigne("realEstate", body);

        // Suppression
        ResponseEntity<Void> deleteResp = service.deletePatrimoineLigne("realEstate", reId);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // Vérification absence
        ResponseEntity<PatrimoineResponseDto> afterDeleteResp = service.getPatrimoine(false);
        assertFalse(afterDeleteResp.getBody().getRealEstate().stream().anyMatch(r -> reId.equals(r.getId())));
    }

    // -------------------------------------------------------------------------
    // POST & DELETE /patrimoine/loans
    // -------------------------------------------------------------------------

    @Test
    void savePatrimoineLigne_loans_createsAndUpdates() {
        String loanId = "loan_test_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", loanId);
        body.put("label", "Pret Residence Principale");
        body.put("crd", new BigDecimal("180000"));
        body.put("rate", new BigDecimal("0.008"));
        body.put("monthly", new BigDecimal("950"));
        body.put("insurance", new BigDecimal("15"));
        body.put("startDate", "2020-01-01");
        body.put("endDate", "2045-01-01");

        // 1. Création
        ResponseEntity<Void> createResp = service.savePatrimoineLigne("loans", body);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertNull(createResp.getBody());

        // 2. Vérification présence : c'est cette liste qui alimente la table
        // "Crédits" de la vue Patrimoine et le KPI "Passif" de l'export PDF.
        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        LoanDto created = getResp.getBody().getLoans().stream()
                .filter(l -> loanId.equals(l.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(created);
        assertEquals("Pret Residence Principale", created.getLabel());
        assertEquals(new BigDecimal("180000"), created.getCrd());

        // 3. Mise à jour
        body.put("crd", new BigDecimal("175000"));
        service.savePatrimoineLigne("loans", body);

        ResponseEntity<PatrimoineResponseDto> updatedResp = service.getPatrimoine(false);
        LoanDto updated = updatedResp.getBody().getLoans().stream()
                .filter(l -> loanId.equals(l.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("175000"), updated.getCrd());
    }

    @Test
    void savePatrimoineLigne_credits_aliasIsAcceptedForLoans() {
        // L'alias "credits" (utilisé historiquement côté JS) doit produire le même
        // résultat que "loans" côté backend.
        String loanId = "loan_alias_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", loanId);
        body.put("label", "Pret Auto");
        body.put("crd", new BigDecimal("12000"));

        ResponseEntity<Void> createResp = service.savePatrimoineLigne("credits", body);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());

        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        assertTrue(getResp.getBody().getLoans().stream().anyMatch(l -> loanId.equals(l.getId())));
    }

    @Test
    void deletePatrimoineLigne_loans_deletesSuccessfully() {
        String loanId = "loan_del_1";
        Map<String, Object> body = new HashMap<>();
        body.put("id", loanId);
        body.put("label", "Pret Temporaire");
        body.put("crd", new BigDecimal("5000"));

        service.savePatrimoineLigne("loans", body);

        // Vérification présence
        ResponseEntity<PatrimoineResponseDto> getResp = service.getPatrimoine(false);
        assertTrue(getResp.getBody().getLoans().stream().anyMatch(l -> loanId.equals(l.getId())));

        // Suppression
        ResponseEntity<Void> deleteResp = service.deletePatrimoineLigne("loans", loanId);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // Vérification absence
        ResponseEntity<PatrimoineResponseDto> afterDeleteResp = service.getPatrimoine(false);
        assertFalse(afterDeleteResp.getBody().getLoans().stream().anyMatch(l -> loanId.equals(l.getId())));
    }

    // -------------------------------------------------------------------------
    // HISTORIQUE DE VALORISATION PLACEMENT
    // -------------------------------------------------------------------------

    @Test
    void addPlacementHistoriquePoint_updatesPlacementBalance() {
        String plcId = "plc_hist_1";
        Map<String, Object> plc = new HashMap<>();
        plc.put("id", plcId);
        plc.put("label", "Assurance Vie");
        plc.put("balance", new BigDecimal("5000"));
        service.savePatrimoineLigne("placements", plc);

        AddPlacementHistoriquePointRequest point = new AddPlacementHistoriquePointRequest();
        point.setDate(LocalDate.of(2026, 6, 30));//2026-06-30
        point.setValue(new BigDecimal("5200"));

        ResponseEntity<Void> pointResp = service.addPlacementHistoriquePoint(plcId, point);
        assertEquals(HttpStatus.OK, pointResp.getStatusCode());
        assertNull(pointResp.getBody());

        // Vérification de la mise à jour de valorisation
        ResponseEntity<PatrimoineResponseDto> resp = service.getPatrimoine(false);
        PlacementDto updated = resp.getBody().getPlacements().stream()
                .filter(p -> plcId.equals(p.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("5200"), updated.getBalance());
        assertEquals("2026-06-30", updated.getBalanceDate());
    }

    @Test
    void deletePlacementHistoriquePoint_returnsNoContent() {
        String plcId = "plc_hist_2";
        ResponseEntity<Void> delResp = service.deletePlacementHistoriquePoint(plcId, 0);
        assertEquals(HttpStatus.NO_CONTENT, delResp.getStatusCode());
        assertNull(delResp.getBody());
    }

    // -------------------------------------------------------------------------
    // CALCULS PROJECTIONS PATRIMOINE
    // -------------------------------------------------------------------------

    @Test
    void computePatrimoineProjections_nominalAndConstantEuros() {
        PatrimoineProjectionsModel projNominal = service.computePatrimoineProjections(persistenceManager.getBudgetData(), false);
        PatrimoineProjectionsModel projReal = service.computePatrimoineProjections(persistenceManager.getBudgetData(), true);

        assertNotNull(projNominal);
        assertNotNull(projReal);
        assertFalse(projNominal.totals().isEmpty());
        assertFalse(projReal.totals().isEmpty());
        assertEquals(projNominal.totals().size(), projReal.totals().size());
    }
}
