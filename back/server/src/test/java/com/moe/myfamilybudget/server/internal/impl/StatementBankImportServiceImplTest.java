package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.moe.myfamilybudget.api.model.BankTransactionSplitDto;
import com.moe.myfamilybudget.api.model.SetBankTransactionCategoryRequestDto;
import com.moe.myfamilybudget.api.model.UpdateBankImportLigneRequestDto;
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
        service = new StatementBankImportServiceImpl(persistenceManager, mapper, new ExcelToCsvService());
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
    @DisplayName("updateBankTransactionSplits updates splits for existing transaction")
    void testUpdateBankTransactionSplitsSuccess() {
        // Setup initial transaction
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankTransactionModel tx = new BankImportModel.BankTransactionModel(
                "tx_split_1", "2026-01-15", "LECLERC SUPER", "", new java.math.BigDecimal("-100.00"), "cat_default"
        );
        java.util.List<BankImportModel.BankTransactionModel> txs = new java.util.ArrayList<>(current.transactions());
        txs.add(tx);
        persistenceManager.updateBankImport(new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), txs, current.pendingOperations(), current.matchings()
        ));
        
        BankTransactionSplitDto split1 = new BankTransactionSplitDto();
        split1.setId("s1");
        split1.setCategoryId("cat_food");
        split1.setAmount(new java.math.BigDecimal("-60.00"));
        split1.setLabel("Courses");
        
        BankTransactionSplitDto split2 = new BankTransactionSplitDto();
        split2.setId("s2");
        split2.setCategoryId("cat_clothes");
        split2.setAmount(new java.math.BigDecimal("-40.00"));
        split2.setLabel("Vêtements");

        // Update with 2 splits: -60 and -40
        java.util.List<BankTransactionSplitDto> splitsBody = java.util.List.of(
                split1, split2
        );

        ResponseEntity<Void> response = service.updateBankTransactionSplits("tx_split_1", splitsBody);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel stored = persistenceManager.getBankImport();
        BankImportModel.BankTransactionModel storedTx = stored.transactions().stream()
                .filter(t -> t.id().equals("tx_split_1"))
                .findFirst().orElseThrow();

        assertThat(storedTx.splits()).hasSize(2);
        assertThat(storedTx.splits().get(0).amount()).isEqualByComparingTo("-60.00");
        assertThat(storedTx.splits().get(0).categoryId()).isEqualTo("cat_food");
        assertThat(storedTx.splits().get(1).amount()).isEqualByComparingTo("-40.00");
        assertThat(storedTx.splits().get(1).categoryId()).isEqualTo("cat_clothes");
    }

    @Test
    @DisplayName("updateBankTransactionSplits returns 400 when splits sum does not match transaction amount")
    void testUpdateBankTransactionSplitsInvalidSum() {
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankTransactionModel tx = new BankImportModel.BankTransactionModel(
                "tx_split_2", "2026-01-15", "LECLERC SUPER", "", new java.math.BigDecimal("-100.00"), "cat_default"
        );
        java.util.List<BankImportModel.BankTransactionModel> txs = new java.util.ArrayList<>(current.transactions());
        txs.add(tx);
        persistenceManager.updateBankImport(new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), txs, current.pendingOperations(), current.matchings()
        ));

        BankTransactionSplitDto split1 = new BankTransactionSplitDto();
        split1.setId("s1");
        split1.setCategoryId("cat_food");
        split1.setAmount(new java.math.BigDecimal("-50.00"));
        split1.setLabel("Courses");
        
        java.util.List<BankTransactionSplitDto> splitsBody = java.util.List.of(
                split1
        ); // -50 != -100

        ResponseEntity<Void> response = service.updateBankTransactionSplits("tx_split_2", splitsBody);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("updateBankTransactionSplits returns 404 when transaction is not found")
    void testUpdateBankTransactionSplitsNotFound() {
        ResponseEntity<Void> response = service.updateBankTransactionSplits("unknown_tx", java.util.List.of());
        assertThat(response.getStatusCode().value()).isEqualTo(404);
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

    @Test
    @DisplayName("addBankImportLigne adds category and rule with auto-generated id if not provided")
    void testAddBankImportLigne() {
        // Add Category
        ResponseEntity<Object> catResp = service.addBankImportLigne("categories", Map.of("label", "Alimentation", "kind", "Dépense"));
        assertThat(catResp.getStatusCode().value()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> catBody = (Map<String, Object>) catResp.getBody();
        assertThat(catBody).isNotNull();
        assertThat(catBody.get("id")).isNotNull();
        assertThat(catBody.get("label")).isEqualTo("Alimentation");

        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.categories()).anyMatch(c -> c.label().equals("Alimentation"));

        // Add Rule
        ResponseEntity<Object> ruleResp = service.addBankImportLigne("rules", Map.of("matchText", "AUCHAN", "categoryId", catBody.get("id")));
        assertThat(ruleResp.getStatusCode().value()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> ruleBody = (Map<String, Object>) ruleResp.getBody();
        assertThat(ruleBody).isNotNull();
        assertThat(ruleBody.get("id")).isNotNull();
        assertThat(ruleBody.get("matchText")).isEqualTo("AUCHAN");

        stored = persistenceManager.getBankImport();
        assertThat(stored.rules()).anyMatch(r -> r.matchText().equals("AUCHAN"));

        // Invalid listKey
        ResponseEntity<Object> invalidResp = service.addBankImportLigne("unknown", Map.of());
        assertThat(invalidResp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("updateBankImportLigne updates category and rule fields")
    void testUpdateBankImportLigne() {
        // Add category first
        ResponseEntity<Object> catResp = service.addBankImportLigne("categories", Map.of("label", "Resto", "kind", "Dépense"));
        @SuppressWarnings("unchecked")
        String catId = (String) ((Map<String, Object>) catResp.getBody()).get("id");

        UpdateBankImportLigneRequestDto updateCatDto = new UpdateBankImportLigneRequestDto();
        updateCatDto.setField("label");
        updateCatDto.setValue("Restaurants & Sorties");
        ResponseEntity<Void> updateCatResp = service.updateBankImportLigne("categories", catId, updateCatDto);
        assertThat(updateCatResp.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.categories()).anyMatch(c -> c.id().equals(catId) && c.label().equals("Restaurants & Sorties"));

        // Add rule first
        ResponseEntity<Object> ruleResp = service.addBankImportLigne("rules", Map.of("matchText", "MCDO", "categoryId", catId));
        @SuppressWarnings("unchecked")
        String ruleId = (String) ((Map<String, Object>) ruleResp.getBody()).get("id");

        UpdateBankImportLigneRequestDto updateRuleDto = new UpdateBankImportLigneRequestDto();
        updateRuleDto.setField("matchText");
        updateRuleDto.setValue("MCDONALDS");
        ResponseEntity<Void> updateRuleResp = service.updateBankImportLigne("rules", ruleId, updateRuleDto);
        assertThat(updateRuleResp.getStatusCode().is2xxSuccessful()).isTrue();

        stored = persistenceManager.getBankImport();
        assertThat(stored.rules()).anyMatch(r -> r.id().equals(ruleId) && r.matchText().equals("MCDONALDS"));

        // 404 on unknown id
        ResponseEntity<Void> notFoundResp = service.updateBankImportLigne("categories", "unknown_id", updateCatDto);
        assertThat(notFoundResp.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<Void> notFoundRuleResp = service.updateBankImportLigne("rules", "unknown_id", updateRuleDto);
        assertThat(notFoundRuleResp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("removeBankImportLigne deletes category and rule")
    void testRemoveBankImportLigne() {
        // Add and remove category
        ResponseEntity<Object> catResp = service.addBankImportLigne("categories", Map.of("label", "TempCat"));
        @SuppressWarnings("unchecked")
        String catId = (String) ((Map<String, Object>) catResp.getBody()).get("id");

        ResponseEntity<Void> removeCatResp = service.removeBankImportLigne("categories", catId);
        assertThat(removeCatResp.getStatusCode().value()).isEqualTo(204);

        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.categories()).noneMatch(c -> c.id().equals(catId));

        // Add and remove rule
        ResponseEntity<Object> ruleResp = service.addBankImportLigne("rules", Map.of("matchText", "TEMPRULE"));
        @SuppressWarnings("unchecked")
        String ruleId = (String) ((Map<String, Object>) ruleResp.getBody()).get("id");

        ResponseEntity<Void> removeRuleResp = service.removeBankImportLigne("rules", ruleId);
        assertThat(removeRuleResp.getStatusCode().value()).isEqualTo(204);

        stored = persistenceManager.getBankImport();
        assertThat(stored.rules()).noneMatch(r -> r.id().equals(ruleId));
    }

    @Test
    @DisplayName("setBankImportTransactionCategory updates tx category, creates new rule, and applies rules to other txs")
    void testSetBankImportTransactionCategoryWithNewRule() {
        // Setup initial transactions
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel(
                "tx_cat_1", "2026-01-15", "BOULANGERIE PAUL", "", new java.math.BigDecimal("-12.50"), ""
        );
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel(
                "tx_cat_2", "2026-01-18", "BOULANGERIE PAUL SUD", "", new java.math.BigDecimal("-8.00"), ""
        );
        persistenceManager.updateBankImport(new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(),
                java.util.List.of(tx1, tx2), current.pendingOperations(), current.matchings()
        ));

        SetBankTransactionCategoryRequestDto request = new SetBankTransactionCategoryRequestDto();
        request.setCategoryId("cat_boulangerie");
        request.setRuleKeyword("BOULANGERIE");

        ResponseEntity<Void> response = service.setBankImportTransactionCategory("tx_cat_1", request);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel stored = persistenceManager.getBankImport();
        // Check rule created
        assertThat(stored.rules()).anyMatch(r -> r.matchText().equals("BOULANGERIE") && r.categoryId().equals("cat_boulangerie"));
        // Check tx1 categorized
        assertThat(stored.transactions().stream().filter(t -> t.id().equals("tx_cat_1")).findFirst().get().categoryId()).isEqualTo("cat_boulangerie");
        // Check tx2 auto-categorized by the new rule
        assertThat(stored.transactions().stream().filter(t -> t.id().equals("tx_cat_2")).findFirst().get().categoryId()).isEqualTo("cat_boulangerie");
    }

    @Test
    @DisplayName("setBankImportTransactionCategory updates existing rule if keyword already exists (case-insensitive)")
    void testSetBankImportTransactionCategoryWithExistingRuleUpdate() {
        // Setup initial rule and transaction
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankImportRuleModel rule = new BankImportModel.BankImportRuleModel("r_exist", "AUCHAN", "cat_old");
        BankImportModel.BankTransactionModel tx = new BankImportModel.BankTransactionModel(
                "tx_auchan", "2026-01-15", "AUCHAN DRIVE", "", new java.math.BigDecimal("-50.00"), ""
        );
        persistenceManager.updateBankImport(new BankImportModel(
                current.columnMapping(), current.categories(), java.util.List.of(rule),
                java.util.List.of(tx), current.pendingOperations(), current.matchings()
        ));

        SetBankTransactionCategoryRequestDto request = new SetBankTransactionCategoryRequestDto();
        request.setCategoryId("cat_new");
        request.setRuleKeyword("  auchan  "); // with whitespace and lowercase

        ResponseEntity<Void> response = service.setBankImportTransactionCategory("tx_auchan", request);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel stored = persistenceManager.getBankImport();
        // Check rule updated, not duplicated
        assertThat(stored.rules()).hasSize(1);
        assertThat(stored.rules().get(0).categoryId()).isEqualTo("cat_new");
        assertThat(stored.transactions().get(0).categoryId()).isEqualTo("cat_new");
    }

    @Test
    @DisplayName("recalculateBankImportRules applies all existing rules to uncategorized transactions")
    void testRecalculateBankImportRules() {
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankImportRuleModel rule = new BankImportModel.BankImportRuleModel("r1", "NETFLIX", "cat_streaming");
        BankImportModel.BankTransactionModel txUncat = new BankImportModel.BankTransactionModel(
                "tx_net", "2026-01-10", "NETFLIX COM", "", new java.math.BigDecimal("-15.99"), ""
        );
        BankImportModel.BankTransactionModel txAlreadyCat = new BankImportModel.BankTransactionModel(
                "tx_manual", "2026-01-11", "NETFLIX COM", "", new java.math.BigDecimal("-15.99"), "cat_custom"
        );
        persistenceManager.updateBankImport(new BankImportModel(
                current.columnMapping(), current.categories(), java.util.List.of(rule),
                java.util.List.of(txUncat, txAlreadyCat), current.pendingOperations(), current.matchings()
        ));

        ResponseEntity<Void> response = service.recalculateBankImportRules();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel stored = persistenceManager.getBankImport();
        // txUncat should now have cat_streaming
        assertThat(stored.transactions().stream().filter(t -> t.id().equals("tx_net")).findFirst().get().categoryId()).isEqualTo("cat_streaming");
        // txAlreadyCat should keep cat_custom
        assertThat(stored.transactions().stream().filter(t -> t.id().equals("tx_manual")).findFirst().get().categoryId()).isEqualTo("cat_custom");
    }
}
