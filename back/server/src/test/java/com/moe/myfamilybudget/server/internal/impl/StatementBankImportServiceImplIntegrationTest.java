package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.server.internal.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@DisplayName("StatementBankImportServiceImpl Full Integration Test")
class StatementBankImportServiceImplIntegrationTest {

    private StatementBankImportServiceImpl service;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        persistenceManager = PersistenceManager.getInstance();
        persistenceManager.resetBudgetData();
        service = new StatementBankImportServiceImpl(persistenceManager, new StatementBankImportMapper());
    }

    @Test
    @DisplayName("Complete end-to-end workflow: Rule creation -> CSV Import -> Auto Categorization -> Auto Reconcile")
    void testEndToEndImportWorkflow() {
        // 1. Create a category and rule
        ResponseEntity<Map<String, Object>> catResp = service.addCategory(Map.of("label", "Courses", "kind", "Dépense"));
        String categoryId = (String) catResp.getBody().get("id");

        service.addRule(Map.of("matchText", "LECLERC", "categoryId", categoryId));

        // 2. Import CSV with bank transactions
        String csvText = "Date;Libelle;Montant\n10/01/2026;E.LECLERC DRIVE;-65.20\n12/01/2026;PRELEVEMENT ELEC;-80.00";
        Map<String, Object> csvPayload = Map.of(
                "csvText", csvText,
                "colRoles", List.of("date", "label", "amount"),
                "mapping", Map.of("delimiter", ";", "dateFormat", "DD/MM/YYYY", "hasHeader", true)
        );

        ResponseEntity<Map<String, Object>> importResp = service.importBankCSV(csvPayload);
        assertThat(importResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> importSummary = importResp.getBody();
        assertThat((Integer) importSummary.get("imported")).isEqualTo(2);
        assertThat((Integer) importSummary.get("autoCategorized")).isEqualTo(1); // LECLERC categorized by rule

        // 3. Pre-seed a pending operation for matching
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.PendingOperationModel> ops = List.of(
                new BankImportModel.PendingOperationModel("op100", "2026-01-10", "2026-01-10", "cb", "", "E.LECLERC DRIVE", new BigDecimal("-65.20"), "", "pending", null, null, "")
        );
        persistenceManager.updateBankImport(new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), current.transactions(), ops, current.matchings()
        ));

        // 4. Trigger auto-reconciliation
        ResponseEntity<Map<String, Object>> reconcileResp = service.autoMatchPendingOperations();
        assertThat(reconcileResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> reconcileBody = reconcileResp.getBody();
        assertThat((Integer) reconcileBody.get("matchCount")).isEqualTo(1);

        BankImportModel finalState = persistenceManager.getBankImport();
        assertThat(finalState.pendingOperations().get(0).status()).isEqualTo("cleared");
        assertThat(finalState.pendingOperations().get(0).linkedTxId()).isNotNull();
    }
}
