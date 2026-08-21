package com.moe.myfamilybudget.server.internal.impl;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
        persistenceManager = PersistenceManager.getInstance();
        persistenceManager.resetBudgetData();
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
    }
}
