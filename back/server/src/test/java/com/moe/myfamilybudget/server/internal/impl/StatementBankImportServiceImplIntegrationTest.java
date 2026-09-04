package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.moe.myfamilybudget.api.model.ImportBankTransactionsRequestDto;
import com.moe.myfamilybudget.api.model.ReconcilePendingOperations200Response;
import com.moe.myfamilybudget.api.model.SetBankTransactionCategoryRequestDto;
import com.moe.myfamilybudget.api.model.UpdateBankImportLigneRequestDto;
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
        service = new StatementBankImportServiceImpl(persistenceManager, mapper, new ExcelToCsvService());
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
        ResponseEntity<ReconcilePendingOperations200Response> reconcileResp = pendingService.reconcilePendingOperations(Map.of());
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

    @Test
    @DisplayName("End-to-end CRUD for categories and rules: Add -> Update -> Delete")
    void testCategoriesAndRulesCrudFlow() {
        // 1. Add Category
        ResponseEntity<Object> addCatResp = service.addBankImportLigne("categories", Map.of(
                "label", "Loisirs & Vacances",
                "kind", "Dépense",
                "compressible", "Oui"
        ));
        assertThat(addCatResp.getStatusCode().value()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> catMap = (Map<String, Object>) addCatResp.getBody();
        String catId = (String) catMap.get("id");
        assertThat(catId).isNotBlank();

        // 2. Add Rule referencing category
        ResponseEntity<Object> addRuleResp = service.addBankImportLigne("rules", Map.of(
                "matchText", "AIRBNB",
                "categoryId", catId
        ));
        assertThat(addRuleResp.getStatusCode().value()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> ruleMap = (Map<String, Object>) addRuleResp.getBody();
        String ruleId = (String) ruleMap.get("id");
        assertThat(ruleId).isNotBlank();

        // 3. Update Category label
        UpdateBankImportLigneRequestDto updateCat = new UpdateBankImportLigneRequestDto();
        updateCat.setField("label");
        updateCat.setValue("Vacances & Voyages");
        ResponseEntity<Void> updateCatResp = service.updateBankImportLigne("categories", catId, updateCat);
        assertThat(updateCatResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 4. Update Rule matchText
        UpdateBankImportLigneRequestDto updateRule = new UpdateBankImportLigneRequestDto();
        updateRule.setField("matchText");
        updateRule.setValue("AIRBNB RESERVATION");
        ResponseEntity<Void> updateRuleResp = service.updateBankImportLigne("rules", ruleId, updateRule);
        assertThat(updateRuleResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 5. Verify in getBankImport()
        ResponseEntity<Object> getResp = service.getBankImport();
        @SuppressWarnings("unchecked")
        Map<String, Object> getMap = (Map<String, Object>) getResp.getBody();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> categories = (java.util.List<Map<String, Object>>) getMap.get("categories");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> rules = (java.util.List<Map<String, Object>>) getMap.get("rules");

        assertThat(categories).anyMatch(c -> c.get("id").equals(catId) && c.get("label").equals("Vacances & Voyages"));
        assertThat(rules).anyMatch(r -> r.get("id").equals(ruleId) && r.get("matchText").equals("AIRBNB RESERVATION"));

        // 6. Delete Rule & Category
        assertThat(service.removeBankImportLigne("rules", ruleId).getStatusCode().value()).isEqualTo(204);
        assertThat(service.removeBankImportLigne("categories", catId).getStatusCode().value()).isEqualTo(204);

        BankImportModel afterDelete = persistenceManager.getBankImport();
        assertThat(afterDelete.categories()).noneMatch(c -> c.id().equals(catId));
        assertThat(afterDelete.rules()).noneMatch(r -> r.id().equals(ruleId));
    }

    @Test
    @DisplayName("End-to-end flow: Manual categorization with rule creation -> CSV import -> Recalculate rules")
    void testManualCategorizationAndRuleRecalculateFlow() {
        // 1. Initial transaction without category
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankTransactionModel tx1 = new BankImportModel.BankTransactionModel(
                "tx_spotify_1", "2026-01-05", "SPOTIFY AB STOCKHOLM", "cb", new java.math.BigDecimal("-10.99"), ""
        );
        BankImportModel.BankTransactionModel tx2 = new BankImportModel.BankTransactionModel(
                "tx_spotify_2", "2026-01-15", "SPOTIFY AB STOCKHOLM", "cb", new java.math.BigDecimal("-10.99"), ""
        );
        persistenceManager.updateBankImport(new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(),
                java.util.List.of(tx1, tx2), current.pendingOperations(), current.matchings()
        ));

        // 2. Set category for tx1 and create rule "SPOTIFY"
        SetBankTransactionCategoryRequestDto setCatDto = new SetBankTransactionCategoryRequestDto();
        setCatDto.setCategoryId("cat_musique");
        setCatDto.setRuleKeyword("SPOTIFY");

        ResponseEntity<Void> setCatResp = service.setBankImportTransactionCategory("tx_spotify_1", setCatDto);
        assertThat(setCatResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 3. Verify tx1 and tx2 are both categorized as cat_musique
        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.rules()).anyMatch(r -> r.matchText().equals("SPOTIFY") && r.categoryId().equals("cat_musique"));
        assertThat(stored.transactions().stream().filter(t -> t.id().equals("tx_spotify_1")).findFirst().get().categoryId()).isEqualTo("cat_musique");
        assertThat(stored.transactions().stream().filter(t -> t.id().equals("tx_spotify_2")).findFirst().get().categoryId()).isEqualTo("cat_musique");

        // 4. Add new uncategorized transaction manually and trigger recalculate
        BankImportModel.BankTransactionModel tx3 = new BankImportModel.BankTransactionModel(
                "tx_spotify_3", "2026-02-05", "SPOTIFY SUBSCRIPTION", "cb", new java.math.BigDecimal("-10.99"), ""
        );
        java.util.List<BankImportModel.BankTransactionModel> allTxs = new java.util.ArrayList<>(stored.transactions());
        allTxs.add(tx3);
        persistenceManager.updateBankImport(new BankImportModel(
                stored.columnMapping(), stored.categories(), stored.rules(),
                allTxs, stored.pendingOperations(), stored.matchings()
        ));

        ResponseEntity<Void> recalcResp = service.recalculateBankImportRules();
        assertThat(recalcResp.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel afterRecalc = persistenceManager.getBankImport();
        assertThat(afterRecalc.transactions().stream().filter(t -> t.id().equals("tx_spotify_3")).findFirst().get().categoryId()).isEqualTo("cat_musique");
    }

    @Test
    @DisplayName("End-to-end flow: Import parsed transactions with custom column mapping & deduplication check")
    void testImportBankTransactionsFlow() {
        // Pre-configure a rule
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankImportRuleModel rule = new BankImportModel.BankImportRuleModel("r_sncf", "SNCF", "cat_transport");
        persistenceManager.updateBankImport(new BankImportModel(
                current.columnMapping(), current.categories(), java.util.List.of(rule),
                java.util.List.of(), current.pendingOperations(), current.matchings()
        ));

        // First import batch
        ImportBankTransactionsRequestDto req1 = new ImportBankTransactionsRequestDto();
        req1.setColRoles(java.util.List.of("date", "label", "amount"));
        req1.setRawRows(java.util.List.of(
                java.util.List.of("01/02/2026", "BILLET SNCF PARIS", "-45.00"),
                java.util.List.of("02/02/2026", "RESTAURANT LE BISTROT", "-32.50")
        ));
        req1.setMapping(Map.of("dateFormat", "DD/MM/YYYY", "delimiter", ";"));

        ResponseEntity<Object> resp1 = service.importBankTransactions(req1);
        assertThat(resp1.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary1 = (Map<String, Object>) resp1.getBody();
        assertThat(summary1.get("imported")).isEqualTo(2);
        assertThat(summary1.get("duplicates")).isEqualTo(0);
        assertThat(summary1.get("autoCategorized")).isEqualTo(1);

        // Second import with a duplicate
        ImportBankTransactionsRequestDto req2 = new ImportBankTransactionsRequestDto();
        req2.setColRoles(java.util.List.of("date", "label", "amount"));
        req2.setRawRows(java.util.List.of(
                java.util.List.of("01/02/2026", "BILLET SNCF PARIS", "-45.00"), // duplicate
                java.util.List.of("05/02/2026", "PHARMACIE DE LA GARE", "-15.00") // new
        ));
        req2.setMapping(Map.of("dateFormat", "DD/MM/YYYY", "delimiter", ";"));

        ResponseEntity<Object> resp2 = service.importBankTransactions(req2);
        assertThat(resp2.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary2 = (Map<String, Object>) resp2.getBody();
        assertThat(summary2.get("imported")).isEqualTo(1);
        assertThat(summary2.get("duplicates")).isEqualTo(1);

        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.transactions()).hasSize(3);
    }

    @Test
    @DisplayName("End-to-end flow: import detects a duplicate, then forceImportBankTransaction adds it anyway")
    void testForceImportDuplicateAfterImportFlow() {
        // First import
        ImportBankTransactionsRequestDto req1 = new ImportBankTransactionsRequestDto();
        req1.setColRoles(java.util.List.of("date", "label", "amount"));
        req1.setRawRows(java.util.List.of(
                java.util.List.of("10/03/2026", "ABONNEMENT SALLE DE SPORT", "-29.90")
        ));
        req1.setMapping(Map.of("dateFormat", "DD/MM/YYYY", "delimiter", ";"));
        service.importBankTransactions(req1);

        // Second import: same line is flagged as duplicate
        ImportBankTransactionsRequestDto req2 = new ImportBankTransactionsRequestDto();
        req2.setColRoles(java.util.List.of("date", "label", "amount"));
        req2.setRawRows(java.util.List.of(
                java.util.List.of("10/03/2026", "ABONNEMENT SALLE DE SPORT", "-29.90")
        ));
        req2.setMapping(Map.of("dateFormat", "DD/MM/YYYY", "delimiter", ";"));
        ResponseEntity<Object> resp2 = service.importBankTransactions(req2);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary2 = (Map<String, Object>) resp2.getBody();
        assertThat(summary2.get("duplicates")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> ignored = (java.util.List<Map<String, Object>>) summary2.get("ignoredDuplicates");
        assertThat(ignored).hasSize(1);
        Map<String, Object> duplicateTx = ignored.get(0);

        // User explicitly forces the import of the flagged duplicate
        ResponseEntity<Object> forceResp = service.forceImportBankTransaction(duplicateTx);
        assertThat(forceResp.getStatusCode().is2xxSuccessful()).isTrue();

        BankImportModel stored = persistenceManager.getBankImport();
        assertThat(stored.transactions()).hasSize(2);
        assertThat(stored.transactions().stream().filter(t -> t.label().equals("ABONNEMENT SALLE DE SPORT")).count()).isEqualTo(2);
    }
}
