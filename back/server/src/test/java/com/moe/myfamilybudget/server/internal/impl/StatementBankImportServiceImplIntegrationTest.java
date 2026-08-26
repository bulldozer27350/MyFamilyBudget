package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.moe.myfamilybudget.server.internal.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@DisplayName("StatementBankImportServiceImpl OpenAPI Integration Test")
class StatementBankImportServiceImplIntegrationTest {

    private StatementBankImportServiceImpl service;
    private PendingOperationsServiceImpl pendingService;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        persistenceManager = new PersistenceManager();
        persistenceManager.init();
        StatementBankImportMapper mapper = new StatementBankImportMapper();
        service = new StatementBankImportServiceImpl(persistenceManager, mapper);
        pendingService = new PendingOperationsServiceImpl(persistenceManager, mapper);
    }

    @Test
    @DisplayName("End-to-end OpenAPI contract flow: mapping update -> CSV import -> pending reconcile")
    void testEndToEndOpenAPIFlow() {
        // 1. Update Mapping
        Map<String, Object> newMapping = Map.of(
                "delimiter", ";",
                "dateFormat", "DD/MM/YYYY",
                "hasHeader", true,
                "dateCol", 0,
                "labelCol", 1,
                "amountCol", 2
        );
        service.updateBankImportMapping(newMapping);

        // 2. Import CSV via MultipartFile
        String csvText = "Date;Libelle;Montant\n10/01/2026;E.LECLERC DRIVE;-65.20";
        MockMultipartFile file = new MockMultipartFile("file", "import.csv", "text/csv", csvText.getBytes());

        ResponseEntity<Void> importResp = service.importBankCSV(file);
        assertThat(importResp.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel afterImport = persistenceManager.getBankImport();
        assertThat(afterImport.transactions()).hasSize(1);
        assertThat(afterImport.transactions().get(0).label()).isEqualTo("E.LECLERC DRIVE");

        // 3. Reconcile Pending Operations
        ResponseEntity<Void> reconcileResp = pendingService.reconcilePendingOperations(Map.of());
        assertThat(reconcileResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 4. Verify GET /bank-import response
        ResponseEntity<Object> getResp = service.getBankImport();
        assertThat(getResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 5. Test Pending CB import with manual duplicate candidate & merge
        // Pre-create manual pending operation
        BankImportModel.PendingOperationModel manualOp = new BankImportModel.PendingOperationModel(
                "man_cb_1", "2026-01-15", "2026-01-15", "cb", "", "Plein essence",
                new java.math.BigDecimal("-60.00"), "cat_carburant", "pending", null, null, ""
        );
        persistenceManager.updateBankImport(new BankImportModel(
                afterImport.columnMapping(), afterImport.categories(), afterImport.rules(),
                afterImport.transactions(), java.util.List.of(manualOp), afterImport.matchings()
        ));

        // Import CB row matching within +-1 day and +-10 EUR
        Map<String, Object> importReq = Map.of(
                "rawRows", java.util.List.of(
                        java.util.List.of("16/01/2026", "TOTAL RELAIS PARIS", "-64.50")
                ),
                "colRoles", java.util.List.of("date", "label", "amount"),
                "config", Map.of("dateFormat", "DD/MM/YYYY", "usePurchaseDate", false)
        );

        ResponseEntity<Object> importCbResp = pendingService.importPendingCB(importReq);
        assertThat(importCbResp.getStatusCode().is2xxSuccessful()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> summaryMap = (Map<String, Object>) importCbResp.getBody();
        assertThat(summaryMap).isNotNull();
        assertThat(summaryMap.get("imported")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> candidates = (java.util.List<Map<String, Object>>) summaryMap.get("duplicateCandidates");
        assertThat(candidates).hasSize(1);

        // Execute merge
        @SuppressWarnings("unchecked")
        Map<String, Object> incomingOpMap = (Map<String, Object>) candidates.get(0).get("incomingOp");
        pendingService.mergePendingOperation(Map.of(
                "manualOpId", "man_cb_1",
                "bankOp", incomingOpMap
        ));

        BankImportModel afterMerge = persistenceManager.getBankImport();
        assertThat(afterMerge.pendingOperations()).hasSize(1);
        BankImportModel.PendingOperationModel merged = afterMerge.pendingOperations().get(0);
        assertThat(merged.id()).isEqualTo("man_cb_1");
        assertThat(merged.categoryId()).isEqualTo("cat_carburant"); // Conserved
        assertThat(merged.label()).isEqualTo("TOTAL RELAIS PARIS"); // Bank value
        assertThat(merged.status()).isEqualTo("pending");
        assertThat(merged.linkedTxId()).isNull();
        assertThat(merged.clearedDate()).isNull();
    }
}
