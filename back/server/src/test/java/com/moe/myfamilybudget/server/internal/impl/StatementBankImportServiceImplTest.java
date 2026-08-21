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

@DisplayName("StatementBankImportServiceImpl OpenAPI Unit Tests")
class StatementBankImportServiceImplTest {

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
    @DisplayName("getBankImport returns bank import state")
    void testGetBankImport() {
        ResponseEntity<Object> response = service.getBankImport();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKeys("columnMapping", "categories", "rules", "transactions", "pendingOperations");
    }

    @Test
    @DisplayName("updateBankImportMapping persists new mapping configuration")
    void testUpdateBankImportMapping() {
        Map<String, Object> newMapping = Map.of(
                "delimiter", ";",
                "dateFormat", "YYYY-MM-DD",
                "hasHeader", true,
                "dateCol", 0,
                "labelCol", 1,
                "amountCol", 2
        );

        ResponseEntity<Void> response = service.updateBankImportMapping(newMapping);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.columnMapping().dateFormat()).isEqualTo("YYYY-MM-DD");
        assertThat(stored.columnMapping().dateCol()).isEqualTo(0);
        assertThat(stored.columnMapping().labelCol()).isEqualTo(1);
        assertThat(stored.columnMapping().amountCol()).isEqualTo(2);
    }

    @Test
    @DisplayName("importBankCSV imports transactions from CSV multipart file")
    void testImportBankCSV() {
        // First, set up the column mapping
        Map<String, Object> mapping = Map.of(
                "delimiter", ";",
                "dateFormat", "DD/MM/YYYY",
                "hasHeader", true,
                "dateCol", 0,
                "labelCol", 1,
                "amountCol", 2
        );
        service.updateBankImportMapping(mapping);

        String csvContent = "Date;Libelle;Montant\n15/01/2026;Achat Carrefour;-45.50\n";
        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", csvContent.getBytes());

        ResponseEntity<Void> response = service.importBankCSV(file);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.transactions()).hasSize(1);
        assertThat(stored.transactions().get(0).label()).isEqualTo("Achat Carrefour");
    }

    @Test
    @DisplayName("getPendingOperations, reconcilePendingOperations and ignorePendingOperation conform to OpenAPI contract")
    void testPendingOperationsAPI() {
        ResponseEntity<Object> getResp = pendingService.getPendingOperations();
        assertThat(getResp.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Void> reconcileResp = pendingService.reconcilePendingOperations(Map.of());
        assertThat(reconcileResp.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Void> ignoreResp = pendingService.ignorePendingOperation(Map.of("id", "op123"));
        assertThat(ignoreResp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
