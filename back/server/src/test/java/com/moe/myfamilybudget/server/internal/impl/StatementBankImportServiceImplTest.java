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

@DisplayName("StatementBankImportServiceImpl Service Unit Tests")
class StatementBankImportServiceImplTest {

    private StatementBankImportServiceImpl service;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        persistenceManager = PersistenceManager.getInstance();
        persistenceManager.resetBudgetData();
        service = new StatementBankImportServiceImpl(persistenceManager, new StatementBankImportMapper());
    }

    @Test
    @DisplayName("getBankImport returns initial bank import state")
    void testGetBankImport() {
        ResponseEntity<Map<String, Object>> response = service.getBankImport();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
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
    @DisplayName("addCategory and removeCategory updates stored categories")
    void testCategoryCRUD() {
        Map<String, Object> catDto = Map.of("label", "Alimentation", "kind", "Dépense", "compressible", "Non");
        ResponseEntity<Map<String, Object>> addResp = service.addCategory(catDto);

        assertThat(addResp.getStatusCode().is2xxSuccessful()).isTrue();
        String catId = (String) addResp.getBody().get("id");
        assertThat(catId).isNotNull();

        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.categories()).hasSize(1);
        assertThat(stored.categories().get(0).label()).isEqualTo("Alimentation");

        ResponseEntity<Void> removeResp = service.removeCategory(catId);
        assertThat(removeResp.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(persistenceManager.getBankImport().categories()).isEmpty();
    }

    @Test
    @DisplayName("addRule and recalculateBankImportRules applies rules to stored transactions")
    void testRuleCRUDAndRecalculate() {
        // Pre-seed a transaction
        BankImportModel base = persistenceManager.getBankImport();
        BankImportModel seeded = new BankImportModel(
                base.columnMapping(),
                base.categories(),
                base.rules(),
                List.of(new BankImportModel.BankTransactionModel("tx1", "2026-01-10", "CARREFOUR HYPER", "", new BigDecimal("-40.00"), "")),
                base.pendingOperations(),
                base.matchings()
        );
        persistenceManager.updateBankImport(seeded);

        // Add rule
        Map<String, Object> ruleDto = Map.of("matchText", "CARREFOUR", "categoryId", "cat_supermarche");
        service.addRule(ruleDto);

        // Recalculate
        ResponseEntity<Void> recalcResp = service.recalculateBankImportRules();
        assertThat(recalcResp.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel updated = persistenceManager.getBankImport();
        assertThat(updated.transactions().get(0).categoryId()).isEqualTo("cat_supermarche");
    }
}
