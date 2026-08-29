package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.server.internal.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@DisplayName("PendingOperationsServiceImpl OpenAPI Controller Unit Tests")
class PendingOperationsServiceImplTest {

    private PendingOperationsServiceImpl service;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        persistenceManager = new PersistenceManager();
        persistenceManager.init();
        service = new PendingOperationsServiceImpl(persistenceManager, new StatementBankImportMapper());
    }

    @Test
    @DisplayName("getPendingOperations returns 200 OK with complete pending operations payload map")
    void testGetPendingOperations() {
        ResponseEntity<Object> response = service.getPendingOperations();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKeys(
                "pendingOperations", "transactions", "categories", "rules",
                "charges", "incomes", "oneoff", "settings"
        );
    }

    @Test
    @DisplayName("importPendingCB imports raw rows and updates persistence")
    void testImportPendingCB() {
        Map<String, Object> request = Map.of(
                "rawRows", List.of(
                        List.of("15/01/2026", "CB RESTAURANT LE BISTROT", "-42.50")
                ),
                "colRoles", List.of("date", "label", "amount"),
                "config", Map.of("dateFormat", "DD/MM/YYYY", "usePurchaseDate", false)
        );

        ResponseEntity<Object> response = service.importPendingCB(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) response.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.get("imported")).isEqualTo(1);

        BankImportModel updated = persistenceManager.getBankImport();
        assertThat(updated.pendingOperations()).hasSize(1);
        assertThat(updated.pendingOperations().get(0).label()).isEqualTo("CB RESTAURANT LE BISTROT");
    }

    @Test
    @DisplayName("forceImportPendingOperation saves and modifies pending operation with notes, category, and splits")
    void testForceImportAndModifyPendingOperationWithSplits() {
        // 1. Create initial pending operation
        Map<String, Object> newOp = Map.of(
                "id", "op_test_1",
                "date", "2026-06-15",
                "expectedDate", "2026-06-20",
                "type", "cheque",
                "refNumber", "CHQ-123456",
                "label", "Cheque Plomberie",
                "amount", -150.0,
                "categoryId", "cat_logement",
                "notes", "Reparation fuite d'eau",
                "splits", List.of(
                        Map.of("id", "sp_1", "categoryId", "cat_logement", "amount", -100.0, "label", "Main d'oeuvre"),
                        Map.of("id", "sp_2", "categoryId", "cat_divers", "amount", -50.0, "label", "Fournitures")
                )
        );

        ResponseEntity<Void> forceResponse = service.forceImportPendingOperation(newOp);
        assertThat(forceResponse.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel current = persistenceManager.getBankImport();
        assertThat(current.pendingOperations()).hasSize(1);
        BankImportModel.PendingOperationModel saved = current.pendingOperations().get(0);
        assertThat(saved.id()).isEqualTo("op_test_1");
        assertThat(saved.categoryId()).isEqualTo("cat_logement");
        assertThat(saved.notes()).isEqualTo("Reparation fuite d'eau");
        assertThat(saved.splits()).hasSize(2);
        assertThat(saved.splits().get(0).label()).isEqualTo("Main d'oeuvre");
        assertThat(saved.splits().get(1).categoryId()).isEqualTo("cat_divers");

        // 2. Modify existing operation (change category, notes, and splits)
        Map<String, Object> modifiedOp = Map.of(
                "id", "op_test_1",
                "date", "2026-06-18",
                "expectedDate", "2026-06-25",
                "type", "cheque",
                "refNumber", "CHQ-123456-BIS",
                "label", "Cheque Plomberie & Chauffage",
                "amount", -180.0,
                "categoryId", "cat_bricolage",
                "notes", "Note mise à jour avec nouvelle ventilation",
                "splits", List.of(
                        Map.of("id", "sp_1_mod", "categoryId", "cat_bricolage", "amount", -120.0, "label", "Tubulure"),
                        Map.of("id", "sp_2_mod", "categoryId", "cat_energie", "amount", -60.0, "label", "Radiateur")
                )
        );

        ResponseEntity<Void> updateResponse = service.forceImportPendingOperation(modifiedOp);
        assertThat(updateResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // 3. Verify in persistence
        BankImportModel afterUpdate = persistenceManager.getBankImport();
        assertThat(afterUpdate.pendingOperations()).hasSize(1);
        BankImportModel.PendingOperationModel updatedOp = afterUpdate.pendingOperations().get(0);
        assertThat(updatedOp.id()).isEqualTo("op_test_1");
        assertThat(updatedOp.label()).isEqualTo("Cheque Plomberie & Chauffage");
        assertThat(updatedOp.amount()).isEqualByComparingTo("-180.0");
        assertThat(updatedOp.categoryId()).isEqualTo("cat_bricolage");
        assertThat(updatedOp.notes()).isEqualTo("Note mise à jour avec nouvelle ventilation");
        assertThat(updatedOp.refNumber()).isEqualTo("CHQ-123456-BIS");
        assertThat(updatedOp.splits()).hasSize(2);
        assertThat(updatedOp.splits().get(0).label()).isEqualTo("Tubulure");
        assertThat(updatedOp.splits().get(1).categoryId()).isEqualTo("cat_energie");

        // 4. Verify in getPendingOperations API response
        ResponseEntity<Object> getResp = service.getPendingOperations();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) getResp.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> opsList = (List<Map<String, Object>>) body.get("pendingOperations");
        assertThat(opsList).hasSize(1);
        Map<String, Object> opMap = opsList.get(0);
        assertThat(opMap.get("categoryId")).isEqualTo("cat_bricolage");
        assertThat(opMap.get("notes")).isEqualTo("Note mise à jour avec nouvelle ventilation");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> splitsList = (List<Map<String, Object>>) opMap.get("splits");
        assertThat(splitsList).hasSize(2);
        assertThat(splitsList.get(0).get("label")).isEqualTo("Tubulure");
    }
}
