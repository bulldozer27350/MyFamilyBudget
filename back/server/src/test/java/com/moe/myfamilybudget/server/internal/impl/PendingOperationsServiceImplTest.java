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
}
