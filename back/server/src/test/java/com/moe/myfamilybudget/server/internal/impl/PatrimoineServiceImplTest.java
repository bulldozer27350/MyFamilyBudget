package com.moe.myfamilybudget.server.internal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.api.model.AddPlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.LoanDto;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PlacementDto;
import com.moe.myfamilybudget.api.model.PlacementEvolutionDto;
import com.moe.myfamilybudget.api.model.PlacementHistoryEntryDto;
import com.moe.myfamilybudget.api.model.RealEstateDto;
import com.moe.myfamilybudget.api.model.TransferDto;
import com.moe.myfamilybudget.server.internal.mapper.PatrimoineMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineYearModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

class PatrimoineServiceImplTest {

    private PatrimoineServiceImpl service;
    private PatrimoineMapper mapper;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        mapper = new PatrimoineMapper();
        persistenceManager = PersistenceManagerTestFactory.inMemory();
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
    void addPlacementHistoriquePoint_addsEntryAndSyncsReferenceBalance() {
        String plcId = "plc_hist_1";
        Map<String, Object> plc = new HashMap<>();
        plc.put("id", plcId);
        plc.put("label", "Assurance Vie");
        plc.put("balance", new BigDecimal("5000"));
        service.savePatrimoineLigne("placements", plc);

        AddPlacementHistoriquePointRequest point = new AddPlacementHistoriquePointRequest();
        point.setDate(LocalDate.of(2026, 6, 30));//2026-06-30
        point.setValue(new BigDecimal("5200"));

        ResponseEntity<PlacementHistoryEntryDto> pointResp = service.addPlacementHistoriquePoint(plcId, point);
        assertEquals(HttpStatus.OK, pointResp.getStatusCode());
        assertNotNull(pointResp.getBody());
        assertNotNull(pointResp.getBody().getId());
        assertEquals(new BigDecimal("5200"), pointResp.getBody().getValue());
        String entryId = pointResp.getBody().getId();

        // Le solde de reference du placement (balance/balanceDate) est resynchronise sur la
        // derniere valeur d'historique connue : c'est lui qui alimente Tresorerie et Vue
        // d'ensemble, et il n'est plus saisissable directement depuis la fiche d'edition du
        // placement (la saisie se fait desormais uniquement via l'historique).
        ResponseEntity<PatrimoineResponseDto> resp = service.getPatrimoine(false);
        PlacementDto updated = resp.getBody().getPlacements().stream()
                .filter(p -> plcId.equals(p.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("5200"), updated.getBalance());
        assertEquals("2026-06-30", updated.getBalanceDate());
        assertNotNull(updated.getHistory());
        assertEquals(1, updated.getHistory().size());
        assertEquals(entryId, updated.getHistory().get(0).getId());
        assertEquals("2026-06-30", updated.getHistory().get(0).getDate());
    }

    @Test
    void deletePlacementHistoriquePoint_returnsNoContent() {
        String plcId = "plc_hist_2";
        ResponseEntity<Void> delResp = service.deletePlacementHistoriquePoint(plcId, "entry_does_not_exist");
        assertEquals(HttpStatus.NO_CONTENT, delResp.getStatusCode());
        assertNull(delResp.getBody());
    }

    @Test
    void getPlacementEvolution_returnsRealPointThenProjections() {
        String plcId = "plc_hist_3";
        Map<String, Object> plc = new HashMap<>();
        plc.put("id", plcId);
        plc.put("label", "Livret évolution");
        plc.put("balance", new BigDecimal("1000"));
        plc.put("ratePess", new BigDecimal("0.01"));
        plc.put("rateCorr", new BigDecimal("0.02"));
        plc.put("rateOpti", new BigDecimal("0.03"));
        service.savePatrimoineLigne("placements", plc);

        AddPlacementHistoriquePointRequest point = new AddPlacementHistoriquePointRequest();
        point.setDate(LocalDate.of(2026, 1, 1));
        point.setValue(new BigDecimal("1000"));
        service.addPlacementHistoriquePoint(plcId, point);

        ResponseEntity<PlacementEvolutionDto> evoResp = service.getPlacementEvolution(plcId, false);
        assertEquals(HttpStatus.OK, evoResp.getStatusCode());
        PlacementEvolutionDto evo = evoResp.getBody();
        assertNotNull(evo);
        assertEquals(plcId, evo.getPlacementId());
        assertFalse(evo.getPoints().isEmpty());
        assertTrue(evo.getPoints().stream().anyMatch(p -> p.getReal() != null));
        assertTrue(evo.getPoints().stream().anyMatch(p -> p.getCorr() != null));
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

    // -------------------------------------------------------------------------
    // MECANISME DE PAUSE AUTOMATIQUE DES VERSEMENTS (portage de view/js/calculations.js)
    // -------------------------------------------------------------------------

    /**
     * Construit un budget avec deux placements inspires du scenario de verification
     * fonctionnelle : un placement "declencheur" (bufferWatch) deja sous son seuil
     * d'alerte, et un placement "pausable" dont les versements doivent etre suspendus des
     * que le niveau de pause atteint sa priorite. Les taux sont mis a zero pour isoler
     * l'effet des versements dans les assertions.
     */
    private BudgetDataModel buildPauseScenarioBudget(boolean sweepEnabled, BigDecimal cashFloor, BigDecimal startBalance) {
        PlacementModel scpiEden = new PlacementModel(
                "plc_scpi_eden", "Test SCPI Eden", "SCPI", new BigDecimal("3700"), "2026-01-01",
                BigDecimal.ZERO, "2026-01-01", null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, null, null, null, new BigDecimal("10000"), null, "cat_scpi"
        );
        PlacementModel selencia = new PlacementModel(
                "plc_selencia", "Test Selencia", "Assurance Vie", new BigDecimal("14425"), "2026-01-01",
                new BigDecimal("200"), "2026-01-01", null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, null, null, null, null, 1, "cat_av"
        );

        SettingsModel settings = new SettingsModel(
                1985, 64, 85, BigDecimal.ZERO, "", "manual", startBalance, 21, BigDecimal.ZERO,
                new BigDecimal("47100"), new BigDecimal("0.015"), sweepEnabled, null, cashFloor
        );

        return new BudgetDataModel(settings, List.of(), List.of(), List.of(selencia, scpiEden), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }

    @Test
    void computePatrimoineProjections_suspendsContributionsWhenBufferWatchPlacementBelowThreshold() {
        // "Test SCPI Eden" est deja sous son seuil d'alerte (3700 < 10000) des le depart :
        // cela doit declencher la pause (mecanisme pauseLevelFromAlerts) et suspendre les
        // versements de "Test Selencia" (pausePriority = 1), sans jamais activer le
        // mecanisme de refill (sweepEnabled = false ici).
        BudgetDataModel data = buildPauseScenarioBudget(false, null, BigDecimal.ZERO);
        persistenceManager.setBudgetData(data);

        PatrimoineProjectionsModel proj = service.computePatrimoineProjections(persistenceManager.getBudgetData(), false);

        PatrimoinePerPlacementModel selenciaRows = proj.perPlacement().stream()
                .filter(p -> "Test Selencia".equals(p.label()))
                .findFirst()
                .orElseThrow();
        List<PatrimoineYearModel> rows = selenciaRows.rows();
        assertTrue(rows.size() >= 4, "Le scenario de test doit couvrir au moins 4 annees");

        // Annee 1 : le pauseLevel initial est 0 (pas encore evalue), le versement de
        // 200EUR/mois est donc applique normalement.
        BigDecimal afterYear1 = rows.get(0).corr();
        assertEquals(0, new BigDecimal("16825.00").compareTo(afterYear1));

        // A partir de l'annee 2, le pauseLevel evalue en fin d'annee 1 (SCPI Eden sous son
        // seuil) suspend les versements : le solde de Selencia ne doit plus bouger.
        assertEquals(0, afterYear1.compareTo(rows.get(1).corr()));
        assertEquals(0, afterYear1.compareTo(rows.get(2).corr()));
        assertEquals(0, afterYear1.compareTo(rows.get(3).corr()));

        // Placement sans pausePriority : jamais affecte par le mecanisme, cf. exigence de
        // non-regression du plan de verification.
        PatrimoinePerPlacementModel scpiRows = proj.perPlacement().stream()
                .filter(p -> "Test SCPI Eden".equals(p.label()))
                .findFirst()
                .orElseThrow();
        for (PatrimoineYearModel row : scpiRows.rows()) {
            assertEquals(0, new BigDecimal("3700").compareTo(row.corr()));
        }
    }

    @Test
    void computePatrimoineProjections_resumesContributionsWhenBufferWatchPlacementRecovers() {
        // Meme scenario, mais "Test SCPI Eden" repasse au-dessus de son seuil (12000) :
        // aucune alerte ne doit se declencher et les versements de Selencia doivent suivre
        // leur cours normal, annee apres annee.
        PlacementModel scpiEden = new PlacementModel(
                "plc_scpi_eden", "Test SCPI Eden", "SCPI", new BigDecimal("12000"), "2026-01-01",
                BigDecimal.ZERO, "2026-01-01", null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, null, null, null, new BigDecimal("10000"), null, "cat_scpi"
        );
        PlacementModel selencia = new PlacementModel(
                "plc_selencia", "Test Selencia", "Assurance Vie", new BigDecimal("14425"), "2026-01-01",
                new BigDecimal("200"), "2026-01-01", null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, null, null, null, null, 1, "cat_av"
        );
        SettingsModel settings = new SettingsModel(
                1985, 64, 85, BigDecimal.ZERO, "", "manual", BigDecimal.ZERO, 21, BigDecimal.ZERO,
                new BigDecimal("47100"), new BigDecimal("0.015"), false, null, null
        );
        BudgetDataModel data = new BudgetDataModel(settings, List.of(), List.of(), List.of(selencia, scpiEden), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        persistenceManager.setBudgetData(data);

        PatrimoineProjectionsModel proj = service.computePatrimoineProjections(persistenceManager.getBudgetData(), false);
        PatrimoinePerPlacementModel selenciaRows = proj.perPlacement().stream()
                .filter(p -> "Test Selencia".equals(p.label()))
                .findFirst()
                .orElseThrow();
        List<PatrimoineYearModel> rows = selenciaRows.rows();

        BigDecimal afterYear1 = rows.get(0).corr();
        BigDecimal afterYear2 = rows.get(1).corr();
        assertEquals(0, new BigDecimal("16825.00").compareTo(afterYear1));
        // Le versement continue normalement : +2400 chaque annee.
        assertEquals(0, afterYear1.add(new BigDecimal("2400")).compareTo(afterYear2));
    }

    @Test
    void getPlacementEvolution_suspendsTracedPlacementWhenBufferWatchPlacementBelowThreshold() {
        // Meme scenario que computePatrimoineProjections_suspendsContributionsWhenBufferWatchPlacementBelowThreshold,
        // mais verifie via la courbe mensuelle individuelle (drawer de detail), qui doit
        // simuler tous les placements en arriere-plan pour evaluer la meme pause.
        BudgetDataModel data = buildPauseScenarioBudget(false, null, BigDecimal.ZERO);
        persistenceManager.setBudgetData(data);

        ResponseEntity<PlacementEvolutionDto> resp = service.getPlacementEvolution("plc_selencia", false);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        PlacementEvolutionDto evo = resp.getBody();
        assertNotNull(evo);
        assertFalse(evo.getPoints().isEmpty());

        // Le solde doit progresser durant les tout premiers mois (versement pas encore
        // suspendu), puis se figer une fois le pauseLevel active (SCPI Eden est sous son
        // seuil des le premier mois de simulation en arriere-plan).
        BigDecimal firstMonthCorr = evo.getPoints().get(0).getCorr();
        BigDecimal lastMonthCorr = evo.getPoints().get(evo.getPoints().size() - 1).getCorr();
        assertTrue(lastMonthCorr.compareTo(firstMonthCorr) >= 0);

        // Deux points suffisamment tardifs doivent etre identiques : la pause s'est
        // maintenue (SCPI Eden ne recupere jamais dans ce scenario).
        BigDecimal midCorr = evo.getPoints().get(evo.getPoints().size() / 2).getCorr();
        assertEquals(0, midCorr.compareTo(lastMonthCorr));
    }
}
